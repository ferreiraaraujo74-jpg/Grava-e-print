package com.avatarfalante.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.avatarfalante.app.anim.CartoonLook
import com.avatarfalante.app.anim.Expression
import com.avatarfalante.app.anim.HairStyle
import com.avatarfalante.app.audio.Speech
import com.avatarfalante.app.data.Media

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioScreen(vm: StudioViewModel, onBack: () -> Unit, onNeedMic: (() -> Unit) -> Unit, onInstallVoice: () -> Unit, onVideos: () -> Unit) {
    val ctx = LocalContext.current
    val renderer = vm.renderer ?: return
    var tab by remember { mutableIntStateOf(0) }
    var showLook by remember { mutableStateOf(false) }
    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let(vm::importAudio) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(if (vm.isCartoon) "Personagem" else "Minha foto") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } },
            actions = {
                if (vm.isCartoon) TextButton(onClick = { showLook = true }) {
                    Icon(Icons.Filled.Brush, null); Spacer(Modifier.width(4.dp)); Text("Personalizar")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )

        // ---------------- prévia ----------------
        Box(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp).clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center,
        ) {
            AvatarView(renderer, vm.timeline, vm.expression, vm::playbackTime)
        }

        // ---------------- expressão ----------------
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Expression.entries.forEach { e ->
                FilterChip(selected = vm.expression == e, onClick = { vm.expression = e }, label = { Text(e.label) })
            }
        }

        // ---------------- origem da fala ----------------
        TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
            Tab(tab == 0, { tab = 0 }, text = { Text("Texto") }, icon = { Icon(Icons.Filled.RecordVoiceOver, null) })
            Tab(tab == 1, { tab = 1 }, text = { Text("Gravar voz") }, icon = { Icon(Icons.Filled.Mic, null) })
            Tab(tab == 2, { tab = 2 }, text = { Text("Áudio") }, icon = { Icon(Icons.Filled.AudioFile, null) })
        }
        Column(Modifier.heightIn(max = 270.dp).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 8.dp)) {
            when (tab) {
                0 -> TextTab(vm, onInstallVoice)
                1 -> MicTab(vm, onNeedMic)
                else -> {
                    Text("Escolha uma música, um áudio do WhatsApp ou qualquer arquivo de som (até ${StudioViewModel.MAX_AUDIO_SEC / 60} minutos).",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    FilledTonalButton(onClick = { pickAudio.launch("audio/*") }, enabled = !vm.busyAudio, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.AudioFile, null); Spacer(Modifier.width(8.dp)); Text(if (vm.busyAudio) "Lendo…" else "Escolher áudio")
                    }
                }
            }
        }

        // ---------------- ações ----------------
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { if (vm.playing) vm.stopPlayback() else vm.play() },
                enabled = vm.audio != null, modifier = Modifier.weight(1f).height(50.dp),
            ) {
                Icon(if (vm.playing) Icons.Filled.Stop else Icons.Filled.PlayArrow, null)
                Spacer(Modifier.width(6.dp)); Text(if (vm.playing) "Parar" else "Ouvir")
            }
            Button(onClick = vm::export, enabled = vm.audio != null, modifier = Modifier.weight(1.3f).height(50.dp)) {
                Icon(Icons.Filled.Movie, null); Spacer(Modifier.width(6.dp)); Text("Gerar vídeo")
            }
        }
    }

    // ---------------- diálogos ----------------
    vm.exportProgress?.let { p ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Gerando vídeo…") },
            text = {
                Column {
                    LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp)); Text("${(p * 100).toInt()}%")
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = vm::cancelExport) { Text("Cancelar") } },
        )
    }
    vm.lastVideo?.let { uri ->
        AlertDialog(
            onDismissRequest = { vm.lastVideo = null },
            title = { Text("Vídeo pronto!") },
            text = { Text("Salvo em Filmes/AvatarFalante. Quer compartilhar agora?") },
            confirmButton = { Button(onClick = { vm.lastVideo = null; ctx.startActivity(Media.shareVideo(uri)) }) { Text("Compartilhar") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { vm.lastVideo = null; runCatching { ctx.startActivity(Media.openVideo(uri)) } }) { Text("Assistir") }
                    TextButton(onClick = { vm.lastVideo = null; onVideos() }) { Text("Meus vídeos") }
                }
            },
        )
    }
    if (showLook) {
        ModalBottomSheet(onDismissRequest = { showLook = false }) {
            LookEditor(vm.look, vm::updateLook)
        }
    }
}

@Composable
private fun TextTab(vm: StudioViewModel, onInstallVoice: () -> Unit) {
    when {
        vm.speechReady == null -> Text("Carregando vozes…")
        vm.speechReady == false || !vm.hasPortuguese -> {
            Text("Seu celular não tem voz em português instalada. Instale a voz (é grátis) e volte aqui.",
                color = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(onClick = onInstallVoice) { Text("Instalar voz em português") }
            Spacer(Modifier.height(8.dp))
        }
    }
    OutlinedTextField(
        value = vm.text, onValueChange = { vm.text = it.take(Speech.maxChars) },
        label = { Text("O que o avatar vai falar") }, minLines = 2, maxLines = 5,
        modifier = Modifier.fillMaxWidth(),
    )
    if (vm.voices.size > 1) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            vm.voices.forEach { v -> FilterChip(selected = vm.voice?.id == v.id, onClick = { vm.voice = v }, label = { Text(v.label) }) }
        }
    }
    LabeledSlider("Velocidade", vm.rate, 0.5f..1.6f) { vm.rate = it }
    LabeledSlider("Tom", vm.pitch, 0.5f..1.6f) { vm.pitch = it }
    Text("Tom baixo = voz mais grave; alto = mais aguda (dá para fazer voz de criança ou de robô).",
        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(6.dp))
    FilledTonalButton(onClick = vm::speak, enabled = !vm.busyAudio && vm.speechReady == true, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Filled.RecordVoiceOver, null); Spacer(Modifier.width(8.dp)); Text(if (vm.busyAudio) "Gerando voz…" else "Gerar fala")
    }
}

@Composable
private fun MicTab(vm: StudioViewModel, onNeedMic: (() -> Unit) -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (vm.recording) "Gravando… toque para parar" else "Toque no botão e fale. O avatar repete com a sua voz.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        val ring = if (vm.recording) 4.dp + (vm.recLevel * 14).dp else 0.dp
        Box(
            Modifier.size(84.dp).border(ring, Color(0x66FF5252), CircleShape).clip(CircleShape)
                .background(if (vm.recording) Color(0xFFE53935) else MaterialTheme.colorScheme.primary)
                .clickable { if (vm.recording) vm.toggleRecording() else onNeedMic { vm.toggleRecording() } },
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (vm.recording) Icons.Filled.Stop else Icons.Filled.Mic, "Gravar", tint = Color.White, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(8.dp))
        val s = vm.recSeconds.toInt()
        Text("%02d:%02d".format(s / 60, s % 60), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(84.dp))
        Slider(value = value, onValueChange = onChange, valueRange = range, modifier = Modifier.weight(1f))
        Text("%.1fx".format(value), style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(40.dp), textAlign = TextAlign.End)
    }
}

@Composable
private fun LookEditor(look: CartoonLook, onChange: (CartoonLook) -> Unit) {
    Column(Modifier.padding(horizontal = 18.dp).padding(bottom = 32.dp).verticalScroll(rememberScrollState())) {
        Text("Personalizar personagem", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text("Cabelo", style = MaterialTheme.typography.labelLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HairStyle.entries.forEach { h -> FilterChip(look.hairStyle == h, { onChange(look.copy(hairStyle = h)) }, label = { Text(h.label) }) }
        }
        Swatches("Pele", CartoonLook.skins, look.skin) { onChange(look.copy(skin = it)) }
        Swatches("Cor do cabelo", CartoonLook.hairs, look.hair) { onChange(look.copy(hair = it)) }
        Swatches("Olhos", CartoonLook.eyeColors, look.eyes) { onChange(look.copy(eyes = it)) }
        Swatches("Roupa", CartoonLook.shirts, look.shirt) { onChange(look.copy(shirt = it)) }
        Swatches("Fundo (verde = chroma key)", CartoonLook.backgrounds, look.background) { onChange(look.copy(background = it)) }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Text("Óculos", modifier = Modifier.weight(1f))
            Switch(checked = look.glasses, onCheckedChange = { onChange(look.copy(glasses = it)) })
        }
    }
}

@Composable
private fun Swatches(label: String, colors: List<Int>, selected: Int, onPick: (Int) -> Unit) {
    Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        colors.forEach { c ->
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(Color(c))
                    .border(if (c == selected) 3.dp else 1.dp, if (c == selected) MaterialTheme.colorScheme.primary else Color(0x55FFFFFF), CircleShape)
                    .clickable { onPick(c) },
            )
        }
    }
}
