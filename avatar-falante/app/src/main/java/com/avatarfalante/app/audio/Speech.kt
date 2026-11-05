package com.avatarfalante.app.audio

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume

data class VoiceOption(val id: String, val label: String, val voice: Voice?)

/** Voz do próprio Android (gratuita). Gera um WAV a partir do texto. */
class Speech(private val ctx: Context) {
    private var tts: TextToSpeech? = null
    private val ready = CompletableDeferred<Boolean>()

    fun init() {
        if (tts != null) return
        tts = TextToSpeech(ctx.applicationContext) { status -> ready.complete(status == TextToSpeech.SUCCESS) }
    }

    suspend fun isReady(): Boolean = ready.await()

    /** Vozes em português (Brasil primeiro). Rótulos simples: "Voz 1", "Voz 2"... (+ Portugal). */
    suspend fun voices(): List<VoiceOption> {
        if (!isReady()) return emptyList()
        val t = tts ?: return emptyList()
        val all = runCatching { t.voices?.toList() }.getOrNull().orEmpty()
            .filter { it.locale.language == "pt" && !it.features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) }
            .sortedWith(compareBy({ if (it.locale.country == "BR") 0 else 1 }, { it.isNetworkConnectionRequired }, { it.name }))
        var br = 0; var pt = 0
        val opts = all.map { v ->
            val offline = if (v.isNetworkConnectionRequired) " (online)" else ""
            val label = if (v.locale.country == "BR") "Voz ${++br}$offline" else "Portugal ${++pt}$offline"
            VoiceOption(v.name, label, v)
        }
        return opts.ifEmpty { listOf(VoiceOption("default", "Padrão", null)) }
    }

    /** Português disponível no motor de voz? */
    suspend fun hasPortuguese(): Boolean {
        if (!isReady()) return false
        val r = tts?.isLanguageAvailable(Locale("pt", "BR")) ?: return false
        return r >= TextToSpeech.LANG_AVAILABLE
    }

    suspend fun synthesize(text: String, voice: VoiceOption?, rate: Float, pitch: Float, out: File): Boolean {
        if (!isReady()) return false
        val t = tts ?: return false
        if (voice?.voice != null) t.voice = voice.voice else t.language = Locale("pt", "BR")
        t.setSpeechRate(rate)
        t.setPitch(pitch)
        val id = "fala_" + System.nanoTime()
        return suspendCancellableCoroutine { cont ->
            t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) { if (utteranceId == id && cont.isActive) cont.resume(true) }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) { if (utteranceId == id && cont.isActive) cont.resume(false) }
                override fun onError(utteranceId: String?, errorCode: Int) { if (utteranceId == id && cont.isActive) cont.resume(false) }
            })
            out.delete()
            val r = t.synthesizeToFile(text, Bundle(), out, id)
            if (r != TextToSpeech.SUCCESS && cont.isActive) cont.resume(false)
        }
    }

    fun shutdown() {
        tts?.shutdown(); tts = null
    }

    companion object {
        /** Limite do motor de voz por chamada. */
        val maxChars: Int get() = TextToSpeech.getMaxSpeechInputLength()
    }
}
