package com.avatarfalante.app.anim

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

data class P(val x: Float, val y: Float)

/**
 * Pontos do rosto (em pixels da foto). Vêm do ML Kit no app, ou de outro detector nos testes.
 * "Esquerdo/direito" são da imagem (não da pessoa); a ordem dos pontos não importa.
 */
class FaceGeom(
    val oval: List<P>,
    val upperLipOuter: List<P>,
    val upperLipInner: List<P>,
    val lowerLipInner: List<P>,
    val lowerLipOuter: List<P>,
    val eyeA: List<P>,
    val eyeB: List<P>,
    val browA: List<P> = emptyList(),
    val browB: List<P> = emptyList(),
    rollDegrees: Float? = null,
) {
    /** Inclinação da cabeça (rad), pelo ângulo entre os olhos se não for informada. */
    val roll: Float
    val cosR: Float
    val sinR: Float

    val mouthCenter: P
    val mouthWidth: Float
    val faceCenter: P
    val faceWidth: Float
    val faceHeight: Float
    /** Distância (no referencial do rosto) do centro da boca até o queixo. */
    val chinDist: Float
    val lowerLipThickness: Float
    val eyes: List<Eye>
    val brows: List<List<P>>

    class Eye(val center: P, val width: Float, val top: Float, val bottom: Float)

    init {
        val ca = centroid(eyeA); val cb = centroid(eyeB)
        val (l, r) = if (ca.x <= cb.x) ca to cb else cb to ca
        roll = rollDegrees?.let { Math.toRadians(it.toDouble()).toFloat() } ?: atan2(r.y - l.y, r.x - l.x)
        cosR = cos(roll); sinR = sin(roll)

        mouthCenter = centroid(upperLipInner + lowerLipInner)
        val outer = (upperLipOuter + lowerLipOuter).map { toLocal(it, mouthCenter) }
        mouthWidth = outer.maxOf { it.x } - outer.minOf { it.x }
        lowerLipThickness = max(2f, centroid(lowerLipOuter.map { toLocal(it, mouthCenter) }).y -
            centroid(lowerLipInner.map { toLocal(it, mouthCenter) }).y)

        faceCenter = centroid(oval)
        val o = oval.map { toLocal(it, faceCenter) }
        faceWidth = o.maxOf { it.x } - o.minOf { it.x }
        faceHeight = o.maxOf { it.y } - o.minOf { it.y }
        chinDist = oval.map { toLocal(it, mouthCenter) }.maxOf { it.y }

        eyes = listOf(eyeA, eyeB).map { pts ->
            val c = centroid(pts)
            val loc = pts.map { toLocal(it, c) }
            Eye(c, loc.maxOf { it.x } - loc.minOf { it.x }, loc.minOf { it.y }, loc.maxOf { it.y })
        }
        brows = listOf(browA, browB).filter { it.isNotEmpty() }
    }

    /** Converte para o referencial do rosto (sem inclinação), com origem em [o]. */
    fun toLocal(p: P, o: P): P {
        val dx = p.x - o.x; val dy = p.y - o.y
        return P(dx * cosR + dy * sinR, -dx * sinR + dy * cosR)
    }

    /** Vetor do referencial do rosto de volta para a imagem. */
    fun toImageVec(u: Float, v: Float): P = P(u * cosR - v * sinR, u * sinR + v * cosR)

    fun isPlausible(): Boolean =
        mouthWidth > 4f && faceWidth > mouthWidth && faceHeight > 10f && eyes.all { it.width > 2f } &&
            abs(roll) < 0.8f

    companion object {
        fun centroid(pts: List<P>): P = P(pts.sumOf { it.x.toDouble() }.toFloat() / pts.size, pts.sumOf { it.y.toDouble() }.toFloat() / pts.size)
    }
}
