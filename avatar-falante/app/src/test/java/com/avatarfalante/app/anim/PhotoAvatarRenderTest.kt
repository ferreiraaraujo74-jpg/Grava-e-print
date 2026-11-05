package com.avatarfalante.app.anim

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renderiza a animação de uma foto real (Canvas nativo do Android via Robolectric).
 * Com -Pface=<pasta com portrait.jpg + landmarks.json> e -Pout=<pasta>, grava quadros PNG.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoAvatarRenderTest {

    private fun pts(o: JSONObject, k: String): List<P> {
        val a = o.optJSONArray(k) ?: return emptyList()
        return (0 until a.length()).map { val p = a.getJSONArray(it); P(p.getDouble(0).toFloat(), p.getDouble(1).toFloat()) }
    }

    @Test
    fun renderizarQuadros() {
        val dir = System.getProperty("face") ?: return
        val out = File(System.getProperty("out") ?: return).apply { mkdirs() }
        val photo = BitmapFactory.decodeFile("$dir/portrait.jpg").copy(Bitmap.Config.ARGB_8888, false)
        val j = JSONObject(File("$dir/landmarks.json").readText())
        val g = FaceGeom(
            pts(j, "oval"), pts(j, "upperLipOuter"), pts(j, "upperLipInner"), pts(j, "lowerLipInner"),
            pts(j, "lowerLipOuter"), pts(j, "eyeA"), pts(j, "eyeB"), pts(j, "browA"), pts(j, "browB"),
        )
        assertTrue(g.isPlausible())
        val r = PhotoAvatar(photo, g)
        val states = linkedMapOf(
            "0_repouso" to AnimState(),
            "1_aberta_meio" to AnimState(open = 0.5f),
            "2_aberta_total" to AnimState(open = 1f),
            "3_o" to AnimState(open = 0.7f, wide = -1f),
            "4_i" to AnimState(open = 0.3f, wide = 1f),
            "5_piscada" to AnimState(blink = 1f),
            "6_feliz_falando" to AnimState(open = 0.6f, smile = 0.8f, brow = 0.2f),
            "7_surpreso" to AnimState(open = 0.8f, brow = 1f, eyesWide = 0.25f),
            "8_cabeca" to AnimState(open = 0.4f, roll = 0.04f, tx = 0.02f, ty = 0.015f),
            "9_bravo" to AnimState(open = 0.3f, smile = -0.3f, brow = -0.8f),
        )
        for ((name, s) in states) {
            val bmp = Bitmap.createBitmap(photo.width, photo.height, Bitmap.Config.ARGB_8888)
            r.draw(Canvas(bmp), bmp.width, bmp.height, s)
            File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        // timeline com áudio sintético: sílabas a 4 Hz
        val sr = 16000
        val pcm = ShortArray(sr * 2) { i ->
            val t = i / sr.toDouble()
            val env = Math.max(0.0, Math.sin(2 * Math.PI * 4 * t))
            (Math.sin(2 * Math.PI * 220 * t) * env * 12000).toInt().toShort()
        }
        val tl = Timeline.fromPcm(pcm, sr)
        val opens = (0 until 120).map { tl.state(it / 60f, Expression.NEUTRAL).open }
        assertTrue("boca deve abrir e fechar", opens.max() > 0.6f && opens.min() < 0.2f)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CartoonAvatarRenderTest {
    @Test
    fun renderizarPersonagens() {
        val out = File(System.getProperty("out") ?: return, "cartoon").apply { mkdirs() }
        val looks = HairStyle.entries.mapIndexed { i, hs ->
            CartoonLook(
                skin = CartoonLook.skins[i % CartoonLook.skins.size], hair = CartoonLook.hairs[i % CartoonLook.hairs.size],
                hairStyle = hs, shirt = CartoonLook.shirts[i % CartoonLook.shirts.size],
                background = CartoonLook.backgrounds[i % CartoonLook.backgrounds.size], glasses = i % 2 == 1,
            )
        }
        val states = listOf(AnimState(), AnimState(open = 0.7f, smile = 0.6f), AnimState(blink = 1f, open = 0.3f, wide = 1f),
            AnimState(open = 0.9f, brow = 1f, eyesWide = 0.25f), AnimState(open = 0.4f, brow = -0.8f, smile = -0.3f, roll = 0.04f))
        looks.forEachIndexed { i, lk ->
            val r = CartoonAvatar(lk)
            states.forEachIndexed { k, s ->
                val bmp = Bitmap.createBitmap(360, 640, Bitmap.Config.ARGB_8888)
                r.draw(Canvas(bmp), 360, 640, s)
                File(out, "${i}_$k.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }
}
