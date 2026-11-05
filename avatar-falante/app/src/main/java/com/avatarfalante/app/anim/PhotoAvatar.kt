package com.avatarfalante.app.anim

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Anima uma foto deformando uma malha (Canvas.drawBitmapMesh) com campos de deslocamento
 * suaves: maxilar/boca, cantos da boca, pálpebras, sobrancelhas e cabeça. O interior da
 * boca (escuro, dentes e língua) é desenhado por cima, entre os lábios deformados.
 * Tudo é monotônico (a malha nunca dobra sobre si mesma).
 */
class PhotoAvatar(private val photo: Bitmap, private val g: FaceGeom) : AvatarRenderer {

    override val aspect: Float = photo.width.toFloat() / photo.height

    private val gx = 80
    private val gy = max(8, (gx / aspect).roundToInt())
    private val verts = FloatArray((gx + 1) * (gy + 1) * 2)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val mouthPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    private val fw = g.faceWidth
    private val fh = g.faceHeight
    private val mw = g.mouthWidth
    private val faceR = max(fw, fh) / 2f
    /** Linha entre os lábios: pontos (u, v) ordenados por u, e a abertura original no centro. */
    private val midline: List<P>
    private val originalGap: Float
    private val mouthOpenInPhoto: Boolean
    private val innerHalf: Float
    private val innerMid: Float

    init {
        val up = g.upperLipInner.map { g.toLocal(it, g.mouthCenter) }.sortedBy { it.x }
        val lo = g.lowerLipInner.map { g.toLocal(it, g.mouthCenter) }.sortedBy { it.x }
        val u0 = max(up.first().x, lo.first().x); val u1 = min(up.last().x, lo.last().x)
        val n = 13
        originalGap = interp(lo, 0f) - interp(up, 0f)
        mouthOpenInPhoto = originalGap > 0.08f * g.mouthWidth
        // boca fechada: corta entre os lábios. Boca aberta (dentes à mostra): corta abaixo dos dentes de cima.
        val ratio = if (mouthOpenInPhoto) 0.72f else 0.5f
        midline = (0 until n).map { i ->
            val u = u0 + (u1 - u0) * i / (n - 1)
            P(u, interp(up, u) + (interp(lo, u) - interp(up, u)) * ratio)
        }
        innerHalf = max(2f, (u1 - u0) / 2f)
        innerMid = (u0 + u1) / 2f
    }

    private fun interp(pts: List<P>, u: Float): Float {
        if (u <= pts.first().x) return pts.first().y
        if (u >= pts.last().x) return pts.last().y
        for (i in 1 until pts.size) {
            val a = pts[i - 1]; val b = pts[i]
            if (u <= b.x) return if (b.x - a.x < 1e-3f) a.y else a.y + (b.y - a.y) * (u - a.x) / (b.x - a.x)
        }
        return pts.last().y
    }

    private val pivot: P = g.mouthCenter.let { m ->
        val v = g.toImageVec(0f, g.chinDist * 1.25f); P(m.x + v.x, m.y + v.y)
    }

    override fun draw(canvas: Canvas, w: Int, h: Int, s: AnimState) {
        val sx = w.toFloat() / photo.width
        val sy = h.toFloat() / photo.height
        val bw = photo.width.toFloat(); val bh = photo.height.toFloat()
        var k = 0
        for (j in 0..gy) {
            val y = bh * j / gy
            for (i in 0..gx) {
                val x = bw * i / gx
                val p = warp(x, y, s)
                // bordas fixas: a foto nunca descola do quadro
                verts[k++] = if (i == 0 || i == gx) x else p.x
                verts[k++] = if (j == 0 || j == gy) y else p.y
            }
        }
        canvas.save()
        canvas.scale(sx, sy)
        canvas.drawColor(Color.BLACK)
        canvas.drawBitmapMesh(photo, gx, gy, verts, 0, null, 0, paint)
        drawMouth(canvas, s)
        canvas.restore()
    }

    /** Posição final (na imagem) do ponto (x, y) da foto original. */
    fun warp(x: Float, y: Float, s: AnimState): P {
        var dx = 0f; var dy = 0f

        // ---------- boca / maxilar (referencial do rosto, origem no centro da boca) ----------
        val l = g.toLocal(P(x, y), g.mouthCenter)
        val u = l.x; val v = l.y
        val vm = v - interp(midline, u) // distância até a linha entre os lábios
        val jaw = s.open * fh * 0.068f
        if (jaw > 0f || s.smile != 0f || s.wide != 0f) {
            var du = 0f; var dv = 0f
            val wx = 1f - smoothstep(0.32f * fw, 0.6f * fw, abs(u))
            val e = max(1.5f, 0.05f * mw)
            val lower = smoothstep(-e, e, vm)
            val below = 1f - smoothstep(g.chinDist, g.chinDist + 0.45f * fh, v)
            // perto dos lábios o lábio inferior desce em arco (cantos parados); mais abaixo, o queixo desce inteiro
            // 1 no centro, 0 nos cantos internos da boca: a abertura afina até sumir nos cantos
            val corner = sqrt(max(0f, 1f - ((u - innerMid) / innerHalf).let { it * it }))
            val rigid = smoothstep(0f, g.lowerLipThickness * 1.2f + 0.06f * fh, vm)
            dv += jaw * wx * lower * below * (corner + (1f - corner) * rigid)
            // lábio de cima sobe um pouquinho
            val upMask = (1f - smoothstep(0f, 0.6f * mw, abs(u))) * (1f - lower) * (1f - smoothstep(0f, 0.3f * fh, -vm))
            dv -= 0.12f * jaw * upMask * corner
            // largura da boca (vogais/consoantes) e sorriso, concentrados em volta da boca
            val sig = 0.75f * mw
            val around = exp(-(u * u) / (sig * sig) - (v * v) / (0.5f * mw * 0.5f * mw))
            du += s.wide * 0.13f * u * around
            val cornerBump = exp(-((abs(u) - 0.5f * mw).let { it * it }) / (0.28f * mw * 0.28f * mw) - (v * v) / (0.35f * mw * 0.35f * mw))
            dv -= s.smile * 0.045f * fh * cornerBump
            du += s.smile * 0.05f * mw * Math.signum(u) * cornerBump
            val d = g.toImageVec(du, dv)
            dx += d.x; dy += d.y
        }

        // ---------- olhos e sobrancelhas ----------
        val close = (s.blink * 0.97f - s.eyesWide * 0.3f).coerceIn(-0.35f, 0.95f)
        for (eye in g.eyes) {
            val le = g.toLocal(P(x, y), eye.center)
            val a = le.x; val b = le.y
            val eh = max(2f, eye.bottom - eye.top)
            val wxe = 1f - smoothstep(0.45f * eye.width, 0.8f * eye.width, abs(a))
            if (wxe <= 0f) continue
            var db = 0f
            if (close != 0f) {
                // pálpebra: comprime o olho em direção à linha de baixo, a pele de cima acompanha
                val lid = eye.bottom
                db += when {
                    b >= lid -> 0f
                    b >= eye.top -> close * (lid - b)
                    else -> close * eh * (1f - smoothstep(eye.top - 1.5f * eh, eye.top, b)).let { 1f - it }
                }
            }
            if (s.brow != 0f) {
                val bc = eye.top - 1.4f * eh
                val wb = 1f - smoothstep(0.55f * eye.width, 1.0f * eye.width, abs(a))
                db -= s.brow * 0.45f * eh * exp(-((b - bc) / (1.1f * eh)).let { it * it }) * wb
            }
            val d = g.toImageVec(0f, db * wxe)
            dx += d.x; dy += d.y
        }

        // ---------- cabeça (gira em torno do pescoço e balança um pouco) ----------
        var px = x + dx; var py = y + dy
        if (s.roll != 0f || s.tx != 0f || s.ty != 0f) {
            val dist = hypot(x - g.faceCenter.x, y - g.faceCenter.y) / faceR
            val wh = 1f - smoothstep(0.95f, 2.0f, dist)
            if (wh > 0f) {
                val c = cos(s.roll); val sn = sin(s.roll)
                val rx = pivot.x + (px - pivot.x) * c - (py - pivot.y) * sn + s.tx * fw
                val ry = pivot.y + (px - pivot.x) * sn + (py - pivot.y) * c + s.ty * fh
                px += (rx - px) * wh; py += (ry - py) * wh
            }
        }
        return P(px, py)
    }

    private fun local(u: Float, v: Float): P {
        val d = g.toImageVec(u, v); return P(g.mouthCenter.x + d.x, g.mouthCenter.y + d.y)
    }

    /** Desenha a faixa que se abre entre os lábios: escuro, dentes (se a foto tinha a boca fechada) e língua. */
    private fun drawMouth(canvas: Canvas, s: AnimState) {
        if (s.open < 0.03f) return
        val e = max(1.5f, 0.05f * mw)
        val top = midline.map { local(it.x, it.y - e) }.map { warp(it.x, it.y, s) }
        val bot = midline.map { local(it.x, it.y + e) }.map { warp(it.x, it.y, s) }
        val tm = top[top.size / 2]; val bm = bot[bot.size / 2]
        val gap = hypot(bm.x - tm.x, bm.y - tm.y) - 2 * e
        if (gap < 1.5f) return

        fun smoothPath(pts: List<P>, p: Path, move: Boolean) {
            if (move) p.moveTo(pts[0].x, pts[0].y) else p.lineTo(pts[0].x, pts[0].y)
            for (i in 1 until pts.size) {
                val a = pts[i - 1]; val b = pts[i]
                p.quadTo(a.x, a.y, (a.x + b.x) / 2, (a.y + b.y) / 2)
            }
            p.lineTo(pts.last().x, pts.last().y)
        }
        path.reset()
        smoothPath(top, path, true)
        smoothPath(bot.reversed(), path, false)
        path.close()

        val cx = (tm.x + bm.x) / 2; val cy = (tm.y + bm.y) / 2
        mouthPaint.style = Paint.Style.FILL
        mouthPaint.shader = RadialGradient(cx, cy, max(gap, mw * 0.45f), 0xFF3B0F14.toInt(), 0xFF0E0306.toInt(), Shader.TileMode.CLAMP)
        canvas.drawPath(path, mouthPaint)

        canvas.save()
        canvas.clipPath(path)
        val len = max(1f, hypot(bm.x - tm.x, bm.y - tm.y))
        val dirX = (bm.x - tm.x) / len; val dirY = (bm.y - tm.y) / len
        if (!mouthOpenInPhoto) {
            // dentes de cima, sombreados nas pontas e com sombra do lábio
            val th = min(gap * 0.34f, mw * 0.085f)
            val teeth = Path()
            smoothPath(top, teeth, true)
            for (p in top.reversed()) teeth.lineTo(p.x + dirX * th, p.y + dirY * th)
            teeth.close()
            mouthPaint.shader = LinearGradient(top.first().x, 0f, top.last().x, 0f,
                intArrayOf(0xFF5E5650.toInt(), 0xFFD9D1C5.toInt(), 0xFFE6DFD4.toInt(), 0xFFD9D1C5.toInt(), 0xFF5E5650.toInt()),
                floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f), Shader.TileMode.CLAMP)
            canvas.drawPath(teeth, mouthPaint)
            mouthPaint.shader = LinearGradient(tm.x, tm.y, tm.x + dirX * th, tm.y + dirY * th,
                0x80000000.toInt(), 0x00000000, Shader.TileMode.CLAMP)
            canvas.drawPath(teeth, mouthPaint)
        }
        // língua, bem no fundo
        mouthPaint.shader = RadialGradient(bm.x, bm.y, mw * 0.28f, 0xD08E3440.toInt(), 0x00000000, Shader.TileMode.CLAMP)
        val tw = mw * 0.28f; val tH = gap * 0.35f
        canvas.drawOval(bm.x - tw, bm.y - tH, bm.x + tw, bm.y + tH * 0.6f, mouthPaint)
        // sombra junto ao lábio de baixo
        mouthPaint.shader = LinearGradient(bm.x, bm.y, bm.x - dirX * gap * 0.3f, bm.y - dirY * gap * 0.3f,
            0x90000000.toInt(), 0x00000000, Shader.TileMode.CLAMP)
        canvas.drawPath(path, mouthPaint)
        canvas.restore()
        mouthPaint.shader = null
    }
}
