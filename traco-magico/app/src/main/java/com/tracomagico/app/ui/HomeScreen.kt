package com.tracomagico.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tracomagico.app.filters.Category
import com.tracomagico.app.filters.Styles

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(onPickPhoto: () -> Unit, onCamera: () -> Unit, onCreations: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
    ) {
        // Cabeçalho com uma "folha de papel" e traços desenhados
        Box(
            Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF3B2A66), Color(0xFF7A2E5C)))),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width; val h = size.height
                val paper = Path().apply {
                    moveTo(w * 0.58f, h * 0.16f); lineTo(w * 0.93f, h * 0.10f)
                    lineTo(w * 0.97f, h * 0.86f); lineTo(w * 0.62f, h * 0.92f); close()
                }
                drawPath(paper, Paper)
                val ink = Color(0xFF2A2633)
                for (i in 0..5) {
                    val y = h * (0.28f + i * 0.1f)
                    val p = Path().apply {
                        moveTo(w * 0.66f, y)
                        cubicTo(w * 0.72f, y - 14f, w * 0.78f, y + 14f, w * 0.9f, y - 4f)
                    }
                    drawPath(p, ink.copy(alpha = 0.25f + i * 0.1f), style = Stroke(width = 3f, cap = StrokeCap.Round))
                }
                drawCircle(Pink.copy(alpha = 0.35f), radius = h * 0.5f, center = Offset(w * 0.05f, h * 1.05f))
            }
            Column(Modifier.align(Alignment.CenterStart).padding(start = 22.dp, end = 160.dp)) {
                Text("Traço Mágico", fontSize = 28.sp, fontWeight = FontWeight.Black, color = Color.White)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Transforme suas fotos em desenhos a lápis, stencil, cartoon e muito mais.",
                    color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigAction(Icons.Filled.PhotoLibrary, "Escolher foto", "Da galeria", Lilac, Modifier.weight(1f), onPickPhoto)
            BigAction(Icons.Filled.PhotoCamera, "Tirar foto", "Com a câmera", Pink, Modifier.weight(1f), onCamera)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onCreations, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Icon(Icons.Filled.Collections, null); Spacer(Modifier.size(8.dp)); Text("Minhas criações")
        }

        Spacer(Modifier.height(24.dp))
        Text("${Styles.all.size} estilos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Category.entries.forEach { c ->
                val n = Styles.all.count { it.category == c }
                Text(
                    "${c.label} · $n",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("Como funciona", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Step("1", "Escolha uma foto ou tire uma na hora.")
        Step("2", "Toque num estilo e ajuste a intensidade. Segure a imagem para comparar com a foto.")
        Step("3", "Salve, compartilhe ou use \"Traçar\" para copiar o desenho no papel, com a câmera ou a mesa de luz.")
    }
}

@Composable
private fun BigAction(icon: ImageVector, title: String, sub: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(18.dp),
    ) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = color)
        }
        Spacer(Modifier.height(14.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Step(n: String, text: String) {
    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(24.dp).clip(RoundedCornerShape(50)).background(Lilac), contentAlignment = Alignment.Center) {
            Text(n, color = Color(0xFF1B1030), fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Spacer(Modifier.size(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
