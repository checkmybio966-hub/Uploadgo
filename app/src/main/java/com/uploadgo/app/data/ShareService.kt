package com.uploadgo.app.data

import android.content.Intent
import com.uploadgo.app.data.model.MediaItem
import com.uploadgo.app.data.prefs.SettingsRepository
import com.uploadgo.app.data.share.ShareManager
import com.uploadgo.app.data.temp.TempFileManager
import com.uploadgo.app.data.zip.ZipStatus
import kotlinx.coroutines.flow.first

/**
 * Coordinates the share flow: resolves what to share (including ZIP extraction
 * on demand), builds the chooser intent, and records history plus deferred
 * temporary-file cleanup.
 */
class ShareService(
    private val sessionStore: SessionStore,
    private val shareManager: ShareManager,
    private val historyRepository: HistoryRepository,
    private val tempFileManager: TempFileManager,
    private val settingsRepository: SettingsRepository,
) {

    /** The included picked files, exactly as selected (ZIPs stay ZIPs). */
    suspend fun prepareShareSelected(): List<MediaItem> =
        sessionStore.includedPickedItems()

    /**
     * The included picked files, but with the contents of any selected ZIP
     * substituted for the ZIP itself. ZIPs are extracted on demand.
     */
    suspend fun prepareShareZipContents(): List<MediaItem> {
        val result = mutableListOf<MediaItem>()
        for (item in sessionStore.includedPickedItems()) {
            if (item.isZip) {
                val state = sessionStore.extractZip(item)
                if (state.status is ZipStatus.Error) {
                    throw IllegalStateException((state.status as ZipStatus.Error).message)
                }
                result += sessionStore.includedZipItems(item.id)
            } else {
                result += item
            }
        }
        return result
    }

    /** Only the original ZIP archives themselves. */
    suspend fun prepareShareOriginalZips(): List<MediaItem> =
        sessionStore.includedPickedItems().filter { it.isZip }

    /** The selected extracted media from a single ZIP. */
    fun prepareShareZipMedia(zipId: String): List<MediaItem> =
        sessionStore.includedZipItems(zipId)

    fun chooserFor(items: List<MediaItem>): Intent? = shareManager.buildChooser(items)

    /** Records one shared batch in the local history. */
    suspend fun recordShared(items: List<MediaItem>) {
        historyRepository.recordShared(items)
    }

    /**
     * Schedules deferred cleanup for the extracted contents of [items] once the
     * whole share operation is finished (never while batches are still being
     * handed off).
     */
    suspend fun cleanupAfterShare(items: List<MediaItem>) {
        val deleteTemp = settingsRepository.settings.first().deleteTempAfterShare
        if (!deleteTemp) return
        items.filter { it.isExtracted }
            .mapNotNull { it.originZipId }
            .distinct()
            .forEach { zipId ->
                sessionStore.zipStateFor(zipId)?.outputDir?.let { dir ->
                    tempFileManager.scheduleCleanup(dir)
                }
            }
    }

    /** Convenience: record + cleanup in one call. */
    suspend fun finalizeShare(items: List<MediaItem>) {
        recordShared(items)
        cleanupAfterShare(items)
    }
}
