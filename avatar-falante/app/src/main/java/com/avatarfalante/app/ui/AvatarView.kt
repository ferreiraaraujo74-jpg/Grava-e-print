package com.avatarfalante.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import com.avatarfalante.app.anim.AvatarRenderer
import com.avatarfalante.app.anim.Expression
import com.avatarfalante.app.anim.Timeline

/**
 * Prévia animada. Enquanto o áudio toca, segue o tempo do áudio; parado, fica "vivo"
 * (piscando e mexendo a cabeça de leve).
 */
@Composable
fun AvatarView(
    renderer: AvatarRenderer,
    speaking: Timeline,
    expression: Expression,
    playbackTime: () -> Float?,
    modifier: Modifier = Modifier,
) {
    val idle = remember { Timeline.idle() }
    var frameNanos by remember { mutableLongStateOf(0L) }
    val start = remember { System.nanoTime() }
    LaunchedEffect(Unit) { while (true) withFrameNanos { frameNanos = it } }

    Canvas(modifier.aspectRatio(renderer.aspect)) {
        if (frameNanos < 0) return@Canvas // lê o relógio: redesenha a cada quadro
        val pt = playbackTime()
        val st = if (pt != null) speaking.state(pt, expression)
        else idle.state((System.nanoTime() - start) / 1e9f, expression)
        drawIntoCanvas { c -> renderer.draw(c.nativeCanvas, size.width.toInt(), size.height.toInt(), st) }
    }
}
