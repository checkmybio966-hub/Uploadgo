package com.uploadgo.app.data

import com.uploadgo.app.data.db.HistoryDao
import com.uploadgo.app.data.model.HistoryEntry
import com.uploadgo.app.data.model.HistoryType
import com.uploadgo.app.data.model.MediaItem
import com.uploadgo.app.data.model.MediaKind
import kotlinx.coroutines.flow.Flow

/**
 * Stores the local share/prepare history. Wording is intentionally neutral:
 * entries record what UploadGo prepared or handed to the Sharesheet, not what
 * Telegram (or any other app) delivered.
 */
class HistoryRepository(private val dao: HistoryDao) {

    val entries: Flow<List<HistoryEntry>> = dao.observeAll()

    suspend fun recordShared(items: List<MediaItem>) {
        dao.insert(
            HistoryEntry(
                timestamp = System.currentTimeMillis(),
                type = HistoryType.SHARED.name,
                category = categoryFor(items),
                itemCount = items.size,
                detail = "Shared via Android Sharesheet",
                fileNames = items.joinToString(", ") { it.displayName },
                totalBytes = items.sumOf { it.sizeBytes.coerceAtLeast(0) },
            )
        )
    }

    suspend fun recordPrepared(zipName: String, mediaCount: Int) {
        dao.insert(
            HistoryEntry(
                timestamp = System.currentTimeMillis(),
                type = HistoryType.PREPARED.name,
                category = "ZIP Contents",
                itemCount = mediaCount,
                detail = "$zipName • $mediaCount media files",
                fileNames = zipName,
                totalBytes = 0,
            )
        )
    }

    suspend fun clear() = dao.clear()

    suspend fun delete(id: Long) = dao.deleteById(id)

    private fun categoryFor(items: List<MediaItem>): String {
        if (items.isEmpty()) return "Files"
        val hasExtracted = items.any { it.isExtracted }
        val allZip = items.all { it.isZip }
        val hasImage = items.any { it.kind == MediaKind.IMAGE }
        val hasVideo = items.any { it.kind == MediaKind.VIDEO }
        return when {
            hasExtracted -> "ZIP Contents"
            allZip -> "Original ZIP"
            hasImage && hasVideo -> "Images + Videos"
            hasImage -> "Images"
            hasVideo -> "Videos"
            else -> "Files"
        }
    }
}
