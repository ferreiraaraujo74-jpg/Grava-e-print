package com.avatarfalante.app.ui

import android.app.Application
import android.graphics.Bitmap
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.avatarfalante.app.anim.AvatarRenderer
import com.avatarfalante.app.anim.CartoonAvatar
import com.avatarfalante.app.anim.CartoonLook
import com.avatarfalante.app.anim.Expression
import com.avatarfalante.app.anim.FaceGeom
import com.avatarfalante.app.anim.PhotoAvatar
import com.avatarfalante.app.anim.Timeline
import com.avatarfalante.app.audio.MicRecorder
import com.avatarfalante.app.audio.PcmAudio
import com.avatarfalante.app.audio.Speech
import com.avatarfalante.app.audio.VoiceOption
import com.avatarfalante.app.data.Media
import com.avatarfalante.app.export.VideoExporter
import com.avatarfalante.app.face.FaceFinder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class StudioViewModel(app: Application) : AndroidViewModel(app) {

    // ---------------- avatar ----------------
    var renderer by mutableStateOf<AvatarRenderer?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var isCartoon by mutableStateOf(false)
        private set
    var look by mutableStateOf(CartoonLook())
        private set
    var expression by mutableStateOf(Expression.NEUTRAL)

    private var photo: Bitmap? = null
    private var geom: FaceGeom? = null

    // ---------------- áudio ----------------
    var audio by mutableStateOf<PcmAudio?>(null)
        private set
    var timeline by mutableStateOf(Timeline.idle())
        private set
    private var audioFile: File? = null

    var text by mutableStateOf("Olá! Eu sou o seu avatar falante. Escreva qualquer coisa e eu falo para você.")
    var voices by mutableStateOf<List<VoiceOption>>(emptyList())
        private set
    var voice by mutableStateOf<VoiceOption?>(null)
    var rate by mutableFloatStateOf(1f)
    var pitch by mutableFloatStateOf(1f)
    var speechReady by mutableStateOf<Boolean?>(null)   // null = carregando
        private set
    var hasPortuguese by mutableStateOf(true)
        private set
    var busyAudio by mutableStateOf(false)
        private set

    private val speech = Speech(app)
    private val mic = MicRecorder()
    var recording by mutableStateOf(false)
        private set
    var recSeconds by mutableFloatStateOf(0f)
        private set
    var recLevel by mutableFloatStateOf(0f)
        private set

    // ---------------- reprodução ----------------
    private var player: MediaPlayer? = null
    var playing by mutableStateOf(false)
        private set
    /** Tempo atual do áudio (s) enquanto toca. */
    fun playbackTime(): Float? = player?.takeIf { playing }?.let { it.currentPosition / 1000f }

    // ---------------- exportação ----------------
    var exportProgress by mutableStateOf<Float?>(null)
        private set
    private var exportJob: Job? = null
    @Volatile private var cancelExport = false
    var lastVideo by mutableStateOf<Uri?>(null)

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages
    private fun say(m: String) { _messages.tryEmit(m) }

    init {
        speech.init()
        viewModelScope.launch {
            val ok = speech.isReady()
            speechReady = ok
            if (ok) {
                hasPortuguese = speech.hasPortuguese()
                voices = speech.voices()
                voice = voices.firstOrNull()
            }
        }
    }

    // ---------------- escolher avatar ----------------

    /** Carrega a foto e procura o rosto. Retorna false se não deu para usar. */
    suspend fun usePhoto(uri: Uri): Boolean {
        loading = true
        try {
            val bmp = withContext(Dispatchers.IO) { Media.loadPhoto(getApplication(), uri) }
            return when (val r = runCatching { FaceFinder.find(bmp) }.getOrElse { FaceFinder.Result.NoFace }) {
                is FaceFinder.Result.Found -> {
                    photo = bmp; geom = r.geom; isCartoon = false
                    renderer = PhotoAvatar(bmp, r.geom)
                    true
                }
                FaceFinder.Result.NoFace -> { say("Não encontrei um rosto. Use uma foto de frente, bem iluminada."); false }
                FaceFinder.Result.Unusable -> { say("O rosto precisa estar de frente e sem nada cobrindo a boca e os olhos."); false }
            }
        } catch (t: Throwable) {
            say("Não foi possível abrir a foto"); return false
        } finally {
            loading = false
        }
    }

    fun useCartoon() {
        isCartoon = true
        photo = null; geom = null
        renderer = CartoonAvatar(look)
    }

    fun updateLook(l: CartoonLook) {
        look = l
        (renderer as? CartoonAvatar)?.look = l
    }

    private fun newRendererCopy(): AvatarRenderer? =
        if (isCartoon) CartoonAvatar(look) else photo?.let { p -> geom?.let { PhotoAvatar(p, it) } }

    // ---------------- gerar áudio ----------------

    fun speak() {
        val t = text.trim()
        if (t.isEmpty()) { say("Escreva o que o avatar vai falar"); return }
        if (busyAudio) return
        busyAudio = true
        stopPlayback()
        viewModelScope.launch {
            try {
                val f = File(getApplication<Application>().cacheDir, "fala.wav")
                val ok = speech.synthesize(t.take(Speech.maxChars), voice, rate, pitch, f)
                if (!ok || !f.exists() || f.length() < 100) { say("O motor de voz não gerou o áudio. Tente outra voz."); return@launch }
                val pcm = withContext(Dispatchers.IO) { PcmAudio.decode(f) }
                setAudio(pcm, f)
            } catch (e: Throwable) {
                say("Erro ao gerar a voz")
            } finally {
                busyAudio = false
            }
        }
    }

    fun toggleRecording() {
        if (recording) {
            recording = false
            val pcm = mic.stop()
            if (pcm.durationSec < 0.3f) { say("Gravação muito curta"); return }
            viewModelScope.launch {
                val f = File(getApplication<Application>().cacheDir, "voz.wav")
                withContext(Dispatchers.IO) { pcm.writeWav(f) }
                setAudio(pcm, f)
            }
        } else {
            stopPlayback()
            try {
                mic.start()
            } catch (t: Throwable) {
                say("Não foi possível usar o microfone"); return
            }
            recording = true
            viewModelScope.launch {
                while (isActive && recording) {
                    recSeconds = mic.elapsedSec
                    recLevel = mic.level
                    if (!mic.isRecording) { toggleRecording(); break }
                    delay(50)
                }
            }
        }
    }

    fun importAudio(uri: Uri) {
        if (busyAudio) return
        busyAudio = true
        stopPlayback()
        viewModelScope.launch {
            try {
                val pcm = withContext(Dispatchers.IO) {
                    val full = PcmAudio.decode(getApplication(), uri)
                    val max = MAX_AUDIO_SEC * full.sampleRate * full.channels
                    if (full.samples.size > max) PcmAudio(full.samples.copyOf(max), full.sampleRate, full.channels) else full
                }
                if (pcm.samples.isEmpty()) { say("Áudio vazio"); return@launch }
                if (pcm.durationSec >= MAX_AUDIO_SEC - 0.1f) say("Usando só os primeiros $MAX_AUDIO_SEC segundos")
                val f = File(getApplication<Application>().cacheDir, "arquivo.wav")
                withContext(Dispatchers.IO) { pcm.writeWav(f) }
                setAudio(pcm, f)
            } catch (t: Throwable) {
                say("Não consegui ler esse arquivo de áudio")
            } finally {
                busyAudio = false
            }
        }
    }

    private suspend fun setAudio(pcm: PcmAudio, file: File) {
        val tl = withContext(Dispatchers.Default) { Timeline.fromPcm(pcm.mono(), pcm.sampleRate) }
        audio = pcm; audioFile = file; timeline = tl
        play()
    }

    // ---------------- reprodução ----------------

    fun play() {
        val f = audioFile ?: return
        stopPlayback()
        try {
            player = MediaPlayer().apply {
                setDataSource(f.absolutePath)
                setOnCompletionListener { playing = false }
                prepare()
                start()
            }
            playing = true
        } catch (t: Throwable) {
            say("Não foi possível tocar o áudio")
        }
    }

    fun stopPlayback() {
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        playing = false
    }

    // ---------------- exportar ----------------

    fun export() {
        val a = audio ?: run { say("Primeiro gere a voz, grave ou escolha um áudio"); return }
        val r = newRendererCopy() ?: return
        if (exportJob?.isActive == true) return
        stopPlayback()
        cancelExport = false
        exportProgress = 0f
        val tl = timeline
        val expr = expression
        exportJob = viewModelScope.launch {
            try {
                val uri = withContext(Dispatchers.Default) {
                    VideoExporter.export(getApplication(), r, tl, expr, a, onProgress = { p -> exportProgress = p }, isCancelled = { cancelExport })
                }
                if (uri != null) lastVideo = uri
                else if (!cancelExport) say("Não foi possível gerar o vídeo")
            } catch (t: Throwable) {
                say("Erro ao gerar o vídeo: ${t.message ?: t.javaClass.simpleName}")
            } finally {
                exportProgress = null
            }
        }
    }

    fun cancelExport() { cancelExport = true }

    override fun onCleared() {
        stopPlayback()
        if (recording) runCatching { mic.stop() }
        speech.shutdown()
        super.onCleared()
    }

    companion object {
        const val MAX_AUDIO_SEC = 180
    }
}
