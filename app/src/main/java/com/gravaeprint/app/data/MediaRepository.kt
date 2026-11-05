package com.gravaeprint.app.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

data class MediaItem(
    val uri: Uri,
    val name: String,
    val isVideo: Boolean,
    val dateAdded: Long,
    val durationMs: Long,
    val mime: String,
)

/** Lista os vídeos e prints do app no MediaStore. */
object MediaRepository {

    fun videos(ctx: Context): List<MediaItem> = query(
        ctx,
        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
        MediaSaver.VIDEO_PATH,
        isVideo = true,
    )

    fun prints(ctx: Context): List<MediaItem> = query(
        ctx,
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
        MediaSaver.PRINT_PATH,
        isVideo = false,
    )

    private fun query(ctx: Context, coll: Uri, path: String, isVideo: Boolean): List<MediaItem> {
        val cols = mutableListOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.MIME_TYPE,
        )
        if (isVideo) cols += MediaStore.Video.Media.DURATION
        val out = ArrayList<MediaItem>()
        runCatching {
            ctx.contentResolver.query(
                coll, cols.toTypedArray(),
                "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?", arrayOf("$path%"),
                "${MediaStore.MediaColumns.DATE_ADDED} DESC",
            )?.use { c ->
                val iId = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val iName = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val iDate = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val iMime = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val iDur = if (isVideo) c.getColumnIndex(MediaStore.Video.Media.DURATION) else -1
                while (c.moveToNext()) {
                    out += MediaItem(
                        uri = ContentUris.withAppendedId(coll, c.getLong(iId)),
                        name = c.getString(iName) ?: "",
                        isVideo = isVideo,
                        dateAdded = c.getLong(iDate),
                        durationMs = if (iDur >= 0) c.getLong(iDur) else 0,
                        mime = c.getString(iMime) ?: if (isVideo) "video/mp4" else "image/png",
                    )
                }
            }
        }
        return out
    }
}
