package com.gravaeprint.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Estado da gravação compartilhado entre o serviço e as telas. */
object RecorderState {
    data class Status(val recording: Boolean = false, val paused: Boolean = false, val elapsedMs: Long = 0)

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status

    fun set(s: Status) { _status.value = s }

    fun format(ms: Long): String {
        val total = ms / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }
}
