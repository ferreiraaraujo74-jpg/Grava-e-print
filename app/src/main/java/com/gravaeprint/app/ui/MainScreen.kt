package com.gravaeprint.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gravaeprint.app.RecorderState
import com.gravaeprint.app.data.AudioMode
import com.gravaeprint.app.data.RecordConfig

@Composable
fun MainScreen(
    config: RecordConfig,
    status: RecorderState.Status,
    onConfig: (RecordConfig) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onPause: () -> Unit,
    onPrint: () -> Unit,
    onGallery: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            buildTitle(), fontSize = 30.sp, fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            "Grave a tela e tire prints sem parar o vídeo",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))

        // ---------- botão grande ----------
        val recording = status.recording
        Button(
            onClick = if (recording) onStop else onStart,
            shape = CircleShape,
            modifier = Modifier.size(200.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (recording) Color(0xFFD32F2F) else Orange,
                contentColor = if (recording) Color.White else Color.Black,
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    if (recording) Icons.Filled.Stop else Icons.Filled.FiberManualRecord,
                    contentDescription = null, modifier = Modifier.size(48.dp),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (recording) "Parar gravação" else "Iniciar gravação",
                    fontWeight = FontWeight.Bold, fontSize = 17.sp, textAlign = TextAlign.Center,
                )
                if (recording) {
                    Text(
                        RecorderState.format(status.elapsedMs),
                        fontFamily = FontFamily.Monospace, fontSize = 20.sp,
                    )
                }
            }
        }

        if (recording) {
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(onClick = onPrint) {
                    Icon(Icons.Filled.PhotoCamera, null); Spacer(Modifier.width(6.dp)); Text("Print")
                }
                FilledTonalButton(onClick = onPause) {
                    Icon(if (status.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause, null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (status.paused) "Retomar" else "Pausar")
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        // ---------- opções ----------
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Column(Modifier.padding(16.dp)) {
                OptionRow("Qualidade", listOf("720p" to 720, "1080p" to 1080), config.quality, !recording) {
                    onConfig(config.copy(quality = it))
                }
                Spacer(Modifier.height(16.dp))
                OptionRow("FPS", listOf("30 fps" to 30, "60 fps" to 60), config.fps, !recording) {
                    onConfig(config.copy(fps = it))
                }
                Spacer(Modifier.height(16.dp))
                OptionRow(
                    "Áudio", AudioMode.entries.map { it.label to it }, config.audio, !recording,
                ) { onConfig(config.copy(audio = it)) }
                if (config.audio == AudioMode.INTERNAL) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Som interno: grava o áudio dos apps (Android 10+). Alguns apps bloqueiam a captura.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        OutlinedButton(onClick = onGallery, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Icon(Icons.Filled.PhotoLibrary, null)
            Spacer(Modifier.width(8.dp))
            Text("Ver gravações", fontSize = 16.sp)
        }
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth()) {
            Text(
                "Durante a gravação: toque na bolha para tirar print, segure para ver o menu.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun buildTitle() = "Grava & Print"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> OptionRow(
    title: String,
    options: List<Pair<String, T>>,
    selected: T,
    enabled: Boolean,
    onSelect: (T) -> Unit,
) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary)
    Spacer(Modifier.height(8.dp))
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, (label, value) ->
            SegmentedButton(
                selected = value == selected,
                onClick = { onSelect(value) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
                label = { Text(label, maxLines = 1, fontSize = 13.sp) },
            )
        }
    }
}
