package com.uploadgo.app.data.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.LruCache
import com.uploadgo.app.data.model.MediaItem
import com.uploadgo.app.data.model.MediaKind
import com.uploadgo.app.data.model.Source
import com.uploadgo.app.util.MimeTypes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID

/**
 * Resolves metadata (name, size, MIME, kind) for SAF URIs and produces
 * lightweight video thumbnails. Thumbnails are cached and downscaled so large
 * videos are never fully decoded into RAM.
 */
class MediaRepository(private val context: Context) {

    private val videoThumbCache = LruCache<String, Bitmap>(48)

    suspend fun resolve(uri: Uri): MediaItem? = withContext(Dispatchers.IO) {
        runCatching {
            val name = queryDisplayName(uri) ?: uri.lastPathSegment ?: "file"
            val mime = context.contentResolver.getType(uri) ?: MimeTypes.fromFileName(name)
            MediaItem(
                id = "picked-${UUID.randomUUID()}",
                uri = uri,
                displayName = name,
                mimeType = mime,
                sizeBytes = querySize(uri),
                kind = kindFor(name, mime),
                source = Source.PICKED,
            )
        }.getOrNull()
    }

    fun kindFor(name: String, mime: String?): MediaKind {
        val m = mime?.lowercase(Locale.ROOT)
        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return when {
            ext == "jpg" || ext == "jpeg" || ext == "png" || ext == "webp" || ext == "gif" -> MediaKind.IMAGE
            ext == "mp4" || ext == "mov" || ext == "mkv" -> MediaKind.VIDEO
            ext == "zip" -> MediaKind.ZIP
            m != null && m.startsWith("image/") -> MediaKind.IMAGE
            m != null && m.startsWith("video/") -> MediaKind.VIDEO
            m == MimeTypes.ZIP || m == "application/x-zip" || m == "application/x-zip-compressed" -> MediaKind.ZIP
            else -> MediaKind.UNSUPPORTED
        }
    }

    /** True when the filename maps to a supported image/video type. */
    fun isSupportedMedia(name: String): Boolean =
        kindFor(name, MimeTypes.fromFileName(name)) in setOf(MediaKind.IMAGE, MediaKind.VIDEO)

    /**
     * Returns a small downscaled frame for a video. The frame is capped at
     * [maxDimension] pixels so a 4K clip never forces a full-resolution bitmap
     * into memory.
     */
    suspend fun videoThumbnail(uri: Uri, maxDimension: Int = 512): Bitmap? = withContext(Dispatchers.IO) {
        val key = "$uri#t$maxDimension"
        videoThumbCache.get(key)?.let { return@withContext it }

        val bitmap = runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                val frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return@runCatching null
                val scale = minOf(
                    1f,
                    maxDimension.toFloat() / maxOf(frame.width, frame.height).coerceAtLeast(1)
                )
                if (scale < 1f) {
                    val scaled = Bitmap.createScaledBitmap(
                        frame,
                        (frame.width * scale).toInt().coerceAtLeast(1),
                        (frame.height * scale).toInt().coerceAtLeast(1),
                        true
                    )
                    if (scaled !== frame) frame.recycle()
                    scaled
                } else {
                    frame
                }
            } finally {
                runCatching { retriever.release() }
            }
        }.getOrNull()

        bitmap?.let { videoThumbCache.put(key, it) }
        bitmap
    }

    private fun queryDisplayName(uri: Uri): String? {
        var name: String? = null
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) name = c.getString(idx)
            }
        }
        return name
    }

    private fun querySize(uri: Uri): Long {
        var size = -1L
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.SIZE)
                if (idx >= 0 && c.moveToFirst() && !c.isNull(idx)) size = c.getLong(idx)
            }
        }
        return size
    }
}
