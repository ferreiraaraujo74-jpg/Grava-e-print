package com.avatarfalante.app.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Áudio PCM 16 bits (intercalado se estéreo). */
class PcmAudio(val samples: ShortArray, val sampleRate: Int, val channels: Int) {
    val durationSec: Float get() = samples.size.toFloat() / channels / sampleRate

    fun mono(): ShortArray {
        if (channels == 1) return samples
        val n = samples.size / channels
        return ShortArray(n) { i ->
            var sum = 0
            for (c in 0 until channels) sum += samples[i * channels + c]
            (sum / channels).toShort()
        }
    }

    fun writeWav(f: File) {
        RandomAccessFile(f, "rw").use { raf ->
            raf.setLength(0)
            val dataLen = samples.size * 2
            val b = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            b.put("RIFF".toByteArray()); b.putInt(36 + dataLen); b.put("WAVE".toByteArray())
            b.put("fmt ".toByteArray()); b.putInt(16); b.putShort(1); b.putShort(channels.toShort())
            b.putInt(sampleRate); b.putInt(sampleRate * channels * 2); b.putShort((channels * 2).toShort()); b.putShort(16)
            b.put("data".toByteArray()); b.putInt(dataLen)
            raf.write(b.array())
            val bb = ByteBuffer.allocate(dataLen).order(ByteOrder.LITTLE_ENDIAN)
            bb.asShortBuffer().put(samples)
            raf.write(bb.array())
        }
    }

    companion object {
        /** Decodifica qualquer áudio que o Android suporte (wav, mp3, m4a, ogg...) para PCM 16 bits. */
        fun decode(ctx: Context, uri: Uri): PcmAudio {
            val ex = MediaExtractor()
            ex.setDataSource(ctx, uri, null)
            return decode(ex)
        }

        fun decode(file: File): PcmAudio {
            val ex = MediaExtractor()
            ex.setDataSource(file.absolutePath)
            return decode(ex)
        }

        private fun decode(ex: MediaExtractor): PcmAudio {
            try {
                val track = (0 until ex.trackCount).firstOrNull {
                    ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
                } ?: error("Arquivo sem áudio")
                ex.selectTrack(track)
                val inFmt = ex.getTrackFormat(track)
                var rate = inFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                var ch = inFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                var floatPcm = false
                val codec = MediaCodec.createDecoderByType(inFmt.getString(MediaFormat.KEY_MIME)!!)
                codec.configure(inFmt, null, null, 0)
                codec.start()
                val out = ByteArrayOutputStream()
                val info = MediaCodec.BufferInfo()
                var inputDone = false
                val limitBytes = 60L * 60 * 48000 * 2 * 2 // 1 hora, por segurança
                try {
                    while (true) {
                        if (!inputDone) {
                            val i = codec.dequeueInputBuffer(10_000)
                            if (i >= 0) {
                                val buf = codec.getInputBuffer(i)!!
                                val n = ex.readSampleData(buf, 0)
                                if (n < 0) {
                                    codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true
                                } else {
                                    codec.queueInputBuffer(i, 0, n, ex.sampleTime, 0); ex.advance()
                                }
                            }
                        }
                        val o = codec.dequeueOutputBuffer(info, 10_000)
                        if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            val f = codec.outputFormat
                            rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            ch = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            floatPcm = f.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                                f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                        } else if (o >= 0) {
                            val b = codec.getOutputBuffer(o)!!
                            b.position(info.offset); b.limit(info.offset + info.size)
                            if (floatPcm) {
                                val fb = b.order(ByteOrder.nativeOrder()).asFloatBuffer()
                                val tmp = ByteBuffer.allocate(fb.remaining() * 2).order(ByteOrder.LITTLE_ENDIAN)
                                while (fb.hasRemaining()) tmp.putShort((fb.get().coerceIn(-1f, 1f) * 32767).toInt().toShort())
                                out.write(tmp.array())
                            } else {
                                val arr = ByteArray(info.size); b.get(arr); out.write(arr)
                            }
                            codec.releaseOutputBuffer(o, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                            if (out.size() > limitBytes) break
                        }
                    }
                } finally {
                    runCatching { codec.stop() }; runCatching { codec.release() }
                }
                val bytes = out.toByteArray()
                val sh = ShortArray(bytes.size / 2)
                ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(sh)
                return PcmAudio(sh, rate, ch)
            } finally {
                ex.release()
            }
        }
    }
}
