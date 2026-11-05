package com.tracomagico.app.ui

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tracomagico.app.data.Images
import com.tracomagico.app.filters.Category
import com.tracomagico.app.filters.Style
import com.tracomagico.app.filters.Styles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(vm: EditorViewModel, onBack: () -> Unit, onTrace: (Bitmap) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val style by vm.style.collectAsState()
    val intensity by vm.intensity.collectAsState()
    var busy by remember { mutableStateOf(false) }
    var category by remember { mutableStateOf(style.category) }

    fun withFinal(action: suspend (Bitmap) -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val b = vm.renderFinal()
                if (b != null) action(b)
            } finally { busy = false }
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = { Text(style.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } },
            actions = {
                IconButton(onClick = {
                    withFinal { b ->
                        val uri = withContext(Dispatchers.IO) { Images.saveToGallery(ctx, b) }
                        Toast.makeText(ctx, if (uri != null) "Salvo em Imagens/TracoMagico" else "Erro ao salvar", Toast.LENGTH_SHORT).show()
                    }
                }) { Icon(Icons.Filled.Download, "Salvar") }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )

        // ---------------- imagem ----------------
        var showOriginal by remember { mutableStateOf(false) }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .pointerInput(Unit) {
                    detectTapGestures(onPress = {
                        showOriginal = true
                        tryAwaitRelease()
                        showOriginal = false
                    })
                },
            contentAlignment = Alignment.Center,
        ) {
            val photo = vm.photo
            val result = vm.result
            when {
                vm.error != null -> Text(vm.error!!, color = MaterialTheme.colorScheme.error)
                photo == null -> CircularProgressIndicator()
                else -> {
                    Image(photo.asImageBitmap(), "Foto original", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    if (result != null && !showOriginal) {
                        Image(
                            result.asImageBitmap(), "Desenho", Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit, alpha = 1f - vm.photoMix,
                        )
                    }
                }
            }
            if (vm.rendering || busy) {
                CircularProgressIndicator(Modifier.align(Alignment.TopEnd).padding(10.dp).size(22.dp), strokeWidth = 2.5.dp)
            }
            if (showOriginal) {
                Text(
                    "Foto original", fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.TopStart).padding(10.dp)
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        Text(
            "Segure a imagem para comparar com a foto",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )

        // ---------------- estilos ----------------
        val cats = Category.entries
        ScrollableTabRow(
            selectedTabIndex = category.ordinal,
            edgePadding = 12.dp,
            containerColor = MaterialTheme.colorScheme.background,
        ) {
            cats.forEach { c ->
                Tab(selected = c == category, onClick = { category = c }, text = { Text(c.label) })
            }
        }
        val list = remember(category) { Styles.all.filter { it.category == category } }
        val listState = rememberLazyListState()
        LaunchedEffect(category) { listState.scrollToItem(0) }
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(list, key = { it.id }) { s ->
                StyleThumb(vm, s, selected = s.id == style.id) { vm.select(s) }
            }
        }

        // ---------------- ajustes ----------------
        Column(Modifier.padding(horizontal = 16.dp)) {
            LabeledSlider("Intensidade", intensity, vm::setIntensity)
            LabeledSlider("Mostrar foto", vm.photoMix) { vm.photoMix = it }
        }

        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FilledTonalButton(
                onClick = { withFinal { b -> ctx.startActivity(withContext(Dispatchers.IO) { Images.shareIntent(ctx, b) }) } },
                modifier = Modifier.weight(1f).height(50.dp),
            ) { Icon(Icons.Filled.Share, null); Spacer(Modifier.width(6.dp)); Text("Compartilhar") }
            Button(
                onClick = { withFinal { b -> onTrace(b) } },
                modifier = Modifier.weight(1f).height(50.dp),
            ) { Icon(Icons.Filled.Draw, null); Spacer(Modifier.width(6.dp)); Text("Traçar") }
        }
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(96.dp))
        Slider(value = value, onValueChange = onChange, modifier = Modifier.weight(1f).height(36.dp))
        Text("${(value * 100).toInt()}%", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(40.dp), textAlign = TextAlign.End)
    }
}

@Composable
private fun StyleThumb(vm: EditorViewModel, s: Style, selected: Boolean, onClick: () -> Unit) {
    val bmp by produceState<Bitmap?>(null, s.id, vm.photo) { value = vm.thumbnail(s) }
    Column(
        Modifier.width(78.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(74.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .then(
                    if (selected) Modifier.border(BorderStroke(3.dp, MaterialTheme.colorScheme.primary), RoundedCornerShape(14.dp))
                    else Modifier
                ),
            contentAlignment = Alignment.Center,
        ) {
            bmp?.let { Image(it.asImageBitmap(), s.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                ?: CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            s.name, fontSize = 11.sp, maxLines = 2, lineHeight = 13.sp, textAlign = TextAlign.Center,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}
