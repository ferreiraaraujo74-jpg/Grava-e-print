package com.gravaeprint.app.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Salva vídeos e prints no MediaStore (aparecem na Galeria). */
object MediaSaver {
    const val FOLDER = "GravaEPrint"
    val VIDEO_PATH = "${Environment.DIRECTORY_MOVIES}/$FOLDER"
    val PRINT_PATH = "${Environment.DIRECTORY_PICTURES}/$FOLDER"

    private fun stamp(): String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    /** Cria o vídeo como "pendente" (invisível até [finishVideo]). */
    fun createVideo(ctx: Context): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "video_${stamp()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, VIDEO_PATH)
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val coll = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        return ctx.contentResolver.insert(coll, values) ?: error("Não foi possível criar o arquivo de vídeo")
    }

    fun finishVideo(ctx: Context, uri: Uri, keep: Boolean) {
        if (keep) {
            ctx.contentResolver.update(
                uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null
            )
        } else {
            runCatching { ctx.contentResolver.delete(uri, null, null) }
        }
    }

    fun savePrint(ctx: Context, bmp: Bitmap): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "print_${stamp()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, PRINT_PATH)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = ctx.contentResolver
        val coll = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(coll, values) ?: return null
        return try {
            resolver.openOutputStream(uri)!!.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            uri
        } catch (t: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            null
        }
    }
}
