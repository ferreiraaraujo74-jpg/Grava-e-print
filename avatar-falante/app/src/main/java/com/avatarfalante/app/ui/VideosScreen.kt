package com.avatarfalante.app.ui

import android.graphics.Bitmap
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avatarfalante.app.data.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideosScreen(items: List<VideoItem>, onBack: () -> Unit, onOpen: (VideoItem) -> Unit, onShare: (VideoItem) -> Unit, onDelete: (VideoItem) -> Unit) {
    var sel by remember { mutableStateOf<VideoItem?>(null) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Meus vídeos") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Text("Nenhum vídeo ainda.\nCrie um avatar e toque em \"Gerar vídeo\".", textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyVerticalGrid(GridCells.Adaptive(110.dp), Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(items, key = { it.uri.toString() }) { v -> Thumb(v) { sel = v } }
            }
        }
    }
    sel?.let { v ->
        AlertDialog(
            onDismissRequest = { sel = null },
            title = { Text(v.name, fontSize = 16.sp) },
            text = {
                Column {
                    TextButton(onClick = { sel = null; onOpen(v) }) { Text("Assistir") }
                    TextButton(onClick = { sel = null; onShare(v) }) { Text("Compartilhar") }
                    TextButton(onClick = { sel = null; onDelete(v) }) { Text("Apagar", color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { TextButton(onClick = { sel = null }) { Text("Fechar") } },
        )
    }
}

@Composable
private fun Thumb(v: VideoItem, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val bmp by produceState<Bitmap?>(null, v.uri) {
        value = withContext(Dispatchers.IO) { runCatching { ctx.contentResolver.loadThumbnail(v.uri, Size(320, 320), null) }.getOrNull() }
    }
    Box(Modifier.aspectRatio(0.6f).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable(onClick = onClick)) {
        bmp?.let { Image(it.asImageBitmap(), v.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        Icon(Icons.Filled.PlayCircle, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.align(Alignment.Center).size(36.dp))
        val s = (v.durationMs / 1000).toInt()
        Text("%d:%02d".format(s / 60, s % 60), color = Color.White, fontSize = 11.sp,
            modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp)).padding(horizontal = 5.dp, vertical = 1.dp))
    }
}
