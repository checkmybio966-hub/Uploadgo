package com.uploadgo.app.data.zip

import android.content.Context
import androidx.core.content.FileProvider
import com.uploadgo.app.data.model.MediaItem
import com.uploadgo.app.data.model.MediaKind
import com.uploadgo.app.data.model.Source
import com.uploadgo.app.util.AppConstants
import com.uploadgo.app.util.MimeTypes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

class ZipExtractionException(message: String, cause: Throwable? = null) : IOException(message, cause)

data class ZipExtractionResult(
    val mediaItems: List<MediaItem>,
    val totalEntries: Int,
    val supportedCount: Int,
    val unsupportedCount: Int,
    val outputDir: File,
)

/**
 * Safely streams a ZIP from its content URI and extracts only supported image
 * and video entries into the app-private cache directory.
 *
 * Security guarantees:
 *  - Path traversal ("../", absolute paths, ".." components) is rejected.
 *  - Nothing is ever written outside [outputDir].
 *  - Nothing is ever executed.
 *  - macOS/junk entries (__MACOSX, .DS_Store, ._*) are skipped.
 *  - Entry count and total size are bounded to avoid exhausting storage.
 *  - The original ZIP is opened read-only and never modified or deleted.
 */
class ZipRepository(private val context: Context) {

    suspend fun extract(zipItem: MediaItem, outputDir: File): ZipExtractionResult = withContext(Dispatchers.IO) {
        require(zipItem.isZip) { "Not a ZIP file" }

        if (outputDir.exists()) outputDir.deleteRecursively()
        if (!outputDir.mkdirs() && !outputDir.isDirectory) {
            throw ZipExtractionException("Could not create the extraction directory.")
        }

        val input = try {
            context.contentResolver.openInputStream(zipItem.uri)
        } catch (e: Exception) {
            throw ZipExtractionException("Could not open the ZIP. It may have been moved or deleted.", e)
        } ?: throw ZipExtractionException("Could not open the ZIP. It may have been moved or deleted.")

        val media = mutableListOf<MediaItem>()
        val usedNames = HashSet<String>()
        var total = 0
        var supported = 0
        var unsupported = 0
        var totalBytes = 0L

        try {
            ZipInputStream(input.buffered()).use { zis ->
                var entry: ZipEntry?
                while (true) {
                    entry = try {
                        zis.nextEntry
                    } catch (e: ZipException) {
                        throw ZipExtractionException("Could not open ZIP. The ZIP may be corrupted or unsupported.", e)
                    }
                    if (entry == null) break

                    if (entry.isDirectory || entry.name.endsWith("/")) continue

                    total++
                    if (total > MAX_ENTRIES) throw ZipExtractionException("The ZIP contains too many files.")

                    val safePath = safePath(entry.name)
                    if (safePath == null || isJunkEntry(safePath)) {
                        unsupported++
                        zis.closeEntry()
                        continue
                    }

                    val ext = safePath.substringAfterLast('.', "").lowercase(Locale.ROOT)
                    val kind = when {
                        ext in IMAGE_EXTS -> MediaKind.IMAGE
                        ext in VIDEO_EXTS -> MediaKind.VIDEO
                        else -> MediaKind.UNSUPPORTED
                    }

                    if (kind == MediaKind.UNSUPPORTED) {
                        unsupported++
                        zis.closeEntry()
                        continue
                    }

                    if (entry.size > 0 && totalBytes + entry.size > MAX_TOTAL_BYTES) {
                        throw ZipExtractionException("Not enough storage to extract this ZIP.")
                    }

                    val outFile = allocateOutputFile(outputDir, safePath, usedNames)
                    try {
                        File(outFile.path).outputStream().buffered().use { out ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                val read = zis.read(buffer)
                                if (read == -1) break
                                out.write(buffer, 0, read)
                                totalBytes += read
                                if (totalBytes > MAX_TOTAL_BYTES) {
                                    throw ZipExtractionException("Not enough storage to extract this ZIP.")
                                }
                            }
                        }
                    } catch (e: ZipExtractionException) {
                        runCatching { outFile.delete() }
                        throw e
                    } catch (e: IOException) {
                        runCatching { outFile.delete() }
                        throw ZipExtractionException("Failed to write extracted files.", e)
                    }

                    val mime = MimeTypes.fromFileName(outFile.name)
                    val uri = FileProvider.getUriForFile(context, AppConstants.FILE_PROVIDER_AUTHORITY, outFile)
                    media += MediaItem(
                        id = "extracted-${zipItem.id}-$total",
                        uri = uri,
                        displayName = outFile.name,
                        mimeType = mime,
                        sizeBytes = outFile.length(),
                        kind = kind,
                        source = Source.EXTRACTED,
                        originZipId = zipItem.id,
                        originZipName = zipItem.displayName,
                    )
                    supported++
                    zis.closeEntry()
                }
            }
        } catch (e: ZipExtractionException) {
            runCatching { outputDir.deleteRecursively() }
            throw e
        } catch (e: IOException) {
            runCatching { outputDir.deleteRecursively() }
            throw ZipExtractionException("Could not open ZIP. The ZIP may be corrupted or unsupported.", e)
        }

        ZipExtractionResult(
            mediaItems = media,
            totalEntries = total,
            supportedCount = supported,
            unsupportedCount = unsupported,
            outputDir = outputDir,
        )
    }

    /**
     * Returns the sanitised relative path for an entry, or null if the entry
     * would escape the extraction directory (path traversal).
     */
    private fun safePath(name: String): String? {
        var n = name.replace('\\', '/')
        if (n.isEmpty() || n.startsWith('/') || n.indexOf('\u0000') >= 0) return null
        val parts = n.split('/')
        val out = ArrayList<String>(parts.size)
        for (p in parts) {
            if (p.isEmpty() || p == ".") continue
            if (p == "..") return null
            out.add(p)
        }
        if (out.isEmpty()) return null
        var path = out.joinToString("/")
        if (path.length > 220) path = out.last().take(120)
        return path
    }

    /** macOS metadata and other junk that should never be surfaced. */
    private fun isJunkEntry(path: String): Boolean {
        val components = path.split('/')
        return components.any { c ->
            c == "__MACOSX" || c == ".DS_Store" || c.startsWith("._")
        }
    }

    /** Builds a unique output File, de-duplicating repeated names. */
    private fun allocateOutputFile(root: File, path: String, used: MutableSet<String>): File {
        var unique = path
        var counter = 2
        while (!used.add(unique)) {
            val dot = unique.lastIndexOf('.')
            unique = if (dot > 0) {
                unique.substring(0, dot) + "_$counter" + unique.substring(dot)
            } else {
                unique + "_$counter"
            }
            counter++
        }
        val file = File(root, unique)
        file.parentFile?.mkdirs()
        return file
    }

    companion object {
        private const val DEFAULT_BUFFER_SIZE = 64 * 1024
        private const val MAX_ENTRIES = 20_000
        private const val MAX_TOTAL_BYTES = 4L * 1024 * 1024 * 1024 // 4 GiB guard

        private val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "webp", "gif")
        private val VIDEO_EXTS = setOf("mp4", "mov", "mkv")
    }
}
