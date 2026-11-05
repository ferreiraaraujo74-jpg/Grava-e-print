package com.avatarfalante.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avatarfalante.app.anim.CartoonAvatar
import com.avatarfalante.app.anim.CartoonLook
import com.avatarfalante.app.anim.Expression
import com.avatarfalante.app.anim.HairStyle
import com.avatarfalante.app.anim.Timeline

@Composable
fun HomeScreen(loading: Boolean, onPick: () -> Unit, onCamera: () -> Unit, onCartoon: () -> Unit, onVideos: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF134E4A), Color(0xFF1E3A8A)))).padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Avatar Falante", fontSize = 28.sp, fontWeight = FontWeight.Black, color = Color.White)
                Spacer(Modifier.height(6.dp))
                Text("Faça uma foto ou um personagem falar o que você quiser, e gere um vídeo.",
                    color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
            }
            val demo = remember { CartoonAvatar(CartoonLook(hairStyle = HairStyle.CURLY, background = 0x00000000, shirt = 0xFFFF8A65.toInt())) }
            val tl = remember {
                // fala de demonstração: sílabas sintéticas
                val sr = 16000
                Timeline.fromPcm(ShortArray(sr * 20) { i ->
                    val t = i / sr.toDouble()
                    val env = Math.max(0.0, Math.sin(2 * Math.PI * 3.1 * t)) * (if ((t % 4) < 2.6) 1.0 else 0.0)
                    (Math.sin(2 * Math.PI * 200 * t) * env * 10000).toInt().toShort()
                }, sr)
            }
            val start = remember { System.nanoTime() }
            AvatarView(demo, tl, Expression.HAPPY, { (System.nanoTime() - start) / 1e9f % 20f }, Modifier.width(96.dp))
        }

        Spacer(Modifier.height(20.dp))
        Text("Com uma foto", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Action(Icons.Filled.PhotoLibrary, "Escolher foto", "Da galeria", Teal, Modifier.weight(1f), enabled = !loading, onClick = onPick)
            Action(Icons.Filled.PhotoCamera, "Tirar foto", "Com a câmera", Coral, Modifier.weight(1f), enabled = !loading, onClick = onCamera)
        }
        if (loading) {
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp)); Text("Procurando o rosto…")
            }
        }
        Text(
            "Dica: foto de frente, rosto bem iluminado, boca e olhos visíveis. Use só fotos suas ou de quem autorizou.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )

        Spacer(Modifier.height(20.dp))
        Text("Sem foto", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Action(Icons.Filled.Face, "Criar personagem", "Escolha pele, cabelo, roupa e fundo", Color(0xFFA78BFA), Modifier.fillMaxWidth(), onClick = onCartoon)

        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onVideos, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Icon(Icons.Filled.VideoLibrary, null); Spacer(Modifier.width(8.dp)); Text("Meus vídeos")
        }
        Spacer(Modifier.height(16.dp))
        Text("Tudo funciona no seu celular, de graça e sem internet: a detecção do rosto, a voz e o vídeo.",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Action(icon: ImageVector, title: String, sub: String, color: Color, modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(enabled = enabled, onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = color)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, fontWeight = FontWeight.Bold)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
