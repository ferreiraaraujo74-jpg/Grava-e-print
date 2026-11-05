package com.avatarfalante.app.anim

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.max

enum class HairStyle(val label: String) { SHORT("Curto"), LONG("Longo"), CURLY("Cacheado"), BUN("Coque"), SPIKY("Espetado"), BALD("Careca") }

data class CartoonLook(
    val skin: Int = 0xFFF1C27D.toInt(),
    val hair: Int = 0xFF3B2416.toInt(),
    val hairStyle: HairStyle = HairStyle.SHORT,
    val eyes: Int = 0xFF5B3A1E.toInt(),
    val shirt: Int = 0xFF2563EB.toInt(),
    val background: Int = 0xFF5EEAD4.toInt(),
    val glasses: Boolean = false,
) {
    companion object {
        val skins = listOf(0xFFFFE0C4, 0xFFF1C27D, 0xFFE0AC69, 0xFFC68642, 0xFF8D5524, 0xFF5C3A21).map { it.toInt() }
        val hairs = listOf(0xFF1C1C1C, 0xFF3B2416, 0xFF7A4A24, 0xFFD8B36A, 0xFFB5441E, 0xFF9AA0A6, 0xFF7C3AED, 0xFFEC4899).map { it.toInt() }
        val eyeColors = listOf(0xFF5B3A1E, 0xFF2E6DB4, 0xFF3C8D4F, 0xFF6B7280, 0xFF1C1C1C).map { it.toInt() }
        val shirts = listOf(0xFF2563EB, 0xFFDC2626, 0xFF16A34A, 0xFFF59E0B, 0xFF111827, 0xFFF9FAFB, 0xFF9333EA, 0xFFDB2777).map { it.toInt() }
        val backgrounds = listOf(0xFF5EEAD4, 0xFFFDE68A, 0xFFBFDBFE, 0xFFFBCFE8, 0xFF1F2937, 0xFF00B140, 0xFFFFFFFF).map { it.toInt() }
    }
}

/** Personagem desenhado com Canvas (vetorial), com boca, olhos e sobrancelhas animados. */
class CartoonAvatar(var look: CartoonLook) : AvatarRenderer {
    override val aspect: Float = 9f / 16f

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val r = RectF()

    private fun darker(c: Int, f: Float): Int {
        val a = c ushr 24; val rr = (c shr 16 and 0xFF) * f; val gg = (c shr 8 and 0xFF) * f; val bb = (c and 0xFF) * f
        return (a shl 24) or (rr.toInt().coerceIn(0, 255) shl 16) or (gg.toInt().coerceIn(0, 255) shl 8) or bb.toInt().coerceIn(0, 255)
    }

    override fun draw(canvas: Canvas, w: Int, h: Int, s: AnimState) {
        val u = w.toFloat()
        val lk = look
        // fundo com leve degradê
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), lk.background, darker(lk.background, 0.82f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, u, h.toFloat(), p)
        p.shader = null

        val cx = u / 2; val cy = h * 0.40f
        val hx = 0.30f * u; val hy = 0.355f * u
        val neckX = cx; val neckY = cy + hy * 1.05f

        // corpo (balança menos que a cabeça)
        canvas.save()
        canvas.translate(s.tx * u * 0.08f, 0f)
        p.color = lk.shirt
        r.set(cx - 0.44f * u, neckY + 0.06f * u, cx + 0.44f * u, h + 0.3f * u)
        canvas.drawRoundRect(r, 0.2f * u, 0.2f * u, p)
        p.color = darker(lk.shirt, 0.8f)
        path.reset(); path.moveTo(cx - 0.1f * u, neckY + 0.06f * u); path.lineTo(cx, neckY + 0.16f * u); path.lineTo(cx + 0.1f * u, neckY + 0.06f * u); path.close()
        canvas.drawPath(path, p)
        p.color = darker(lk.skin, 0.86f)
        r.set(cx - 0.075f * u, cy + hy * 0.6f, cx + 0.075f * u, neckY + 0.08f * u)
        canvas.drawRoundRect(r, 0.05f * u, 0.05f * u, p)
        canvas.restore()

        // cabeça
        canvas.save()
        canvas.rotate(Math.toDegrees(s.roll.toDouble() * 1.6).toFloat(), neckX, neckY)
        canvas.translate(s.tx * u * 0.25f, s.ty * u * 0.25f)

        if (lk.hairStyle == HairStyle.LONG) {
            p.color = darker(lk.hair, 0.85f)
            r.set(cx - hx * 1.12f, cy - hy * 0.9f, cx + hx * 1.12f, cy + hy * 1.25f)
            canvas.drawRoundRect(r, hx, hx, p)
        }
        if (lk.hairStyle == HairStyle.BUN) {
            p.color = lk.hair
            canvas.drawCircle(cx, cy - hy * 1.08f, hx * 0.42f, p)
        }
        // orelhas
        p.color = darker(lk.skin, 0.93f)
        canvas.drawOval(cx - hx * 1.1f, cy - hy * 0.05f, cx - hx * 0.8f, cy + hy * 0.32f, p)
        canvas.drawOval(cx + hx * 0.8f, cy - hy * 0.05f, cx + hx * 1.1f, cy + hy * 0.32f, p)
        // rosto
        p.color = lk.skin
        canvas.drawOval(cx - hx, cy - hy, cx + hx, cy + hy, p)
        // bochechas
        p.color = 0x33FF5A6E
        canvas.drawCircle(cx - hx * 0.55f, cy + hy * 0.28f, hx * 0.17f, p)
        canvas.drawCircle(cx + hx * 0.55f, cy + hy * 0.28f, hx * 0.17f, p)

        // olhos
        val ey = cy - hy * 0.02f
        for (side in listOf(-1f, 1f)) {
            val ex = cx + side * hx * 0.4f
            val erx = hx * 0.2f; val ery = hx * 0.16f * (1f + s.eyesWide)
            r.set(ex - erx, ey - ery, ex + erx, ey + ery)
            canvas.save()
            path.reset(); path.addOval(r, Path.Direction.CW)
            canvas.clipPath(path)
            p.color = 0xFFFFFFFF.toInt(); canvas.drawOval(r, p)
            p.color = lk.eyes; canvas.drawCircle(ex, ey + ery * 0.1f, ery * 0.78f, p)
            p.color = 0xFF111111.toInt(); canvas.drawCircle(ex, ey + ery * 0.1f, ery * 0.38f, p)
            p.color = 0xCCFFFFFF.toInt(); canvas.drawCircle(ex + ery * 0.25f, ey - ery * 0.2f, ery * 0.18f, p)
            // pálpebra
            val lid = s.blink.coerceIn(0f, 1f)
            if (lid > 0.01f) {
                p.color = darker(lk.skin, 0.95f)
                canvas.drawRect(ex - erx, ey - ery, ex + erx, ey - ery + 2 * ery * lid, p)
            }
            canvas.restore()
            // contorno e cílios
            p.style = Paint.Style.STROKE; p.strokeWidth = u * 0.006f; p.color = 0xFF2B2B2B.toInt()
            if (s.blink > 0.85f) canvas.drawLine(ex - erx, ey + ery * 0.6f, ex + erx, ey + ery * 0.6f, p)
            else canvas.drawArc(r, 200f, 140f, false, p)
            p.style = Paint.Style.FILL

            // sobrancelha
            val by = ey - ery - hx * 0.13f - s.brow * hx * 0.07f
            val tilt = (if (s.brow < 0) -s.brow else 0f) * hx * 0.07f // bravo: ponta de dentro desce
            p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND
            p.strokeWidth = u * 0.016f; p.color = darker(lk.hair, 0.9f)
            val inner = ex - side * erx * 0.9f; val outer = ex + side * erx * 1.05f
            path.reset(); path.moveTo(inner, by + tilt)
            path.quadTo(ex, by - hx * 0.04f - (if (s.brow > 0) s.brow * hx * 0.03f else 0f), outer, by + hx * 0.02f)
            canvas.drawPath(path, p)
            p.style = Paint.Style.FILL
        }

        // nariz
        p.style = Paint.Style.STROKE; p.strokeWidth = u * 0.007f; p.color = darker(lk.skin, 0.72f); p.strokeCap = Paint.Cap.ROUND
        path.reset(); path.moveTo(cx - hx * 0.06f, cy + hy * 0.2f); path.quadTo(cx, cy + hy * 0.27f, cx + hx * 0.06f, cy + hy * 0.2f)
        canvas.drawPath(path, p)
        p.style = Paint.Style.FILL

        drawMouth(canvas, cx, cy + hy * 0.5f, hx, s)
        drawFrontHair(canvas, cx, cy, hx, hy, lk)

        if (lk.glasses) {
            p.style = Paint.Style.STROKE; p.strokeWidth = u * 0.009f; p.color = 0xFF1F2937.toInt()
            for (side in listOf(-1f, 1f)) {
                val ex = cx + side * hx * 0.4f
                r.set(ex - hx * 0.27f, ey - hx * 0.22f, ex + hx * 0.27f, ey + hx * 0.22f)
                canvas.drawRoundRect(r, hx * 0.12f, hx * 0.12f, p)
            }
            canvas.drawLine(cx - hx * 0.13f, ey - hx * 0.04f, cx + hx * 0.13f, ey - hx * 0.04f, p)
            p.style = Paint.Style.FILL
        }
        canvas.restore()
    }

    private fun drawMouth(c: Canvas, mx: Float, my: Float, hx: Float, s: AnimState) {
        val mw = hx * 0.36f * (1f + 0.3f * s.wide) * (1f + 0.12f * s.smile)
        val oh = hx * 0.48f * s.open * (1f - 0.35f * max(0f, s.wide))
        val curve = s.smile * hx * 0.12f
        if (oh < hx * 0.02f) {
            p.style = Paint.Style.STROKE; p.strokeWidth = hx * 0.045f; p.color = 0xFF7A2E2E.toInt(); p.strokeCap = Paint.Cap.ROUND
            path.reset(); path.moveTo(mx - mw, my - curve * 0.5f); path.quadTo(mx, my + curve, mx + mw, my - curve * 0.5f)
            c.drawPath(path, p)
            p.style = Paint.Style.FILL
            return
        }
        path.reset()
        path.moveTo(mx - mw, my - curve * 0.5f)
        path.quadTo(mx, my - oh * 0.25f + curve * 0.4f, mx + mw, my - curve * 0.5f)
        path.quadTo(mx, my + oh * 1.6f + curve, mx - mw, my - curve * 0.5f)
        path.close()
        p.color = 0xFF5A1520.toInt(); c.drawPath(path, p)
        c.save(); c.clipPath(path)
        p.color = 0xFFFFFFFF.toInt()
        c.drawRect(mx - mw, my - oh, mx + mw, my - oh * 0.25f + curve * 0.4f + hx * 0.06f, p)
        p.color = 0xFFE86A7A.toInt()
        c.drawOval(mx - mw * 0.55f, my + oh * 0.45f + curve * 0.5f, mx + mw * 0.55f, my + oh * 1.5f + curve, p)
        c.restore()
        p.style = Paint.Style.STROKE; p.strokeWidth = hx * 0.03f; p.color = 0xFF7A2E2E.toInt()
        c.drawPath(path, p)
        p.style = Paint.Style.FILL
    }

    private fun drawFrontHair(c: Canvas, cx: Float, cy: Float, hx: Float, hy: Float, lk: CartoonLook) {
        p.color = lk.hair
        when (lk.hairStyle) {
            HairStyle.BALD -> Unit
            HairStyle.SHORT, HairStyle.LONG, HairStyle.BUN -> {
                path.reset()
                path.moveTo(cx - hx * 1.02f, cy - hy * 0.05f)
                path.cubicTo(cx - hx * 1.1f, cy - hy * 1.25f, cx + hx * 1.1f, cy - hy * 1.25f, cx + hx * 1.02f, cy - hy * 0.05f)
                path.quadTo(cx + hx * 0.85f, cy - hy * 0.55f, cx + hx * 0.2f, cy - hy * 0.62f)
                path.quadTo(cx - hx * 0.5f, cy - hy * 0.5f, cx - hx * 1.02f, cy - hy * 0.05f)
                path.close()
                c.drawPath(path, p)
            }
            HairStyle.CURLY -> {
                val n = 11
                for (i in 0 until n) {
                    val a = Math.PI * (1.0 + i / (n - 1.0))
                    val x = cx + (hx * 0.95f * Math.cos(a)).toFloat()
                    val y = cy - hy * 0.25f + (hy * 0.85f * Math.sin(a)).toFloat()
                    c.drawCircle(x, y, hx * 0.3f, p)
                }
                c.drawOval(cx - hx * 0.9f, cy - hy * 1.12f, cx + hx * 0.9f, cy - hy * 0.45f, p)
            }
            HairStyle.SPIKY -> {
                path.reset()
                path.moveTo(cx - hx * 1.0f, cy - hy * 0.2f)
                val spikes = 7
                for (i in 0..spikes) {
                    val t = i / spikes.toFloat()
                    val x = cx - hx + 2 * hx * t
                    val base = cy - hy * (0.55f + 0.35f * (1 - (2 * t - 1) * (2 * t - 1)))
                    path.lineTo(x - hx * 0.12f, base)
                    path.lineTo(x, base - hy * 0.32f)
                    path.lineTo(x + hx * 0.12f, base)
                }
                path.lineTo(cx + hx * 1.0f, cy - hy * 0.2f)
                path.quadTo(cx, cy - hy * 0.7f, cx - hx * 1.0f, cy - hy * 0.2f)
                path.close()
                c.drawPath(path, p)
            }
        }
    }
}
