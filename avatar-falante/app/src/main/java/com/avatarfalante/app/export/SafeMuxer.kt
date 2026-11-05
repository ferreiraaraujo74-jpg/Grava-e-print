package com.avatarfalante.app.export

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.FileDescriptor
import java.nio.ByteBuffer

/**
 * MediaMuxer thread-safe. Só inicia quando todas as trilhas (vídeo e, se houver, áudio)
 * foram adicionadas; amostras que chegam antes disso ficam guardadas.
 */
class SafeMuxer(fd: FileDescriptor, private val expectedTracks: Int) {
    private val muxer = MediaMuxer(fd, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    private var added = 0
    private var started = false
    private var stopped = false
    private val pending = ArrayList<Triple<Int, ByteBuffer, MediaCodec.BufferInfo>>()
    private val lastPts = HashMap<Int, Long>()

    @Volatile var samplesWritten = 0L
        private set

    @Synchronized
    fun addTrack(format: MediaFormat): Int {
        val track = muxer.addTrack(format)
        added++
        if (added == expectedTracks) {
            muxer.start()
            started = true
            for ((t, b, i) in pending) writeNow(t, b, i)
            pending.clear()
        }
        return track
    }

    @Synchronized
    fun write(track: Int, buf: ByteBuffer, info: MediaCodec.BufferInfo) {
        if (stopped || info.size <= 0) return
        if (!started) {
            if (pending.size > 100_000) return // proteção de memória
            val copy = ByteBuffer.allocateDirect(info.size)
            buf.position(info.offset); buf.limit(info.offset + info.size)
            copy.put(buf); copy.flip()
            val ci = MediaCodec.BufferInfo().apply { set(0, info.size, info.presentationTimeUs, info.flags) }
            pending.add(Triple(track, copy, ci))
            return
        }
        writeNow(track, buf, info)
    }

    private fun writeNow(track: Int, buf: ByteBuffer, info: MediaCodec.BufferInfo) {
        val prev = lastPts[track] ?: -1L
        if (info.presentationTimeUs <= prev) info.presentationTimeUs = prev + 1
        lastPts[track] = info.presentationTimeUs
        try {
            muxer.writeSampleData(track, buf, info)
            samplesWritten++
        } catch (t: Throwable) {
            Log.e("SafeMuxer", "writeSampleData", t)
        }
    }

    @Synchronized
    fun stop() {
        if (stopped) return
        stopped = true
        try {
            if (started) muxer.stop()
        } catch (t: Throwable) {
            Log.e("SafeMuxer", "stop", t)
            samplesWritten = 0
        }
        runCatching { muxer.release() }
    }
}
