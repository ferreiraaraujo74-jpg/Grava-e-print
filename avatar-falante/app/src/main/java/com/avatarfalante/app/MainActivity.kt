package com.avatarfalante.app

import android.Manifest
import android.app.RecoverableSecurityException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.avatarfalante.app.data.Media
import com.avatarfalante.app.data.VideoItem
import com.avatarfalante.app.ui.AvatarTheme
import com.avatarfalante.app.ui.HomeScreen
import com.avatarfalante.app.ui.StudioScreen
import com.avatarfalante.app.ui.StudioViewModel
import com.avatarfalante.app.ui.VideosScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private enum class Screen { HOME, STUDIO, VIDEOS }

    private var screen by mutableStateOf(Screen.HOME)
    private var videos by mutableStateOf<List<VideoItem>>(emptyList())
    private val vm: StudioViewModel by viewModels()
    private var cameraUri: Uri? = null
    private var afterMic: (() -> Unit)? = null

    private val pickPhoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(::openPhoto) }
    private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) cameraUri?.let(::openPhoto)
    }
    private val cameraPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) launchCamera() else toast("Permita a câmera para tirar a foto")
    }
    private val micPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) afterMic?.invoke() else toast("Permita o microfone para gravar a sua voz")
        afterMic = null
    }
    private val readPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { reloadVideos() }
    private val deleteReq = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { reloadVideos() }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            AvatarTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    LaunchedEffect(Unit) { vm.messages.collect { toast(it) } }
                    BackHandler(enabled = screen != Screen.HOME) { goHome() }
                    Box(Modifier.safeDrawingPadding().imePadding()) {
                        when (screen) {
                            Screen.HOME -> HomeScreen(
                                loading = vm.loading,
                                onPick = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                onCamera = ::takePicture,
                                onCartoon = { vm.useCartoon(); screen = Screen.STUDIO },
                                onVideos = ::openVideos,
                            )
                            Screen.STUDIO -> StudioScreen(
                                vm = vm,
                                onBack = ::goHome,
                                onNeedMic = ::withMic,
                                onInstallVoice = ::installVoice,
                                onVideos = ::openVideos,
                            )
                            Screen.VIDEOS -> VideosScreen(
                                items = videos,
                                onBack = ::goHome,
                                onOpen = { v -> runCatching { startActivity(Media.openVideo(v.uri)) }.onFailure { toast("Nenhum app para abrir o vídeo") } },
                                onShare = { v -> startActivity(Media.shareVideo(v.uri)) },
                                onDelete = ::deleteVideo,
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (screen == Screen.VIDEOS) reloadVideos()
    }

    override fun onStop() {
        super.onStop()
        vm.stopPlayback()
    }

    private fun goHome() {
        vm.stopPlayback()
        screen = Screen.HOME
    }

    private fun openPhoto(uri: Uri) {
        lifecycleScope.launch { if (vm.usePhoto(uri)) screen = Screen.STUDIO }
    }

    private fun takePicture() {
        if (granted(Manifest.permission.CAMERA)) launchCamera() else cameraPerm.launch(Manifest.permission.CAMERA)
    }

    private fun launchCamera() {
        val uri = Media.newCameraUri(this)
        cameraUri = uri
        runCatching { takePhoto.launch(uri) }.onFailure { toast("Nenhum app de câmera encontrado") }
    }

    private fun withMic(action: () -> Unit) {
        if (granted(Manifest.permission.RECORD_AUDIO)) action()
        else { afterMic = action; micPerm.launch(Manifest.permission.RECORD_AUDIO) }
    }

    private fun installVoice() {
        runCatching { startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)) }
            .onFailure { toast("Instale o \"Serviços de voz do Google\" pela Play Store") }
    }

    private fun openVideos() {
        vm.stopPlayback()
        screen = Screen.VIDEOS
        val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE
        val prefs = getSharedPreferences("app", MODE_PRIVATE)
        // Os vídeos deste app aparecem sem permissão; ela só serve para os de instalações antigas.
        if (!granted(perm) && !prefs.getBoolean("askedRead", false)) {
            prefs.edit().putBoolean("askedRead", true).apply()
            readPerm.launch(perm)
        }
        reloadVideos()
    }

    private fun reloadVideos() {
        lifecycleScope.launch { videos = withContext(Dispatchers.IO) { Media.videos(this@MainActivity) } }
    }

    private fun deleteVideo(v: VideoItem) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) { contentResolver.delete(v.uri, null, null) }
                toast("Apagado"); reloadVideos()
            } catch (e: SecurityException) {
                val sender = when {
                    Build.VERSION.SDK_INT >= 30 -> MediaStore.createDeleteRequest(contentResolver, listOf(v.uri)).intentSender
                    e is RecoverableSecurityException -> e.userAction.actionIntent.intentSender
                    else -> null
                }
                if (sender != null) deleteReq.launch(IntentSenderRequest.Builder(sender).build()) else toast("Sem permissão para apagar")
            }
        }
    }

    private fun granted(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED
    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()
}
