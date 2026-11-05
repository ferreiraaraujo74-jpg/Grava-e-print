package com.avatarfalante.app.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Grava a voz do microfone em PCM mono 44,1 kHz. */
class MicRecorder {
    private var record: AudioRecord? = null
    private var thread: Thread? = null
    @Volatile private var running = false
    private val data = ByteArrayOutputStream()
    @Volatile var level = 0f   // 0..1, para o medidor na tela
        private set

    @SuppressLint("MissingPermission")
    fun start() {
        val ch = AudioFormat.CHANNEL_IN_MONO
        val enc = AudioFormat.ENCODING_PCM_16BIT
        val min = AudioRecord.getMinBufferSize(RATE, ch, enc)
        val rec = AudioRecord(MediaRecorder.AudioSource.MIC, RATE, ch, enc, maxOf(min * 2, 8192))
        check(rec.state == AudioRecord.STATE_INITIALIZED) { "Microfone indisponível" }
        data.reset()
        record = rec
        running = true
        rec.startRecording()
        thread = Thread({
            val buf = ShortArray(2048)
            val bb = ByteBuffer.allocate(buf.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            while (running) {
                val n = rec.read(buf, 0, buf.size)
                if (n <= 0) continue
                var peak = 0
                bb.clear()
                for (i in 0 until n) { bb.putShort(buf[i]); peak = maxOf(peak, kotlin.math.abs(buf[i].toInt())) }
                synchronized(data) { data.write(bb.array(), 0, n * 2) }
                level = peak / 32768f
                if (data.size() > MAX_SEC * RATE * 2) running = false
            }
        }, "mic").apply { start() }
    }

    val elapsedSec: Float get() = synchronized(data) { data.size() / 2f / RATE }
    val isRecording get() = running

    fun stop(): PcmAudio {
        running = false
        thread?.join(1500)
        runCatching { record?.stop() }
        runCatching { record?.release() }
        record = null
        val bytes = synchronized(data) { data.toByteArray() }
        val sh = ShortArray(bytes.size / 2)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(sh)
        level = 0f
        return PcmAudio(sh, RATE, 1)
    }

    companion object {
        const val RATE = 44_100
        const val MAX_SEC = 120
    }
}
