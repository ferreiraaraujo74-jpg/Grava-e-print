package com.tracomagico.app.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

data class Creation(val uri: Uri, val name: String, val dateAdded: Long)

/** Abrir fotos, salvar e compartilhar desenhos. */
object Images {
    const val FOLDER = "TracoMagico"
    private val RELATIVE = "${Environment.DIRECTORY_PICTURES}/$FOLDER"

    /** Decodifica já reduzido (respeita a orientação EXIF) e em bitmap de software. */
    fun load(ctx: Context, uri: Uri, maxSide: Int): Bitmap {
        val src = ImageDecoder.createSource(ctx.contentResolver, uri)
        return ImageDecoder.decodeBitmap(src) { dec, info, _ ->
            dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            dec.isMutableRequired = false
            val w = info.size.width; val h = info.size.height
            val scale = maxSide.toFloat() / max(w, h)
            if (scale < 1f) dec.setTargetSize((w * scale).roundToInt().coerceAtLeast(1), (h * scale).roundToInt().coerceAtLeast(1))
        }.let { if (it.config != Bitmap.Config.ARGB_8888) it.copy(Bitmap.Config.ARGB_8888, false) else it }
    }

    fun fit(b: Bitmap, maxSide: Int): Bitmap {
        val scale = maxSide.toFloat() / max(b.width, b.height)
        if (scale >= 1f) return b
        return Bitmap.createScaledBitmap(b, (b.width * scale).roundToInt().coerceAtLeast(1), (b.height * scale).roundToInt().coerceAtLeast(1), true)
    }

    private fun stamp() = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    /** Salva na Galeria: Pictures/TracoMagico/desenho_AAAAMMDD_HHMMSS.png */
    fun saveToGallery(ctx: Context, bmp: Bitmap): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "desenho_${stamp()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, RELATIVE)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val r = ctx.contentResolver
        val uri = r.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values) ?: return null
        return try {
            r.openOutputStream(uri)!!.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            r.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            uri
        } catch (t: Throwable) {
            runCatching { r.delete(uri, null, null) }; null
        }
    }

    /** Grava num arquivo temporário e devolve um Intent de compartilhamento. */
    fun shareIntent(ctx: Context, bmp: Bitmap): Intent {
        val dir = File(ctx.cacheDir, "share").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val f = File(dir, "desenho_${stamp()}.png")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
        return shareUri(uri)
    }

    fun shareUri(uri: Uri): Intent = Intent.createChooser(
        Intent(Intent.ACTION_SEND).setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
        "Compartilhar desenho",
    )

    /** Uri temporária para a câmera gravar a foto. */
    fun newCameraUri(ctx: Context): Uri {
        val dir = File(ctx.cacheDir, "camera").apply { mkdirs() }
        dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000 }?.forEach { it.delete() }
        return FileProvider.getUriForFile(ctx, ctx.packageName + ".files", File(dir, "foto_${stamp()}.jpg"))
    }

    fun creations(ctx: Context): List<Creation> {
        val coll = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val out = ArrayList<Creation>()
        runCatching {
            ctx.contentResolver.query(
                coll,
                arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.DATE_ADDED),
                "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?", arrayOf("$RELATIVE%"),
                "${MediaStore.Images.Media.DATE_ADDED} DESC",
            )?.use { c ->
                while (c.moveToNext()) {
                    out += Creation(ContentUris.withAppendedId(coll, c.getLong(0)), c.getString(1) ?: "", c.getLong(2))
                }
            }
        }
        return out
    }
}
