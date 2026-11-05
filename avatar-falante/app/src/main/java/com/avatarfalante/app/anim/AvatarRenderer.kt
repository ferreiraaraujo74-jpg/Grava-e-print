package com.avatarfalante.app.anim

import android.graphics.Canvas

/** Desenha um quadro do avatar num Canvas de [w] x [h]. */
interface AvatarRenderer {
    /** Proporção natural (largura / altura). */
    val aspect: Float
    fun draw(canvas: Canvas, w: Int, h: Int, s: AnimState)
}

internal fun smoothstep(e0: Float, e1: Float, x: Float): Float {
    val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
    return t * t * (3 - 2 * t)
}
