package com.uploadgo.app.data.model

import android.net.Uri

/** Broad category of a selected file. */
enum class MediaKind { IMAGE, VIDEO, ZIP, UNSUPPORTED }

/** Where a file came from: the system picker, or extracted from a ZIP. */
enum class Source { PICKED, EXTRACTED }

/**
 * A single selectable file. Original files keep the SAF `content://` URI the
 * user granted; extracted ZIP contents carry a `content://` URI served by the
 * app's FileProvider — never a raw filesystem path.
 */
data class MediaItem(
    val id: String,
    val uri: Uri,
    val displayName: String,
    val mimeType: String?,
    val sizeBytes: Long,
    val kind: MediaKind,
    val source: Source,
    val originZipId: String? = null,
    val originZipName: String? = null,
) {
    val isZip: Boolean get() = kind == MediaKind.ZIP
    val isExtracted: Boolean get() = source == Source.EXTRACTED
    val isMedia: Boolean get() = kind == MediaKind.IMAGE || kind == MediaKind.VIDEO
}
