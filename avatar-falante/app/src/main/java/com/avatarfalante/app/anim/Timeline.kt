package com.avatarfalante.app.anim

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Transforma o áudio em movimento: abertura e formato da boca a partir do volume (RMS)
 * e da "aspereza" (taxa de cruzamento por zero, que separa vogais de s/f/x/i),
 * mais piscadas e movimentos de cabeça naturais.
 */
class Timeline private constructor(
    private val open: FloatArray,
    private val wide: FloatArray,
    private val energy: FloatArray,
    val durationSec: Float,
    private val seed: Int,
) {
    private val blinks: FloatArray = makeBlinks(durationSec + 600f, seed)

    fun state(tSec: Float, expr: Expression): AnimState {
        // a boca adianta ~40 ms em relação ao som (fica mais natural)
        val f = (tSec + LEAD) * FPS
        val o = sample(open, f)
        val w = sample(wide, f)
        val e = sample(energy, f)
        val t = tSec
        val talk = min(1f, e * 1.4f)
        val roll = 0.022f * sin(2 * PI * 0.21 * t + seed).toFloat() + 0.010f * sin(2 * PI * 0.57 * t).toFloat() +
            0.012f * talk * sin(2 * PI * 1.3 * t).toFloat()
        val tx = 0.010f * sin(2 * PI * 0.17 * t + 1.3).toFloat() + 0.006f * talk * sin(2 * PI * 0.9 * t).toFloat()
        val ty = 0.007f * sin(2 * PI * 0.29 * t + 0.4).toFloat() + 0.012f * talk * sin(2 * PI * 2.1 * t).toFloat().coerceAtLeast(0f)
        return AnimState(
            open = o, wide = w, blink = blinkAt(t),
            smile = expr.smile * (1f - 0.5f * o), brow = expr.brow + 0.25f * talk * sin(2 * PI * 0.8 * t).toFloat().coerceAtLeast(0f),
            eyesWide = expr.eyesWide, roll = roll, tx = tx, ty = ty,
        )
    }

    private fun blinkAt(t: Float): Float {
        for (b in blinks) {
            if (b > t + 0.2f) break
            val d = t - b
            if (d in 0f..BLINK_DUR) {
                val x = d / BLINK_DUR // fecha rápido, abre um pouco mais devagar
                return if (x < 0.4f) (x / 0.4f) else (1f - (x - 0.4f) / 0.6f).pow(1.5f)
            }
        }
        return 0f
    }

    private fun sample(a: FloatArray, f: Float): Float {
        if (a.isEmpty() || f < 0f) return 0f
        val i = f.toInt()
        if (i >= a.size - 1) return if (i == a.size - 1) a[i] else 0f
        val fr = f - i
        return a[i] * (1 - fr) + a[i + 1] * fr
    }

    companion object {
        const val FPS = 60
        private const val LEAD = 0.04f
        private const val BLINK_DUR = 0.18f

        /** Sem fala (prévia em repouso). */
        fun idle(seed: Int = 7) = Timeline(FloatArray(0), FloatArray(0), FloatArray(0), 0f, seed)

        /** [pcm] mono 16 bits. */
        fun fromPcm(pcm: ShortArray, sampleRate: Int, seed: Int = 7): Timeline {
            val hop = max(1, sampleRate / FPS)
            val n = pcm.size / hop + 1
            val rms = FloatArray(n)
            val zcr = FloatArray(n)
            for (i in 0 until n) {
                val s = i * hop
                val e = min(pcm.size, s + hop * 2) // janela de 2 passos
                if (e - s < 2) continue
                var sum = 0.0; var z = 0
                for (j in s until e) {
                    val v = pcm[j] / 32768.0
                    sum += v * v
                    if (j > s && (pcm[j] >= 0) != (pcm[j - 1] >= 0)) z++
                }
                rms[i] = sqrt(sum / (e - s)).toFloat()
                zcr[i] = z.toFloat() / (e - s)
            }
            val sorted = rms.sorted()
            val p95 = max(1e-4f, sorted[(sorted.size * 0.95f).toInt().coerceIn(0, sorted.size - 1)])
            val floor = sorted[(sorted.size * 0.15f).toInt().coerceIn(0, sorted.size - 1)] * 1.6f + 0.004f

            val open = FloatArray(n)
            val wide = FloatArray(n)
            val energy = FloatArray(n)
            val lvls = FloatArray(n) { ((rms[it] - floor) / (p95 - floor)).coerceIn(0f, 1.2f) }
            // ritmo das sílabas: desconta o "chão" local (mínimo em ~±80 ms), senão a boca
            // fica aberta direto em fala contínua
            val win = max(1, FPS * 8 / 100)
            var o = 0f; var w = 0f
            for (i in 0 until n) {
                val lvl = lvls[i]
                var localMin = lvl
                for (k in max(0, i - win)..min(n - 1, i + win)) localMin = min(localMin, lvls[k])
                val syl = ((lvl - 0.65f * localMin) / (1f - 0.65f * min(localMin, 0.9f))).coerceIn(0f, 1.2f)
                val target = (syl * 0.95f).coerceAtMost(1f)
                // abre rápido, fecha um pouco mais devagar
                o += (target - o) * (if (target > o) 0.55f else 0.32f)
                // ZCR alto = consoantes sibilantes/"i" (boca larga e pouco aberta); baixo = "o/u"
                val zn = ((zcr[i] - 0.02f) / 0.12f).coerceIn(0f, 1f)
                val wt = if (lvl > 0.05f) (zn * 2f - 1f) else 0f
                w += (wt - w) * 0.25f
                open[i] = o * (1f - 0.35f * max(0f, w))
                wide[i] = w
                energy[i] = lvl.coerceAtMost(1f)
            }
            // suaviza a energia (para o movimento de cabeça)
            val en2 = FloatArray(n)
            var acc = 0f
            for (i in 0 until n) { acc += (energy[i] - acc) * 0.05f; en2[i] = acc }
            return Timeline(open, wide, en2, n.toFloat() / FPS, seed)
        }

        private fun makeBlinks(maxT: Float, seed: Int): FloatArray {
            val out = ArrayList<Float>()
            var t = 0.8f
            var r = seed * 1103515245 + 12345
            while (t < maxT) {
                out += t
                r = r * 1103515245 + 12345
                val u = abs((r ushr 8) % 1000) / 1000f
                t += 2.0f + 3.2f * u
                if (u < 0.12f) { out += t - 1.6f + 0.35f } // às vezes uma piscada dupla
            }
            return out.sorted().toFloatArray()
        }
    }
}
