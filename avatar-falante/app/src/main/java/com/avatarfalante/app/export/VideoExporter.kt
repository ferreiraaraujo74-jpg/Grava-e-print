package com.avatarfalante.app.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.net.Uri
import android.opengl.GLES20
import android.opengl.GLUtils
import android.os.Environment
import android.provider.MediaStore
import com.avatarfalante.app.anim.AvatarRenderer
import com.avatarfalante.app.anim.Expression
import com.avatarfalante.app.anim.Timeline
import com.avatarfalante.app.audio.PcmAudio
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Gera o MP4 (H.264 + AAC) quadro a quadro, mais rápido que o tempo real:
 * Canvas → Bitmap → textura OpenGL → Surface do encoder (com timestamp exato).
 */
object VideoExporter {
    const val FPS = 30
    private val RELATIVE = "${Environment.DIRECTORY_MOVIES}/AvatarFalante"

    /** Tamanho do vídeo: até 720 px de largura (ou 1280 de altura), múltiplo de 16. */
    fun sizeFor(aspect: Float): Pair<Int, Int> {
        var w = 720f; var h = w / aspect
        if (h > 1280f) { h = 1280f; w = h * aspect }
        fun a16(v: Float) = max(16, (v / 16f).roundToInt() * 16)
        return a16(w) to a16(h)
    }

    fun export(
        ctx: Context,
        renderer: AvatarRenderer,
        timeline: Timeline,
        expression: Expression,
        audio: PcmAudio,
        onProgress: (Float) -> Unit,
        isCancelled: () -> Boolean,
    ): Uri? {
        val (w, h) = sizeFor(renderer.aspect)
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "avatar_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, RELATIVE)
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val resolver = ctx.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values) ?: return null
        var ok = false
        try {
            resolver.openFileDescriptor(uri, "rw")!!.use { pfd ->
                val muxer = SafeMuxer(pfd.fileDescriptor, 2)
                try {
                    encodeAudio(audio, muxer)
                    ok = encodeVideo(w, h, renderer, timeline, expression, audio.durationSec + 0.5f, muxer, onProgress, isCancelled)
                } finally {
                    muxer.stop()
                    if (muxer.samplesWritten == 0L) ok = false
                }
            }
        } catch (t: Throwable) {
            ok = false
            throw t
        } finally {
            if (ok) resolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
            else runCatching { resolver.delete(uri, null, null) }
        }
        return if (ok) uri else null
    }

    private fun encodeAudio(audio: PcmAudio, muxer: SafeMuxer) {
        val ch = audio.channels.coerceIn(1, 2)
        val pcm = if (audio.channels > 2) audio.mono() else audio.samples
        val fmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, audio.sampleRate, ch).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        codec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        val info = MediaCodec.BufferInfo()
        var track = -1
        var pos = 0 // em amostras (shorts)
        var inputDone = false
        try {
            while (true) {
                if (!inputDone) {
                    val i = codec.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        val buf = codec.getInputBuffer(i)!!
                        buf.clear()
                        val n = min(buf.remaining() / 2, pcm.size - pos) / ch * ch
                        val ptsUs = pos.toLong() / ch * 1_000_000L / audio.sampleRate
                        if (n <= 0) {
                            codec.queueInputBuffer(i, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true
                        } else {
                            buf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(pcm, pos, n)
                            codec.queueInputBuffer(i, 0, n * 2, ptsUs, 0)
                            pos += n
                        }
                    }
                }
                val o = codec.dequeueOutputBuffer(info, 10_000)
                if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) track = muxer.addTrack(codec.outputFormat)
                else if (o >= 0) {
                    val b = codec.getOutputBuffer(o)
                    if (b != null && track >= 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) muxer.write(track, b, info)
                    codec.releaseOutputBuffer(o, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
        } finally {
            runCatching { codec.stop() }; runCatching { codec.release() }
        }
    }

    private fun encodeVideo(
        w: Int, h: Int, renderer: AvatarRenderer, timeline: Timeline, expression: Expression, durationSec: Float,
        muxer: SafeMuxer, onProgress: (Float) -> Unit, isCancelled: () -> Boolean,
    ): Boolean {
        val fmt = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, (w * h * FPS * 0.15).toInt().coerceIn(2_000_000, 8_000_000))
            setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val surface = codec.createInputSurface()
        codec.start()
        val egl = EglCore()
        val eglSurface = egl.createWindowSurface(surface)
        egl.makeCurrent(eglSurface)
        val gl = QuadRenderer()
        val frame = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(frame)
        val info = MediaCodec.BufferInfo()
        var track = -1
        val total = max(1, (durationSec * FPS).roundToInt())

        fun drain(endOfStream: Boolean) {
            if (endOfStream) codec.signalEndOfInputStream()
            val deadline = System.currentTimeMillis() + if (endOfStream) 3_000 else 0
            while (true) {
                val o = codec.dequeueOutputBuffer(info, if (endOfStream) 10_000 else 0)
                when {
                    o == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!endOfStream || System.currentTimeMillis() > deadline) return
                    o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> track = muxer.addTrack(codec.outputFormat)
                    o >= 0 -> {
                        val b = codec.getOutputBuffer(o)
                        if (b != null && track >= 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) muxer.write(track, b, info)
                        codec.releaseOutputBuffer(o, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                    }
                }
            }
        }

        try {
            for (i in 0 until total) {
                if (isCancelled()) return false
                val t = i.toFloat() / FPS
                renderer.draw(canvas, w, h, timeline.state(t, expression))
                gl.draw(frame, w, h)
                egl.setPresentationTime(eglSurface, i * 1_000_000_000L / FPS)
                egl.swapBuffers(eglSurface)
                drain(false)
                if (i % 5 == 0) onProgress(i.toFloat() / total)
            }
            drain(true)
            onProgress(1f)
            return true
        } finally {
            frame.recycle()
            gl.release()
            egl.releaseSurface(eglSurface)
            egl.release()
            runCatching { codec.stop() }; runCatching { codec.release() }
            surface.release()
        }
    }

    /** Desenha um Bitmap na tela inteira (textura 2D). */
    private class QuadRenderer {
        private val program: Int
        private val tex: Int
        private val aPos: Int
        private val aTex: Int
        private var uploaded = false
        private val quad: FloatBuffer = ByteBuffer.allocateDirect(16 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            // x, y, s, t (t=0 no topo do bitmap)
            put(floatArrayOf(-1f, -1f, 0f, 1f, 1f, -1f, 1f, 1f, -1f, 1f, 0f, 0f, 1f, 1f, 1f, 0f)); position(0)
        }

        init {
            program = link(
                "attribute vec2 aPos; attribute vec2 aTex; varying vec2 vTex; void main(){ gl_Position = vec4(aPos,0.0,1.0); vTex = aTex; }",
                "precision mediump float; varying vec2 vTex; uniform sampler2D sTex; void main(){ gl_FragColor = vec4(texture2D(sTex, vTex).rgb, 1.0); }",
            )
            aPos = GLES20.glGetAttribLocation(program, "aPos")
            aTex = GLES20.glGetAttribLocation(program, "aTex")
            val t = IntArray(1); GLES20.glGenTextures(1, t, 0); tex = t[0]
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        }

        fun draw(bmp: Bitmap, w: Int, h: Int) {
            GLES20.glViewport(0, 0, w, h)
            GLES20.glUseProgram(program)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
            if (!uploaded) { GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0); uploaded = true }
            else GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, bmp)
            quad.position(0)
            GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 16, quad)
            GLES20.glEnableVertexAttribArray(aPos)
            quad.position(2)
            GLES20.glVertexAttribPointer(aTex, 2, GLES20.GL_FLOAT, false, 16, quad)
            GLES20.glEnableVertexAttribArray(aTex)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        }

        fun release() {
            GLES20.glDeleteTextures(1, intArrayOf(tex), 0)
            GLES20.glDeleteProgram(program)
        }

        private fun link(vs: String, fs: String): Int {
            fun sh(type: Int, src: String) = GLES20.glCreateShader(type).also {
                GLES20.glShaderSource(it, src); GLES20.glCompileShader(it)
                val ok = IntArray(1); GLES20.glGetShaderiv(it, GLES20.GL_COMPILE_STATUS, ok, 0)
                check(ok[0] != 0) { "shader: " + GLES20.glGetShaderInfoLog(it) }
            }
            val p = GLES20.glCreateProgram()
            GLES20.glAttachShader(p, sh(GLES20.GL_VERTEX_SHADER, vs))
            GLES20.glAttachShader(p, sh(GLES20.GL_FRAGMENT_SHADER, fs))
            GLES20.glLinkProgram(p)
            return p
        }
    }
}
