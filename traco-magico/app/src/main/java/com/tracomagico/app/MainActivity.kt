package com.tracomagico.app

import android.Manifest
import android.app.RecoverableSecurityException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.tracomagico.app.data.Creation
import com.tracomagico.app.data.Images
import com.tracomagico.app.data.Work
import com.tracomagico.app.ui.CreationsScreen
import com.tracomagico.app.ui.EditorScreen
import com.tracomagico.app.ui.EditorViewModel
import com.tracomagico.app.ui.HomeScreen
import com.tracomagico.app.ui.TraceScreen
import com.tracomagico.app.ui.TracoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private sealed interface Screen {
        data object Home : Screen
        data object Editor : Screen
        data class Trace(val from: Screen) : Screen
        data object Creations : Screen
    }

    private var screen by mutableStateOf<Screen>(Screen.Home)
    private var creations by mutableStateOf<List<Creation>>(emptyList())
    private val editor: EditorViewModel by viewModels()
    private var cameraUri: Uri? = null

    private val pickPhoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) openEditor(uri)
    }
    private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = cameraUri
        if (ok && uri != null) openEditor(uri)
    }
    private val cameraPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) launchCamera() else toast("Permita a câmera para tirar fotos")
    }
    private val readPerm = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { reloadCreations() }
    private val deleteReq = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { reloadCreations() }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleShareIntent(intent)

        setContent {
            TracoTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val s = screen
                    BackHandler(enabled = s != Screen.Home) {
                        screen = if (s is Screen.Trace) s.from else Screen.Home
                    }
                    when (s) {
                        Screen.Home -> Box(Modifier.safeDrawingPadding()) {
                            HomeScreen(
                                onPickPhoto = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                onCamera = ::takePicture,
                                onCreations = ::openCreations,
                            )
                        }
                        Screen.Editor -> Box(Modifier.safeDrawingPadding()) {
                            EditorScreen(
                                vm = editor,
                                onBack = { screen = Screen.Home },
                                onTrace = { b -> Work.traceImage = b; screen = Screen.Trace(Screen.Editor) },
                            )
                        }
                        is Screen.Trace -> {
                            val img = Work.traceImage
                            if (img == null) screen = s.from
                            else TraceScreen(img, onBack = { screen = s.from })
                        }
                        Screen.Creations -> Box(Modifier.safeDrawingPadding()) {
                            CreationsScreen(
                                items = creations,
                                onBack = { screen = Screen.Home },
                                onOpen = { c ->
                                    runCatching {
                                        startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(c.uri, "image/*")
                                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                                    }.onFailure { toast("Nenhum app para abrir a imagem") }
                                },
                                onShare = { c -> startActivity(Images.shareUri(c.uri)) },
                                onTrace = ::traceCreation,
                                onDelete = ::deleteCreation,
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        if (screen == Screen.Creations) reloadCreations()
    }

    /** Foto recebida pelo menu "Compartilhar" de outro app. */
    private fun handleShareIntent(i: Intent?) {
        if (i?.action != Intent.ACTION_SEND) return
        @Suppress("DEPRECATION")
        val uri: Uri? = if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else i.getParcelableExtra(Intent.EXTRA_STREAM)
        if (uri != null) openEditor(uri)
    }

    private fun openEditor(uri: Uri) {
        editor.load(uri)
        screen = Screen.Editor
    }

    private fun takePicture() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) launchCamera()
        else cameraPerm.launch(Manifest.permission.CAMERA)
    }

    private fun launchCamera() {
        val uri = Images.newCameraUri(this)
        cameraUri = uri
        runCatching { takePhoto.launch(uri) }.onFailure { toast("Nenhum app de câmera encontrado") }
    }

    private fun openCreations() {
        screen = Screen.Creations
        val prefs = getSharedPreferences("app", MODE_PRIVATE)
        val perms = if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        val missing = perms.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        // As criações deste app aparecem sem permissão; ela só serve para as de instalações antigas.
        if (missing.isNotEmpty() && !prefs.getBoolean("askedRead", false)) {
            prefs.edit().putBoolean("askedRead", true).apply()
            readPerm.launch(missing.toTypedArray())
        }
        reloadCreations()
    }

    private fun reloadCreations() {
        lifecycleScope.launch {
            creations = withContext(Dispatchers.IO) { Images.creations(this@MainActivity) }
        }
    }

    private fun traceCreation(c: Creation) {
        lifecycleScope.launch {
            val b = withContext(Dispatchers.IO) { runCatching { Images.load(this@MainActivity, c.uri, 2048) }.getOrNull() }
            if (b == null) { toast("Não foi possível abrir"); return@launch }
            Work.traceImage = b
            screen = Screen.Trace(Screen.Creations)
        }
    }

    private fun deleteCreation(c: Creation) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) { contentResolver.delete(c.uri, null, null) }
                toast("Apagado")
                reloadCreations()
            } catch (e: SecurityException) {
                val sender = when {
                    Build.VERSION.SDK_INT >= 30 -> MediaStore.createDeleteRequest(contentResolver, listOf(c.uri)).intentSender
                    e is RecoverableSecurityException -> e.userAction.actionIntent.intentSender
                    else -> null
                }
                if (sender != null) deleteReq.launch(IntentSenderRequest.Builder(sender).build())
                else toast("Sem permissão para apagar")
            }
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
