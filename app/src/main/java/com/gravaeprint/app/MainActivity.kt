package com.gravaeprint.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gravaeprint.app.data.AudioMode
import com.gravaeprint.app.data.RecordConfig
import com.gravaeprint.app.service.RecorderService
import com.gravaeprint.app.ui.GravaTheme
import com.gravaeprint.app.ui.MainScreen

/**
 * Tela principal. Ao tocar em "Iniciar gravação", pede as permissões em sequência,
 * explicando cada uma, e por fim a captura de tela (pedida a cada gravação).
 */
class MainActivity : ComponentActivity() {

    private enum class Step { OVERLAY, MIC, NOTIFICATIONS, CAPTURE }

    private var config by mutableStateOf(RecordConfig())
    private var explain by mutableStateOf<Step?>(null)
    /** Config efetiva da gravação atual (o áudio pode cair para "sem áudio" se negado). */
    private var sessionConfig = RecordConfig()

    private val overlayLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (!Settings.canDrawOverlays(this)) {
            toast("Sem essa permissão a bolha flutuante não aparece. Use os botões da notificação.")
        }
        next(Step.MIC)
    }

    private val micLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok) {
            toast("Microfone negado — a gravação será sem áudio")
            sessionConfig = sessionConfig.copy(audio = AudioMode.NONE)
        }
        next(Step.NOTIFICATIONS)
    }

    private val notifLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        next(Step.CAPTURE)
    }

    private val captureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val data = r.data
        if (r.resultCode == Activity.RESULT_OK && data != null) {
            RecorderService.start(this, r.resultCode, data, sessionConfig)
            moveTaskToBack(true) // minimiza para o usuário gravar o que quiser
        } else {
            toast("Captura de tela cancelada")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        config = RecordConfig.load(this)

        setContent {
            GravaTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val status by RecorderState.status.collectAsStateWithLifecycle()
                    androidx.compose.foundation.layout.Box(Modifier.safeDrawingPadding()) {
                        MainScreen(
                            config = config,
                            status = status,
                            onConfig = { config = it; it.save(this@MainActivity) },
                            onStart = ::beginFlow,
                            onStop = { RecorderService.send(this@MainActivity, RecorderService.ACTION_STOP) },
                            onPause = { RecorderService.send(this@MainActivity, RecorderService.ACTION_PAUSE) },
                            onPrint = { RecorderService.send(this@MainActivity, RecorderService.ACTION_PRINT) },
                            onGallery = { startActivity(Intent(this@MainActivity, GalleryActivity::class.java)) },
                        )
                    }
                    explain?.let { step -> ExplainDialog(step) }
                }
            }
        }
    }

    private fun beginFlow() {
        if (RecorderState.status.value.recording) return
        sessionConfig = config
        next(Step.OVERLAY)
    }

    /** Avança para o próximo passo que ainda precisa de permissão. */
    private fun next(from: Step) {
        var step: Step? = from
        while (step != null) {
            if (needs(step)) {
                if (step == Step.CAPTURE && prefs.getBoolean("captureExplained", false)) confirm(step)
                else explain = step
                return
            }
            step = Step.entries.getOrNull(step.ordinal + 1)
        }
    }

    private fun needs(step: Step): Boolean = when (step) {
        Step.OVERLAY -> !Settings.canDrawOverlays(this)
        Step.MIC -> sessionConfig.audio != AudioMode.NONE && !granted(Manifest.permission.RECORD_AUDIO)
        Step.NOTIFICATIONS -> Build.VERSION.SDK_INT >= 33 && !granted(Manifest.permission.POST_NOTIFICATIONS)
        Step.CAPTURE -> true // a captura é pedida a cada gravação
    }

    private val prefs by lazy { getSharedPreferences("config", MODE_PRIVATE) }

    private fun granted(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun confirm(step: Step) {
        explain = null
        when (step) {
            Step.OVERLAY -> overlayLauncher.launch(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
            Step.MIC -> micLauncher.launch(Manifest.permission.RECORD_AUDIO)
            Step.NOTIFICATIONS -> if (Build.VERSION.SDK_INT >= 33) {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else next(Step.CAPTURE)
            Step.CAPTURE -> {
                prefs.edit().putBoolean("captureExplained", true).apply()
                val mpm = getSystemService(MediaProjectionManager::class.java)
                captureLauncher.launch(mpm.createScreenCaptureIntent())
            }
        }
    }

    private fun skip(step: Step) {
        explain = null
        when (step) {
            Step.OVERLAY -> { toast("Sem a bolha, use os botões da notificação"); next(Step.MIC) }
            Step.MIC -> { sessionConfig = sessionConfig.copy(audio = AudioMode.NONE); next(Step.NOTIFICATIONS) }
            Step.NOTIFICATIONS -> next(Step.CAPTURE)
            Step.CAPTURE -> Unit
        }
    }

    @androidx.compose.runtime.Composable
    private fun ExplainDialog(step: Step) {
        val (title, text) = when (step) {
            Step.OVERLAY -> "Sobrepor a outros apps" to
                "Para mostrar a bolha flutuante por cima de qualquer app, o Android pede a permissão " +
                "\"Sobrepor a outros apps\". Na próxima tela, ative a opção para o Grava & Print e volte."
            Step.MIC -> "Microfone" to
                if (sessionConfig.audio == AudioMode.INTERNAL)
                    "Para gravar o som interno do celular, o Android exige a permissão de áudio (microfone). " +
                        "O app só grava o som dos apps, não a sua voz."
                else "Para gravar a sua voz junto com o vídeo, permita o acesso ao microfone."
            Step.NOTIFICATIONS -> "Notificações" to
                "A notificação fixa mostra o tempo de gravação e os botões Print, Pausar e Parar. " +
                    "Permita as notificações para usar esses controles."
            Step.CAPTURE -> "Captura de tela" to
                "Agora o Android vai perguntar se você permite gravar a tela. Escolha \"Tela inteira\" " +
                    "e toque em \"Iniciar\". Isso é pedido a cada gravação, por segurança."
        }
        AlertDialog(
            onDismissRequest = { explain = null },
            title = { Text(title) },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { confirm(step) }) { Text("Continuar") } },
            dismissButton = {
                TextButton(onClick = { if (step == Step.CAPTURE) explain = null else skip(step) }) {
                    Text(if (step == Step.CAPTURE) "Cancelar" else "Agora não")
                }
            },
        )
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
