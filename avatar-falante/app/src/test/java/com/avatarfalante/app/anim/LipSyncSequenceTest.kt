package com.avatarfalante.app.anim

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Gera a sequência de quadros (30 qps) de uma fala real, para montar um vídeo de conferência:
 * -Pface=<pasta com portrait.jpg, landmarks.json e fala.wav> -Pout=<pasta>
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LipSyncSequenceTest {

    private fun readWav(f: File): Pair<ShortArray, Int> {
        val b = ByteBuffer.wrap(f.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        var rate = 0; var ch = 1
        b.position(12)
        while (b.remaining() > 8) {
            val id = ByteArray(4).also { b.get(it) }.toString(Charsets.US_ASCII)
            val len = b.int
            if (id == "fmt ") { b.short; ch = b.short.toInt(); rate = b.int; b.position(b.position() + len - 8) }
            else if (id == "data") {
                val n = minOf(len, b.remaining()) / 2
                val s = ShortArray(n); b.asShortBuffer().get(s)
                val mono = if (ch == 1) s else ShortArray(n / ch) { s[it * ch] }
                return mono to rate
            } else b.position(b.position() + len)
        }
        error("wav inválido")
    }

    @Test
    fun sequencia() {
        val dir = System.getProperty("face") ?: return
        val wav = File(dir, "fala.wav").takeIf { it.exists() } ?: return
        val out = File(System.getProperty("out") ?: return)
        val (pcm, rate) = readWav(wav)
        val tl = Timeline.fromPcm(pcm, rate)
        val frames = (tl.durationSec * 30).toInt() + 15
        val opens = (0 until frames).map { tl.state(it / 30f, Expression.NEUTRAL).open }
        val sorted = opens.sorted()
        println("ABERTURA p10=%.2f p50=%.2f p90=%.2f  quadros<0.2: %d%%".format(
            sorted[sorted.size / 10], sorted[sorted.size / 2], sorted[sorted.size * 9 / 10], opens.count { it < 0.2f } * 100 / opens.size))
        println("SEQ " + opens.subList(30, 90).joinToString(" ") { "%.1f".format(it) })

        val photo = BitmapFactory.decodeFile("$dir/portrait.jpg").copy(Bitmap.Config.ARGB_8888, false)
        val j = JSONObject(File("$dir/landmarks.json").readText())
        fun pts(k: String) = j.getJSONArray(k).let { a -> (0 until a.length()).map { val p = a.getJSONArray(it); P(p.getDouble(0).toFloat(), p.getDouble(1).toFloat()) } }
        val g = FaceGeom(pts("oval"), pts("upperLipOuter"), pts("upperLipInner"), pts("lowerLipInner"), pts("lowerLipOuter"),
            pts("eyeA"), pts("eyeB"), pts("browA"), pts("browB"))
        val renderers = listOf(
            "seq_photo" to Triple(PhotoAvatar(photo, g), 410, 512),
            "seq_cartoon" to Triple(CartoonAvatar(CartoonLook(hairStyle = HairStyle.LONG, shirt = 0xFFDB2777.toInt())), 360, 640),
        )
        for ((name, rr) in renderers) {
            val (r, w, h) = rr
            val d = File(out, name).apply { mkdirs() }
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            for (i in 0 until frames) {
                r.draw(c, w, h, tl.state(i / 30f, Expression.HAPPY))
                File(d, "%04d.png".format(i)).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }
}
