package com.tracomagico.app.filters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.DataInputStream
import java.io.File

/**
 * Roda todos os estilos (no JVM, sem aparelho). Com -Psample=<foto.ppm> e -Pout=<pasta>
 * também grava os resultados (PPM) para conferência visual.
 */
class StylesRenderTest {

    private fun synthetic(w: Int, h: Int) = IntArray(w * h) { i ->
        val x = i % w; val y = i / w
        val chk = (x / 20 + y / 20) % 2 == 0
        val r = if (chk) 30 else x * 255 / w; val g = if (chk) 30 else y * 255 / h; val b = if (chk) 40 else 220
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    @Test
    fun todosOsEstilosGeramImagemValida() {
        val w = 160; val h = 120
        val px = synthetic(w, h)
        assertTrue("esperado 50+ estilos", Styles.all.size >= 50)
        assertEquals("ids únicos", Styles.all.size, Styles.all.map { it.id }.toSet().size)
        for (st in Styles.all) for (k in floatArrayOf(0f, 0.5f, 1f)) {
            val out = FilterEngine.render(px, w, h, st, k)
            assertEquals(st.id, px.size, out.size)
            assertTrue("${st.id} sem alfa total", out.all { it ushr 24 == 0xFF })
            assertTrue("${st.id} imagem lisa (k=$k)", out.toSet().size > 1)
        }
    }

    @Test
    fun exportarAmostras() {
        val sample = System.getProperty("sample") ?: return
        val outDir = File(System.getProperty("out") ?: return).apply { mkdirs() }
        val (w, h, px) = readPpm(File(sample))
        val t0 = System.currentTimeMillis()
        for (st in Styles.all) {
            val out = FilterEngine.render(px, w, h, st, 0.5f)
            writePpm(File(outDir, "${st.category.ordinal}_${st.id}.ppm"), w, h, out)
        }
        println("Tempo total: ${System.currentTimeMillis() - t0} ms para ${Styles.all.size} estilos (${w}x$h)")
    }

    private fun readPpm(f: File): Triple<Int, Int, IntArray> {
        DataInputStream(f.inputStream().buffered()).use { inp ->
            fun token(): String {
                val sb = StringBuilder()
                while (true) {
                    val c = inp.read()
                    if (c == '#'.code) { while (inp.read() != '\n'.code) Unit; continue }
                    if (c <= ' '.code) { if (sb.isNotEmpty()) return sb.toString() else continue }
                    sb.append(c.toChar())
                }
            }
            check(token() == "P6")
            val w = token().toInt(); val h = token().toInt(); token()
            val bytes = ByteArray(w * h * 3); inp.readFully(bytes)
            val px = IntArray(w * h) { i ->
                (0xFF shl 24) or ((bytes[i * 3].toInt() and 0xFF) shl 16) or
                    ((bytes[i * 3 + 1].toInt() and 0xFF) shl 8) or (bytes[i * 3 + 2].toInt() and 0xFF)
            }
            return Triple(w, h, px)
        }
    }

    private fun writePpm(f: File, w: Int, h: Int, px: IntArray) {
        f.outputStream().buffered().use { o ->
            o.write("P6\n$w $h\n255\n".toByteArray())
            for (c in px) { o.write(c shr 16 and 0xFF); o.write(c shr 8 and 0xFF); o.write(c and 0xFF) }
        }
    }
}
