package com.tracomagico.app.filters

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Operações de imagem em IntArray (rápidas e sem dependências). */
internal object Ops {

    fun lum(px: IntArray): IntArray = IntArray(px.size) { i ->
        val c = px[i]
        (77 * (c shr 16 and 0xFF) + 150 * (c shr 8 and 0xFF) + 29 * (c and 0xFF)) shr 8
    }

    fun clamp(v: Int): Int = if (v < 0) 0 else if (v > 255) 255 else v

    fun rgb(r: Int, g: Int, b: Int): Int = (0xFF shl 24) or (clamp(r) shl 16) or (clamp(g) shl 8) or clamp(b)

    /** Mistura [a] -> [b] com t em 0..255 (0 = a, 255 = b). */
    fun mix(a: Int, b: Int, t: Int): Int {
        val ar = a shr 16 and 0xFF; val ag = a shr 8 and 0xFF; val ab = a and 0xFF
        val br = b shr 16 and 0xFF; val bg = b shr 8 and 0xFF; val bb = b and 0xFF
        return rgb(ar + (br - ar) * t / 255, ag + (bg - ag) * t / 255, ab + (bb - ab) * t / 255)
    }

    /** Ruído determinístico 0..255 (textura de papel, granulado, pontilhado). */
    fun noise(x: Int, y: Int): Int {
        var h = x * 374761393 + y * 668265263
        h = (h xor (h ushr 13)) * 1274126177
        return (h xor (h ushr 16)) and 0xFF
    }

    private fun boxH(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        val div = 2 * r + 1
        for (y in 0 until h) {
            val row = y * w
            var sum = 0
            for (k in -r..r) sum += src[row + min(max(k, 0), w - 1)]
            for (x in 0 until w) {
                dst[row + x] = sum / div
                sum += src[row + min(x + r + 1, w - 1)] - src[row + max(x - r, 0)]
            }
        }
    }

    private fun boxV(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        val div = 2 * r + 1
        for (x in 0 until w) {
            var sum = 0
            for (k in -r..r) sum += src[min(max(k, 0), h - 1) * w + x]
            for (y in 0 until h) {
                dst[y * w + x] = sum / div
                sum += src[min(y + r + 1, h - 1) * w + x] - src[max(y - r, 0) * w + x]
            }
        }
    }

    fun box(src: IntArray, w: Int, h: Int, r: Int): IntArray {
        if (r <= 0) return src.copyOf()
        val tmp = IntArray(src.size)
        val out = IntArray(src.size)
        boxH(src, tmp, w, h, r)
        boxV(tmp, out, w, h, r)
        return out
    }

    /** Aproximação de desfoque gaussiano com 3 passadas de box blur. */
    fun gaussian(src: IntArray, w: Int, h: Int, r: Int): IntArray {
        if (r <= 0) return src.copyOf()
        val rr = max(1, (r + 1) / 2)
        return box(box(box(src, w, h, rr), w, h, rr), w, h, rr)
    }

    fun split(px: IntArray): Array<IntArray> {
        val r = IntArray(px.size); val g = IntArray(px.size); val b = IntArray(px.size)
        for (i in px.indices) {
            val c = px[i]; r[i] = c shr 16 and 0xFF; g[i] = c shr 8 and 0xFF; b[i] = c and 0xFF
        }
        return arrayOf(r, g, b)
    }

    fun join(ch: Array<IntArray>): IntArray = IntArray(ch[0].size) { i -> rgb(ch[0][i], ch[1][i], ch[2][i]) }

    fun blurRgb(px: IntArray, w: Int, h: Int, r: Int): IntArray {
        val ch = split(px)
        return join(Array(3) { gaussian(ch[it], w, h, r) })
    }

    /** Magnitude do gradiente de Sobel (0..255). */
    fun sobel(g: IntArray, w: Int, h: Int): IntArray {
        val out = IntArray(g.size)
        for (y in 0 until h) {
            val y0 = max(y - 1, 0) * w; val y1 = y * w; val y2 = min(y + 1, h - 1) * w
            for (x in 0 until w) {
                val x0 = max(x - 1, 0); val x2 = min(x + 1, w - 1)
                val tl = g[y0 + x0]; val tc = g[y0 + x]; val tr = g[y0 + x2]
                val ml = g[y1 + x0]; val mr = g[y1 + x2]
                val bl = g[y2 + x0]; val bc = g[y2 + x]; val br = g[y2 + x2]
                val gx = (tr + 2 * mr + br) - (tl + 2 * ml + bl)
                val gy = (bl + 2 * bc + br) - (tl + 2 * tc + tr)
                out[y1 + x] = clamp((abs(gx) + abs(gy)) / 2)
            }
        }
        return out
    }

    /** "Color dodge": base / (1 - blend). Coração do efeito de lápis. */
    fun dodge(base: Int, blend: Int): Int = if (blend >= 255) 255 else min(255, base * 255 / (255 - blend))

    fun gammaLut(gamma: Float): IntArray =
        IntArray(256) { clamp((255.0 * Math.pow(it / 255.0, gamma.toDouble())).toInt()) }

    fun saturate(c: Int, amount: Float): Int {
        val r = c shr 16 and 0xFF; val g = c shr 8 and 0xFF; val b = c and 0xFF
        val l = (77 * r + 150 * g + 29 * b) shr 8
        return rgb(
            (l + (r - l) * amount).toInt(),
            (l + (g - l) * amount).toInt(),
            (l + (b - l) * amount).toInt(),
        )
    }

    fun posterize(v: Int, levels: Int): Int {
        val step = 255f / (levels - 1)
        return clamp((Math.round(v / step) * step).toInt())
    }

    /** Valor (0..255) abaixo do qual está a fração [q] dos pixels. */
    fun percentile(v: IntArray, q: Float): Int {
        val hist = IntArray(256)
        for (x in v) hist[x]++
        val target = (v.size * q.coerceIn(0f, 1f)).toLong()
        var acc = 0L
        for (i in 0..255) { acc += hist[i]; if (acc >= target) return i }
        return 255
    }
}
