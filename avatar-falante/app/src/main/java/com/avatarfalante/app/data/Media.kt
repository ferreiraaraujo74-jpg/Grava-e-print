package com.avatarfalante.app.data

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

data class VideoItem(val uri: Uri, val name: String, val durationMs: Long)

object Media {
    private val RELATIVE = "${Environment.DIRECTORY_MOVIES}/AvatarFalante"

    fun loadPhoto(ctx: Context, uri: Uri, maxSide: Int = 1280): Bitmap {
        val src = ImageDecoder.createSource(ctx.contentResolver, uri)
        val b = ImageDecoder.decodeBitmap(src) { dec, info, _ ->
            dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val w = info.size.width; val h = info.size.height
            val s = maxSide.toFloat() / max(w, h)
            if (s < 1f) dec.setTargetSize((w * s).roundToInt().coerceAtLeast(1), (h * s).roundToInt().coerceAtLeast(1))
        }
        return if (b.config != Bitmap.Config.ARGB_8888) b.copy(Bitmap.Config.ARGB_8888, false) else b
    }

    fun newCameraUri(ctx: Context): Uri {
        val dir = File(ctx.cacheDir, "camera").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        return FileProvider.getUriForFile(ctx, ctx.packageName + ".files", File(dir, "foto_${System.currentTimeMillis()}.jpg"))
    }

    fun shareVideo(uri: Uri): Intent = Intent.createChooser(
        Intent(Intent.ACTION_SEND).setType("video/mp4").putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
        "Compartilhar vídeo",
    )

    fun openVideo(uri: Uri): Intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "video/mp4")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    fun videos(ctx: Context): List<VideoItem> {
        val coll = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val out = ArrayList<VideoItem>()
        runCatching {
            ctx.contentResolver.query(
                coll, arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.DURATION),
                "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?", arrayOf("$RELATIVE%"),
                "${MediaStore.Video.Media.DATE_ADDED} DESC",
            )?.use { c ->
                while (c.moveToNext()) out += VideoItem(ContentUris.withAppendedId(coll, c.getLong(0)), c.getString(1) ?: "", c.getLong(2))
            }
        }
        return out
    }
}
