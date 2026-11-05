package com.tracomagico.app.filters

import android.graphics.Bitmap
import com.tracomagico.app.filters.Ops.clamp
import com.tracomagico.app.filters.Ops.mix
import com.tracomagico.app.filters.Ops.noise
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Transforma uma foto em desenho. Todos os estilos são algoritmos clássicos de
 * processamento de imagem (dodge, Sobel, limiar adaptativo, posterização, hachuras...).
 *
 * [intensity] vai de 0 a 1 (0,5 = padrão do estilo).
 */
object FilterEngine {

    fun apply(src: Bitmap, style: Style, intensity: Float): Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val out = render(px, w, h, style, intensity.coerceIn(0f, 1f))
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }

    /** Mistura o desenho com a foto original (0 = só desenho, 1 = só foto). */
    fun blend(drawing: Bitmap, photo: Bitmap, amount: Float): Bitmap {
        if (amount <= 0.001f) return drawing
        val w = drawing.width; val h = drawing.height
        val a = IntArray(w * h); val b = IntArray(w * h)
        drawing.getPixels(a, 0, w, 0, 0, w, h)
        val p = if (photo.width == w && photo.height == h) photo else Bitmap.createScaledBitmap(photo, w, h, true)
        p.getPixels(b, 0, w, 0, 0, w, h)
        val t = (amount * 255).roundToInt()
        for (i in a.indices) a[i] = mix(a[i], b[i], t)
        return Bitmap.createBitmap(a, w, h, Bitmap.Config.ARGB_8888)
    }

    internal fun render(px: IntArray, w: Int, h: Int, st: Style, k: Float): IntArray {
        val s = max(w, h) / 1000f
        fun sc(r: Float) = max(1, (r * s).roundToInt())
        return when (st.kind) {
            Kind.PENCIL -> pencil(px, w, h, st, k, sc(st.radius), s)
            Kind.CHARCOAL -> charcoal(px, w, h, st, k, sc(st.radius), s)
            Kind.COLOR_PENCIL -> colorPencil(px, w, h, st, k, sc(st.radius), s)
            Kind.INK -> ink(px, w, h, st, k, s)
            Kind.STENCIL -> stencil(px, w, h, st, k, s)
            Kind.TONES -> tones(px, w, h, st, k, s)
            Kind.CARTOON -> cartoon(px, w, h, st, k, s)
            Kind.MANGA -> manga(px, w, h, st, k, s)
            Kind.HATCH -> hatch(px, w, h, st, k, s)
            Kind.ENGRAVING -> engraving(px, w, h, st, k, s)
            Kind.STIPPLE -> stipple(px, w, h, st, k, s)
            Kind.HALFTONE -> halftone(px, w, h, st, k, s)
            Kind.WATERCOLOR -> watercolor(px, w, h, st, k, s)
        }
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    /** Desenho a lápis: cinza "dodge" com o negativo desfocado. Retorna valores 0..255. */
    private fun sketchValues(g: IntArray, w: Int, h: Int, r: Int, gamma: Float): IntArray {
        val inv = IntArray(g.size) { 255 - g[it] }
        val b = Ops.gaussian(inv, w, h, r)
        val lut = Ops.gammaLut(gamma)
        return IntArray(g.size) { lut[Ops.dodge(g[it], b[it])] }
    }

    private fun grainy(v: Int, x: Int, y: Int, grain: Float, s: Float): Int {
        if (grain <= 0f) return v
        val gs = max(1, (1.5f * s).roundToInt())
        val n = noise(x / gs, y / gs) - 128
        // granulado mais forte no traço do que no papel, e uma textura leve no papel
        return clamp(v + (n * grain * (255 - v) / 255f).toInt() - ((n + 128) and 7) * (grain * 1.2f).toInt())
    }

    private fun pencil(px: IntArray, w: Int, h: Int, st: Style, k: Float, r: Int, s: Float): IntArray {
        val g = Ops.lum(px)
        val v = sketchValues(g, w, h, r, lerp(0.8f, 3.2f, k) * st.p1)
        val out = IntArray(px.size)
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            out[i] = mix(st.ink, st.paper, grainy(v[i], x, y, st.grain, s))
        }
        return out
    }

    private fun charcoal(px: IntArray, w: Int, h: Int, st: Style, k: Float, r: Int, s: Float): IntArray {
        val g = Ops.lum(px)
        var v = sketchValues(g, w, h, r, lerp(1.6f, 4f, k) * st.p1)
        // massa de sombra: escurece com o tom da foto
        val tone = Ops.gaussian(g, w, h, max(1, (3 * s).roundToInt()))
        val dark = lerp(0.35f, 0.8f, k)
        v = IntArray(v.size) { (v[it] * (255 - (255 - tone[it]) * dark) / 255f).toInt() }
        if (st.p2 > 0f) v = Ops.gaussian(v, w, h, max(1, (st.p2 * s).roundToInt()))
        val out = IntArray(px.size)
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            out[i] = mix(st.ink, st.paper, grainy(v[i], x, y, st.grain, s))
        }
        return out
    }

    private fun colorPencil(px: IntArray, w: Int, h: Int, st: Style, k: Float, r: Int, s: Float): IntArray {
        val g = Ops.lum(px)
        val v = sketchValues(g, w, h, r, lerp(0.7f, 2.6f, k))
        val color = if (st.p3 > 0f) Ops.blurRgb(px, w, h, max(1, (st.p3 * s).roundToInt())) else px
        val amount = st.p2
        val out = IntArray(px.size)
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            // cor clareada (parece pigmento sobre papel), multiplicada pelo traço
            val c = Ops.saturate(color[i], st.p1)
            val tinted = mix(st.paper, c, (amount * 255).toInt())
            val vv = grainy(v[i], x, y, st.grain, s)
            out[i] = Ops.rgb(
                (tinted shr 16 and 0xFF) * vv / 255,
                (tinted shr 8 and 0xFF) * vv / 255,
                (tinted and 0xFF) * vv / 255,
            )
        }
        return out
    }

    /**
     * Linhas (0 = sem linha, 255 = linha forte) a partir das bordas da imagem.
     * O limiar é um percentil da própria imagem, então funciona em fotos claras ou escuras:
     * [lo]/[hi] = fração de pixels que vira linha com intensidade 0 e 1.
     */
    private fun lines(px: IntArray, w: Int, h: Int, pre: Float, thick: Float, k: Float, s: Float, lo: Float = 0.04f, hi: Float = 0.16f): IntArray {
        val g = Ops.gaussian(Ops.lum(px), w, h, max(1, (pre * s).roundToInt()))
        var e = Ops.sobel(g, w, h)
        if (thick > 0f) {
            val tr = max(1, (thick * s).roundToInt())
            e = Ops.box(e, w, h, tr)
        }
        val frac = lerp(lo, hi, k)
        val t = Ops.percentile(e, 1f - frac).coerceAtLeast(8)
        // os ~40% mais fortes dessas bordas já saem com tinta cheia
        val top = max(t + 12, Ops.percentile(e, 1f - frac * 0.4f))
        val range = (top - t).toFloat()
        return IntArray(e.size) { clamp(((e[it] - t) * 255f / range).toInt()) }
    }

    private fun ink(px: IntArray, w: Int, h: Int, st: Style, k: Float, s: Float): IntArray {
        val line = lines(px, w, h, st.p2, st.radius, k, s)
        val out = IntArray(px.size)
        if (st.p1 > 1f) { // neon: brilho em volta das linhas
            val glow = Ops.gaussian(line, w, h, max(2, (6 * s).roundToInt()))
            for (i in out.indices) out[i] = mix(st.paper, st.ink, clamp(line[i] + glow[i] * 3 / 4))
        } else {
            for (i in out.indices) out[i] = mix(st.ink, st.paper, 255 - line[i])
        }
        return out
    }

    private fun stencil(px: IntArray, w: Int, h: Int, st: Style, k: Float, s: Float): IntArray {
        val g = Ops.gaussian(Ops.lum(px), w, h, max(1, (1.5f * s).roundToInt()))
        val out = IntArray(px.size)
        if (st.radius > 0f) {
            // limiar adaptativo: preto onde o pixel é mais escuro que a vizinhança
            val mean = Ops.box(Ops.box(g, w, h, max(2, (st.radius * s).roundToInt())), w, h, max(2, (st.radius * s).roundToInt()))
            val c = lerp(18f, -6f, k)
            for (i in out.indices) out[i] = if (g[i] < mean[i] - c) st.ink else st.paper
        } else {
            // fração da imagem que fica preta
            val t = Ops.percentile(g, lerp(0.6f, 1.4f, k) * st.p1)
            for (i in out.indices) out[i] = if (g[i] <= t) st.ink else st.paper
        }
        return out
    }

    private fun tones(px: IntArray, w: Int, h: Int, st: Style, k: Float, s: Float): IntArray {
        val g = Ops.gaussian(Ops.lum(px), w, h, max(1, (2f * s).roundToInt()))
        val n = st.p1.toInt()
        val q = lerp(-0.15f, 0.15f, k)
        val t1 = Ops.percentile(g, (if (n <= 2) 0.5f else 0.33f) + q)
        val t2 = Ops.percentile(g, 0.68f + q)
        val out = IntArray(px.size)
        for (i in out.indices) {
            val v = g[i]
            out[i] = if (n <= 2) {
                if (v < t1) st.ink else st.paper
            } else when {
                v < t1 -> st.ink
                v < t2 -> st.mid
                else -> st.paper
            }
        }
        return out
    }

    private fun cartoon(px: IntArray, w: Int, h: Int, st: Style, k: Float, s: Float): IntArray {
        val r = max(1, (st.radius * s).roundToInt())
        val smooth = Ops.blurRgb(Ops.blurRgb(px, w, h, r), w, h, r)
        val levels = st.p1.toInt().coerceAtLeast(2)
        val line = if (st.p3 > 0f) lines(px, w, h, 2f, st.p3 - 1f, k, s, lo = 0.03f, hi = 0.12f) else null
        val out = IntArray(px.size)
        for (i in out.indices) {
            val c = Ops.saturate(smooth[i], st.p2)
            val col = Ops.rgb(
                Ops.posterize(c shr 16 and 0xFF, levels),
                Ops.posterize(c shr 8 and 0xFF, levels),
                Ops.posterize(c and 0xFF, levels),
            )
            out[i] = if (line != null) mix(col, 0xFF111111.toInt(), line[i]) else col
        }
        return out
    }

    private fun manga(px: IntArray, w: Int, h: Int, st: Style, k: Float, s: Float): IntArray {
        val g = Ops.gaussian(Ops.lum(px), w, h, max(1, (st.radius * s).roundToInt()))
        val line = lines(px, w, h, 1.5f, 0f, k, s)
        val shift = lerp(-40f, 40f, k)
        val cell = max(3, (5 * s).roundToInt())
        val out = IntArray(px.size)
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            val v = g[i] - shift
            val base = when {
                v > 165 -> 255
                v > 85 -> { // retícula de pontos (screentone)
                    val cx = x % cell - cell / 2; val cy = (y + if ((x / cell) % 2 == 0) 0 else cell / 2) % cell - cell / 2
                    if (cx * cx + cy * cy <= (cell * cell) / 7) 30 else 255
                }
                else -> 20
            }
            out[i] = Ops.rgb(base, base, base).let { mix(it, 0xFF000000.toInt(), line[i]) }
        }
        return out
    }

    private fun hatch(px: IntArray, w: Int, h: Int, st: Style, k: Float, s: Float): IntArray {
        val tone = Ops.gaussian(Ops.lum(px), w, h, max(1, (2.5f * s).roundToInt()))
        val line = lines(px, w, h, 1.5f, 0f, 0.4f, s)
        val sp = max(3, (st.radius * s).roundToInt())
        val lw = max(1, sp / 3)
        val shift = lerp(-50f, 50f, k)
        val cross = st.p1 > 0f
        val out = IntArray(px.size)
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            val v = tone[i] - shift
            var ink = false
            if (v < 210 && (x + y) % sp < lw) ink = true
            if (v < 140 && (if (cross) (x - y + 100000 * sp) % sp < lw else (x + y + sp / 2) % sp < lw)) ink = true
            if (v < 75 && (if (cross) y % sp < lw else (x - y + 100000 * sp) % sp < lw)) ink = true
            if (v < 35 && x % sp < lw) ink = true
            val base = if (ink) 0xFF202020.toInt() else 0xFFFDFCF8.toInt()
            out[i] = mix(base, 0xFF101010.toInt(), line[i] / 2)
        }
        return out
    }

    private fun engraving(px: IntArray, w: Int, h: Int, st: Style, k: Float, s: Float): IntArray {
        val tone = Ops.gaussian(Ops.lum(px), w, h, max(1, (2f * s).roundToInt()))
        val sp = max(3, (st.radius * s).roundToInt())
        val gamma = lerp(1.6f, 0.6f, k)
        val out = IntArray(px.size)
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            val dark = Math.pow(1.0 - tone[i] / 255.0, gamma.toDouble())
            // linhas onduladas cuja espessura acompanha o escuro da foto
            val wave = (y + (sp * 0.35 * Math.sin(x * 0.02 / s)).toInt()) % sp
            val pos = if (wave < 0) wave + sp else wave
            val thickness = dark * sp
            out[i] = if (pos < thickness) 0xFF1B1B22.toInt() else 0xFFF8F5EC.toInt()
        }
        return out
    }

    private fun stipple(px: IntArray, w: Int, h: Int, st: Style, k: Float, s: Float): IntArray {
        val tone = Ops.gaussian(Ops.lum(px), w, h, max(1, (1.5f * s).roundToInt()))
        val gamma = lerp(1.8f, 0.7f, k)
        val lut = Ops.gammaLut(gamma)
        val ds = max(1, (1.2f * s).roundToInt()) // tamanho do ponto
        val out = IntArray(px.size)
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            val n = noise(x / ds, y / ds)
            out[i] = if (lut[tone[i]] < n) st.ink else st.paper
        }
        return out
    }

    private fun halftone(px: IntArray, w: Int, h: Int, st: Style, k: Float, s: Float): IntArray {
        val cell = max(4, (st.radius * s).roundToInt())
        val tone = Ops.box(Ops.lum(px), w, h, cell / 2)
        val boost = lerp(0.8f, 1.35f, k)
        val out = IntArray(px.size)
        for (y in 0 until h) for (x in 0 until w) {
            val cx = (x / cell) * cell + cell / 2
            val cy = (y / cell) * cell + cell / 2
            val t = tone[minOf(cy, h - 1) * w + minOf(cx, w - 1)]
            val rad = cell / 2f * sqrt((255 - t) / 255f) * 1.4f * boost
            val dx = x - cx; val dy = y - cy
            out[y * w + x] = if (dx * dx + dy * dy <= rad * rad) st.ink else st.paper
        }
        return out
    }

    private fun watercolor(px: IntArray, w: Int, h: Int, st: Style, k: Float, s: Float): IntArray {
        val r = max(1, (st.radius * s).roundToInt())
        val smooth = Ops.blurRgb(px, w, h, r)
        val levels = st.p1.toInt()
        val edge = lines(px, w, h, 2f, 0f, k, s, lo = 0.03f, hi = 0.12f)
        val edgeAmt = lerp(0.2f, 0.7f, k) * (if (st.p2 > 0f) st.p2 else 1f)
        val out = IntArray(px.size)
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            val c = Ops.saturate(smooth[i], 1.15f)
            val n = (noise(x / 2, y / 2) - 128) / 12 // textura do papel
            fun ch(v: Int) = clamp(255 - ((255 - Ops.posterize(v, levels)) * 0.85f).toInt() + n)
            val col = Ops.rgb(ch(c shr 16 and 0xFF), ch(c shr 8 and 0xFF), ch(c and 0xFF))
            out[i] = mix(col, 0xFF3A3A44.toInt(), (edge[i] * edgeAmt).toInt())
        }
        return out
    }
}
