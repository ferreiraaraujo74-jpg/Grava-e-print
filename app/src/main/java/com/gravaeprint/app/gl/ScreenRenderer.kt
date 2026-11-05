package com.gravaeprint.app.gl

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import com.gravaeprint.app.service.RecordingClock
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Renderizador OpenGL ES que fica entre o VirtualDisplay e o encoder:
 *
 *   VirtualDisplay -> Surface(SurfaceTexture/OES) -> [GL] -> Surface de entrada do MediaCodec
 *
 * Como todos os frames passam por aqui, um print é só desenhar o frame atual num FBO
 * e ler com glReadPixels — no mesmo contexto, sem um segundo VirtualDisplay e sem
 * interromper o vídeo.
 */
class ScreenRenderer(
    private val srcWidth: Int,
    private val srcHeight: Int,
    private val encWidth: Int,
    private val encHeight: Int,
    private val encoderSurface: Surface,
    private val clock: RecordingClock,
    fps: Int,
) {
    private val thread = HandlerThread("GravaEPrint-GL").apply { start() }
    private val handler = Handler(thread.looper)

    private lateinit var egl: EglCore
    private lateinit var encEglSurface: EGLSurface
    private lateinit var surfaceTexture: SurfaceTexture
    private lateinit var inputSurface: Surface

    private var program = 0
    private var oesTex = 0
    private var aPos = 0
    private var aTex = 0
    private var uTexM = 0
    private var uFlip = 0
    private val texMatrix = FloatArray(16)

    private var fbo = 0
    private var fboTex = 0

    private var hasFrame = false
    private var released = false
    private var lastDrawNs = 0L
    private var lastPtsNs = -1L
    private val minIntervalNs = (1_000_000_000L / fps) * 85 / 100
    private val heartbeatMs = 100L

    private val quad: FloatBuffer = floatBuffer(
        // x, y, s, t
        -1f, -1f, 0f, 0f,
        1f, -1f, 1f, 0f,
        -1f, 1f, 0f, 1f,
        1f, 1f, 1f, 1f,
    )

    /** Inicializa o GL na thread própria e devolve a Surface onde o VirtualDisplay deve desenhar. */
    fun start(): Surface {
        val latch = CountDownLatch(1)
        var error: Throwable? = null
        handler.post {
            try {
                initGl()
            } catch (t: Throwable) {
                error = t
            }
            latch.countDown()
        }
        check(latch.await(5, TimeUnit.SECONDS)) { "Timeout ao iniciar o OpenGL" }
        error?.let { throw it }
        handler.postDelayed(heartbeat, heartbeatMs)
        return inputSurface
    }

    private fun initGl() {
        egl = EglCore()
        encEglSurface = egl.createWindowSurface(encoderSurface)
        egl.makeCurrent(encEglSurface)

        program = buildProgram(VERTEX, FRAGMENT)
        aPos = GLES20.glGetAttribLocation(program, "aPos")
        aTex = GLES20.glGetAttribLocation(program, "aTex")
        uTexM = GLES20.glGetUniformLocation(program, "uTexM")
        uFlip = GLES20.glGetUniformLocation(program, "uFlip")

        val t = IntArray(1)
        GLES20.glGenTextures(1, t, 0)
        oesTex = t[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTex)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        surfaceTexture = SurfaceTexture(oesTex).apply {
            setDefaultBufferSize(srcWidth, srcHeight)
            setOnFrameAvailableListener({ onFrameAvailable() }, handler)
        }
        inputSurface = Surface(surfaceTexture)
    }

    private fun onFrameAvailable() {
        if (released) return
        try {
            surfaceTexture.updateTexImage()
            surfaceTexture.getTransformMatrix(texMatrix)
            hasFrame = true
            val now = System.nanoTime()
            if (now - lastDrawNs >= minIntervalNs) drawToEncoder(now)
        } catch (t: Throwable) {
            Log.e(TAG, "Erro no frame", t)
        }
    }

    /** Mantém o vídeo com a duração certa mesmo quando a tela fica parada. */
    private val heartbeat = object : Runnable {
        override fun run() {
            if (released) return
            val now = System.nanoTime()
            if (hasFrame && now - lastDrawNs >= heartbeatMs * 1_000_000L) {
                try { drawToEncoder(now) } catch (t: Throwable) { Log.e(TAG, "heartbeat", t) }
            }
            handler.postDelayed(this, heartbeatMs)
        }
    }

    private fun drawToEncoder(nowNs: Long) {
        if (clock.isPaused) return // pausado: não alimenta o encoder
        var pts = clock.ptsNs(nowNs)
        if (pts <= lastPtsNs) pts = lastPtsNs + 1_000
        lastPtsNs = pts
        lastDrawNs = nowNs

        egl.makeCurrent(encEglSurface)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glViewport(0, 0, encWidth, encHeight)
        drawQuad(flip = 1f)
        egl.setPresentationTime(encEglSurface, pts)
        egl.swapBuffers(encEglSurface)
    }

    private fun drawQuad(flip: Float) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTex)
        GLES20.glUniformMatrix4fv(uTexM, 1, false, texMatrix, 0)
        GLES20.glUniform1f(uFlip, flip)
        quad.position(0)
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 16, quad)
        GLES20.glEnableVertexAttribArray(aPos)
        quad.position(2)
        GLES20.glVertexAttribPointer(aTex, 2, GLES20.GL_FLOAT, false, 16, quad)
        GLES20.glEnableVertexAttribArray(aTex)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisableVertexAttribArray(aTex)
    }

    /**
     * Captura o frame atual em resolução nativa da tela. O callback roda na thread GL;
     * recebe null se ainda não chegou nenhum frame.
     */
    fun capture(callback: (Bitmap?) -> Unit) {
        handler.post {
            if (released || !hasFrame) {
                callback(null); return@post
            }
            val bmp = try {
                readFrame()
            } catch (t: Throwable) {
                Log.e(TAG, "Falha no print", t); null
            }
            callback(bmp)
        }
    }

    private fun readFrame(): Bitmap {
        egl.makeCurrent(encEglSurface)
        ensureFbo()
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo)
        GLES20.glViewport(0, 0, srcWidth, srcHeight)
        // Desenha invertido em Y para que a 1ª linha lida pelo glReadPixels seja o topo da imagem.
        drawQuad(flip = -1f)
        val buf = ByteBuffer.allocateDirect(srcWidth * srcHeight * 4).order(ByteOrder.nativeOrder())
        GLES20.glPixelStorei(GLES20.GL_PACK_ALIGNMENT, 1)
        GLES20.glReadPixels(0, 0, srcWidth, srcHeight, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        buf.rewind()
        return Bitmap.createBitmap(srcWidth, srcHeight, Bitmap.Config.ARGB_8888).apply {
            copyPixelsFromBuffer(buf)
        }
    }

    private fun ensureFbo() {
        if (fbo != 0) return
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        fboTex = ids[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fboTex)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, srcWidth, srcHeight, 0,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null
        )
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glGenFramebuffers(1, ids, 0)
        fbo = ids[0]
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo)
        GLES20.glFramebufferTexture2D(
            GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, fboTex, 0
        )
        val status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        check(status == GLES20.GL_FRAMEBUFFER_COMPLETE) { "FBO incompleto: $status" }
    }

    /** Libera tudo e encerra a thread GL (bloqueia até terminar). */
    fun release() {
        val latch = CountDownLatch(1)
        handler.post {
            released = true
            handler.removeCallbacks(heartbeat)
            try {
                if (::surfaceTexture.isInitialized) {
                    surfaceTexture.setOnFrameAvailableListener(null)
                    surfaceTexture.release()
                }
                if (::inputSurface.isInitialized) inputSurface.release()
                if (::egl.isInitialized) {
                    if (fbo != 0) {
                        GLES20.glDeleteFramebuffers(1, intArrayOf(fbo), 0)
                        GLES20.glDeleteTextures(1, intArrayOf(fboTex), 0)
                    }
                    if (program != 0) GLES20.glDeleteProgram(program)
                    if (::encEglSurface.isInitialized) egl.releaseSurface(encEglSurface)
                    egl.release()
                }
            } catch (t: Throwable) {
                Log.e(TAG, "release", t)
            }
            latch.countDown()
        }
        latch.await(3, TimeUnit.SECONDS)
        thread.quitSafely()
    }

    companion object {
        private const val TAG = "ScreenRenderer"

        private const val VERTEX = """
            attribute vec2 aPos;
            attribute vec4 aTex;
            uniform mat4 uTexM;
            uniform float uFlip;
            varying vec2 vTex;
            void main() {
                gl_Position = vec4(aPos.x, aPos.y * uFlip, 0.0, 1.0);
                vTex = (uTexM * aTex).xy;
            }
        """

        private const val FRAGMENT = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTex;
            uniform samplerExternalOES sTex;
            void main() {
                gl_FragColor = vec4(texture2D(sTex, vTex).rgb, 1.0);
            }
        """

        private fun floatBuffer(vararg v: Float): FloatBuffer =
            ByteBuffer.allocateDirect(v.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                put(v); position(0)
            }

        private fun compile(type: Int, src: String): Int {
            val s = GLES20.glCreateShader(type)
            GLES20.glShaderSource(s, src.trimIndent())
            GLES20.glCompileShader(s)
            val ok = IntArray(1)
            GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0)
            if (ok[0] == 0) {
                val log = GLES20.glGetShaderInfoLog(s)
                GLES20.glDeleteShader(s)
                error("Shader não compilou: $log")
            }
            return s
        }

        private fun buildProgram(vs: String, fs: String): Int {
            val p = GLES20.glCreateProgram()
            GLES20.glAttachShader(p, compile(GLES20.GL_VERTEX_SHADER, vs))
            GLES20.glAttachShader(p, compile(GLES20.GL_FRAGMENT_SHADER, fs))
            GLES20.glLinkProgram(p)
            val ok = IntArray(1)
            GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0)
            check(ok[0] != 0) { "Programa GL não linkou: " + GLES20.glGetProgramInfoLog(p) }
            return p
        }

    }
}
