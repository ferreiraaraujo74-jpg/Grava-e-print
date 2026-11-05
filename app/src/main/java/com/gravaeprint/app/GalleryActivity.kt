package com.gravaeprint.app

import android.Manifest
import android.app.RecoverableSecurityException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.gravaeprint.app.data.MediaItem
import com.gravaeprint.app.data.MediaRepository
import com.gravaeprint.app.ui.GalleryScreen
import com.gravaeprint.app.ui.GravaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Galeria do app: abas Vídeos e Prints; abrir, compartilhar ou apagar. */
class GalleryActivity : ComponentActivity() {

    private val videos = mutableStateOf<List<MediaItem>>(emptyList())
    private val prints = mutableStateOf<List<MediaItem>>(emptyList())

    private val readPerms = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { reload() }
    private val deleteLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        if (it.resultCode == RESULT_OK) toast("Apagado")
        reload()
    }
    private var pendingDelete: MediaItem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            GravaTheme {
                GalleryScreen(
                    videos = videos.value,
                    prints = prints.value,
                    onBack = { finish() },
                    onOpen = ::open,
                    onShare = ::share,
                    onDelete = ::delete,
                )
            }
        }
        askReadPermissionOnce()
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    /** Arquivos criados pelo próprio app aparecem sem permissão; esta só serve para os de instalações antigas. */
    private fun askReadPermissionOnce() {
        val perms = if (Build.VERSION.SDK_INT >= 33)
            arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_IMAGES)
        else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        val prefs = getSharedPreferences("config", MODE_PRIVATE)
        val missing = perms.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty() && !prefs.getBoolean("askedRead", false)) {
            prefs.edit().putBoolean("askedRead", true).apply()
            readPerms.launch(missing.toTypedArray())
        }
    }

    private fun reload() {
        lifecycleScope.launch {
            val (v, p) = withContext(Dispatchers.IO) {
                MediaRepository.videos(this@GalleryActivity) to MediaRepository.prints(this@GalleryActivity)
            }
            videos.value = v
            prints.value = p
        }
    }

    private fun open(item: MediaItem) {
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(item.uri, item.mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { startActivity(i) }.onFailure { toast("Nenhum app para abrir este arquivo") }
    }

    private fun share(item: MediaItem) {
        val send = Intent(Intent.ACTION_SEND)
            .setType(item.mime)
            .putExtra(Intent.EXTRA_STREAM, item.uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(send, "Compartilhar com…"))
    }

    private fun delete(item: MediaItem) {
        lifecycleScope.launch {
            try {
                val n = withContext(Dispatchers.IO) { contentResolver.delete(item.uri, null, null) }
                if (n > 0) toast("Apagado")
                reload()
            } catch (e: SecurityException) {
                // Arquivo de outra instalação: o sistema precisa confirmar.
                pendingDelete = item
                val sender = when {
                    Build.VERSION.SDK_INT >= 30 ->
                        MediaStore.createDeleteRequest(contentResolver, listOf(item.uri)).intentSender
                    e is RecoverableSecurityException -> e.userAction.actionIntent.intentSender
                    else -> null
                }
                if (sender != null) deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
                else toast("Sem permissão para apagar")
            }
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
