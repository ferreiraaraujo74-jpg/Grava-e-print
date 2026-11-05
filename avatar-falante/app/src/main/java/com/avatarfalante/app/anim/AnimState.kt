package com.avatarfalante.app.anim

enum class Expression(val label: String, val smile: Float, val brow: Float, val eyesWide: Float) {
    NEUTRAL("Neutro", 0f, 0f, 0f),
    HAPPY("Feliz", 0.8f, 0.2f, 0f),
    SURPRISED("Surpreso", 0f, 1f, 0.25f),
    ANGRY("Bravo", -0.3f, -0.8f, 0.05f),
    SAD("Triste", -0.6f, 0.4f, -0.1f),
}

/** Tudo o que define um quadro da animação. Valores pequenos (frações), ver os renderizadores. */
data class AnimState(
    val open: Float = 0f,      // 0..1 abertura da boca
    val wide: Float = 0f,      // -1 (bico "o/u") .. 1 (largo "e/i")
    val blink: Float = 0f,     // 0..1 olhos fechando
    val smile: Float = 0f,     // -1..1
    val brow: Float = 0f,      // -1 (franzido) .. 1 (levantado)
    val eyesWide: Float = 0f,  // olhos mais abertos (surpreso)
    val roll: Float = 0f,      // rad, inclinação da cabeça
    val tx: Float = 0f,        // deslocamento da cabeça (fração do tamanho do rosto)
    val ty: Float = 0f,
)
