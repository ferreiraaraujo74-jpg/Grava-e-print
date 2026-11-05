package com.gravaeprint.app.service

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.util.Log
import android.view.Surface
import kotlin.math.roundToInt

/** Encoder H.264 com entrada por Surface (alimentada pelo ScreenRenderer). */
class VideoEncoder(
    val width: Int,
    val height: Int,
    fps: Int,
    bitrate: Int,
    private val muxer: SafeMuxer,
) {
    private val codec: MediaCodec = MediaCodec.createEncoderByType(MIME)
    val inputSurface: Surface
    private var track = -1
    @Volatile private var running = true
    private val drainThread: Thread

    init {
        val format = MediaFormat.createVideoFormat(MIME, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        inputSurface = codec.createInputSurface()
        codec.start()
        drainThread = Thread(::drainLoop, "GravaEPrint-video").apply { start() }
    }

    private fun drainLoop() {
        val info = MediaCodec.BufferInfo()
        try {
            while (true) {
                val idx = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    idx == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!running && eosTimedOut()) break
                    idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> track = muxer.addTrack(codec.outputFormat)
                    idx >= 0 -> {
                        val buf = codec.getOutputBuffer(idx)
                        if (buf != null && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && track >= 0) {
                            muxer.write(track, buf, info)
                        }
                        codec.releaseOutputBuffer(idx, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "drain", t)
        }
    }

    private var eosRequestedAt = 0L
    private fun eosTimedOut() = System.currentTimeMillis() - eosRequestedAt > 2_000

    fun stop() {
        eosRequestedAt = System.currentTimeMillis()
        running = false
        runCatching { codec.signalEndOfInputStream() }
        drainThread.join(3_000)
        runCatching { codec.stop() }
        runCatching { codec.release() }
        runCatching { inputSurface.release() }
    }

    companion object {
        private const val TAG = "VideoEncoder"
        const val MIME = MediaFormat.MIMETYPE_VIDEO_AVC

        /**
         * Escolhe um tamanho que o encoder do aparelho aceita, mantendo a proporção da tela.
         * [shortSide] é 720 ou 1080.
         */
        fun pickSize(screenW: Int, screenH: Int, shortSide: Int, fps: Int): Pair<Int, Int> {
            val caps = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
                .firstOrNull { it.isEncoder && it.supportedTypes.any { t -> t.equals(MIME, true) } }
                ?.getCapabilitiesForType(MIME)?.videoCapabilities

            val portrait = screenH >= screenW
            val sShort = minOf(screenW, screenH).toDouble()
            val sLong = maxOf(screenW, screenH).toDouble()
            var scale = minOf(1.0, shortSide / sShort)
            repeat(30) {
                val a = align16(sShort * scale)
                val b = align16(sLong * scale)
                val (w, h) = if (portrait) a to b else b to a
                val ok = caps == null || (caps.isSizeSupported(w, h) &&
                    runCatching { caps.areSizeAndRateSupported(w, h, fps.toDouble()) }.getOrDefault(true))
                if (ok) return w to h
                scale *= 0.9
            }
            return if (portrait) 720 to 1280 else 1280 to 720
        }

        private fun align16(v: Double): Int = ((v / 16.0).roundToInt() * 16).coerceAtLeast(16)

        fun bitrateFor(w: Int, h: Int, fps: Int): Int {
            // ~0.16 bit por pixel por frame a 30fps; 60fps ganha 50% a mais
            val base = w.toLong() * h * 30 * 0.16
            val f = if (fps >= 60) 1.5 else 1.0
            return (base * f).toInt().coerceIn(4_000_000, 20_000_000)
        }
    }
}
