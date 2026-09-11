package com.uploadgo.app.data.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import com.uploadgo.app.R
import com.uploadgo.app.data.model.MediaItem
import com.uploadgo.app.util.MimeTypes
import java.util.ArrayList

/**
 * Builds Android Sharesheet intents using the standard ACTION_SEND /
 * ACTION_SEND_MULTIPLE mechanism. UploadGo never uploads anything itself; it
 * only hands content:// URIs (SAF URIs or FileProvider URIs) to the receiving
 * app with temporary read permission granted.
 */
class ShareManager(private val context: Context) {

    fun buildIntent(items: List<MediaItem>): Intent? {
        if (items.isEmpty()) return null
        val uris = items.map { it.uri }
        val type = commonMimeType(items)

        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                setType(type)
                putExtra(Intent.EXTRA_STREAM, uris.first())
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                setType(type)
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            }
        }

        // ClipData makes the URIs reachable by apps that read the clipboard-style stream.
        val clip = ClipData.newRawUri("UploadGo files", uris.first())
        for (i in 1 until uris.size) clip.addItem(ClipData.Item(uris[i]))
        intent.clipData = clip

        // Temporary read permission for the receiving app.
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return intent
    }

    /** Wraps [buildIntent] in a system chooser (the native Sharesheet). */
    fun buildChooser(items: List<MediaItem>): Intent? {
        val send = buildIntent(items) ?: return null
        return Intent.createChooser(send, context.getString(R.string.share_chooser_title)).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * Picks a sensible MIME type for a mixed batch. A generic fallback keeps
     * the Sharesheet available for any target, but more specific types such as
     * image and video are preferred so targets filter correctly.
     */
    private fun commonMimeType(items: List<MediaItem>): String {
        val mimes = items.map { it.mimeType ?: MimeTypes.OCTET_STREAM }.distinct()
        return when {
            mimes.size == 1 -> mimes.first()
            mimes.all { it.startsWith("image/") } -> "image/*"
            mimes.all { it.startsWith("video/") } -> "video/*"
            else -> "*/*"
        }
    }
}
