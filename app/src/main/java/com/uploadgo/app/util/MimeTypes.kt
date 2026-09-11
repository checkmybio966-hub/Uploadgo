package com.uploadgo.app.util

import java.util.Locale

/**
 * MIME type helpers. UploadGo supports images, videos and ZIP archives, and
 * picks sensible MIME types for both the system picker and the Sharesheet.
 */
object MimeTypes {
    const val ZIP = "application/zip"
    const val JPEG = "image/jpeg"
    const val PNG = "image/png"
    const val WEBP = "image/webp"
    const val GIF = "image/gif"
    const val MP4 = "video/mp4"
    const val QUICKTIME = "video/quicktime"
    const val MATROSKA = "video/x-matroska"
    const val OCTET_STREAM = "application/octet-stream"

    val IMAGE_TYPES = setOf(JPEG, PNG, WEBP, GIF)
    val VIDEO_TYPES = setOf(MP4, QUICKTIME, MATROSKA)
    val ZIP_TYPES = setOf(ZIP, "application/x-zip", "application/x-zip-compressed")

    /** MIME types offered to Android's native file picker. */
    val PICKER_TYPES = arrayOf(
        JPEG, PNG, WEBP, GIF,
        MP4, QUICKTIME, MATROSKA,
        ZIP, "application/x-zip", "application/x-zip-compressed"
    )

    fun fromFileName(name: String): String? {
        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return when (ext) {
            "jpg", "jpeg" -> JPEG
            "png" -> PNG
            "webp" -> WEBP
            "gif" -> GIF
            "mp4" -> MP4
            "mov" -> QUICKTIME
            "mkv" -> MATROSKA
            "zip" -> ZIP
            else -> null
        }
    }
}
