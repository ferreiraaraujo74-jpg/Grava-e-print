package com.gravaeprint.app.data

import android.content.Context
import android.content.Intent

enum class AudioMode(val label: String) {
    NONE("Sem áudio"),
    MIC("Microfone"),
    INTERNAL("Som interno"),
}

data class RecordConfig(
    val quality: Int = 1080,   // 720 ou 1080 (lado menor do vídeo)
    val fps: Int = 30,         // 30 ou 60
    val audio: AudioMode = AudioMode.NONE,
) {
    fun putInto(i: Intent): Intent = i.putExtra(K_Q, quality).putExtra(K_F, fps).putExtra(K_A, audio.name)

    fun save(ctx: Context) {
        prefs(ctx).edit().putInt(K_Q, quality).putInt(K_F, fps).putString(K_A, audio.name).apply()
    }

    companion object {
        private const val K_Q = "quality"
        private const val K_F = "fps"
        private const val K_A = "audio"

        private fun prefs(ctx: Context) = ctx.getSharedPreferences("config", Context.MODE_PRIVATE)

        fun load(ctx: Context): RecordConfig {
            val p = prefs(ctx)
            return RecordConfig(
                quality = p.getInt(K_Q, 1080),
                fps = p.getInt(K_F, 30),
                audio = runCatching { AudioMode.valueOf(p.getString(K_A, null)!!) }.getOrDefault(AudioMode.NONE),
            )
        }

        fun from(i: Intent) = RecordConfig(
            quality = i.getIntExtra(K_Q, 1080),
            fps = i.getIntExtra(K_F, 30),
            audio = runCatching { AudioMode.valueOf(i.getStringExtra(K_A)!!) }.getOrDefault(AudioMode.NONE),
        )
    }
}
