package com.gravaeprint.app.service

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.util.Log
import com.gravaeprint.app.data.AudioMode

/** Captura áudio (microfone ou som interno) e codifica em AAC para o MediaMuxer. */
class AudioEncoder private constructor(
    private val record: AudioRecord,
    private val channels: Int,
    private val muxer: SafeMuxer,
    private val clock: RecordingClock,
) {
    private val codec: MediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
    private var track = -1
    @Volatile private var running = false
    private var thread: Thread? = null
    private var lastPtsUs = -1L

    init {
        val fmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, channels).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16 * 1024)
        }
        codec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
    }

    fun start() {
        codec.start()
        record.startRecording()
        running = true
        thread = Thread(::loop, "GravaEPrint-audio").apply { start() }
    }

    private fun loop() {
        val bytesPerFrame = 2 * channels
        val chunk = ByteArray(1024 * bytesPerFrame)
        val info = MediaCodec.BufferInfo()
        try {
            while (running) {
                val n = record.read(chunk, 0, chunk.size)
                if (n <= 0) continue
                if (!clock.isPaused) {
                    // timestamp do início deste bloco
                    val durUs = n.toLong() / bytesPerFrame * 1_000_000L / SAMPLE_RATE
                    var pts = clock.ptsNs() / 1000 - durUs
                    if (pts <= lastPtsUs) pts = lastPtsUs + 1
                    lastPtsUs = pts
                    val inIdx = codec.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val ib = codec.getInputBuffer(inIdx)!!
                        ib.clear()
                        val len = minOf(n, ib.remaining())
                        ib.put(chunk, 0, len)
                        codec.queueInputBuffer(inIdx, 0, len, pts.coerceAtLeast(0), 0)
                    }
                }
                drain(info, false)
            }
            // fim do fluxo
            val inIdx = codec.dequeueInputBuffer(50_000)
            if (inIdx >= 0) {
                codec.queueInputBuffer(inIdx, 0, 0, lastPtsUs + 1, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                drain(info, true)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "loop", t)
        }
    }

    private fun drain(info: MediaCodec.BufferInfo, untilEos: Boolean) {
        val deadline = System.currentTimeMillis() + 1_500
        while (true) {
            val idx = codec.dequeueOutputBuffer(info, if (untilEos) 10_000 else 0)
            when {
                idx == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!untilEos || System.currentTimeMillis() > deadline) return
                }
                idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> track = muxer.addTrack(codec.outputFormat)
                idx >= 0 -> {
                    val buf = codec.getOutputBuffer(idx)
                    if (buf != null && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && track >= 0) {
                        muxer.write(track, buf, info)
                    }
                    codec.releaseOutputBuffer(idx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }

    fun stop() {
        running = false
        thread?.join(3_000)
        runCatching { record.stop() }
        runCatching { record.release() }
        runCatching { codec.stop() }
        runCatching { codec.release() }
    }

    companion object {
        private const val TAG = "AudioEncoder"
        const val SAMPLE_RATE = 44_100

        /** Cria o AudioRecord (e nº de canais), ou devolve null se não for possível (sem permissão, etc.). */
        @SuppressLint("MissingPermission")
        fun createRecord(mode: AudioMode, projection: MediaProjection): Pair<AudioRecord, Int>? {
            if (mode == AudioMode.NONE) return null
            val channels = if (mode == AudioMode.INTERNAL) 2 else 1
            val chMask = if (channels == 2) AudioFormat.CHANNEL_IN_STEREO else AudioFormat.CHANNEL_IN_MONO
            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(chMask)
                .build()
            val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, chMask, AudioFormat.ENCODING_PCM_16BIT)
            val bufSize = maxOf(minBuf * 4, 16 * 1024)
            return try {
                val builder = AudioRecord.Builder().setAudioFormat(format).setBufferSizeInBytes(bufSize)
                if (mode == AudioMode.INTERNAL) {
                    val cfg = AudioPlaybackCaptureConfiguration.Builder(projection)
                        .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                        .addMatchingUsage(AudioAttributes.USAGE_GAME)
                        .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                        .build()
                    builder.setAudioPlaybackCaptureConfig(cfg)
                } else {
                    builder.setAudioSource(MediaRecorder.AudioSource.MIC)
                }
                val rec = builder.build()
                if (rec.state != AudioRecord.STATE_INITIALIZED) {
                    rec.release(); null
                } else rec to channels
            } catch (t: Throwable) {
                Log.e(TAG, "AudioRecord", t); null
            }
        }

        fun build(rec: AudioRecord, channels: Int, muxer: SafeMuxer, clock: RecordingClock) =
            AudioEncoder(rec, channels, muxer, clock)
    }
}
