package com.gravaeprint.app.service

/** Relógio da gravação que desconta o tempo pausado (para timestamps e cronômetro). */
class RecordingClock {
    @Volatile private var startNs = 0L
    @Volatile private var pausedTotalNs = 0L
    @Volatile private var pauseStartNs = 0L
    @Volatile var isPaused = false
        private set

    fun start() {
        startNs = System.nanoTime(); pausedTotalNs = 0; isPaused = false
    }

    @Synchronized fun pause() {
        if (isPaused) return
        pauseStartNs = System.nanoTime(); isPaused = true
    }

    @Synchronized fun resume() {
        if (!isPaused) return
        pausedTotalNs += System.nanoTime() - pauseStartNs; isPaused = false
    }

    /** Timestamp de apresentação (ns) correspondente ao instante [nowNs]. */
    fun ptsNs(nowNs: Long = System.nanoTime()): Long = (nowNs - startNs - pausedTotalNs).coerceAtLeast(0)

    fun elapsedMs(): Long {
        val now = if (isPaused) pauseStartNs else System.nanoTime()
        return ((now - startNs - pausedTotalNs) / 1_000_000L).coerceAtLeast(0)
    }
}
