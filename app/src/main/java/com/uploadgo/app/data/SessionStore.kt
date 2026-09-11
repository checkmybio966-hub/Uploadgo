package com.uploadgo.app.data

import com.uploadgo.app.data.model.MediaItem
import com.uploadgo.app.data.temp.TempFileManager
import com.uploadgo.app.data.zip.ZipExtractionException
import com.uploadgo.app.data.zip.ZipRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** State of a single ZIP archive in the current session. */
sealed interface ZipStatus {
    object NotStarted : ZipStatus
    object Extracting : ZipStatus
    data class Ready(
        val totalEntries: Int,
        val supportedCount: Int,
        val unsupportedCount: Int,
        val media: List<MediaItem>,
        val outputDir: File,
    ) : ZipStatus
    data class Error(val message: String) : ZipStatus
}

data class ZipState(val zipId: String, val status: ZipStatus) {
    val media: List<MediaItem> get() = (status as? ZipStatus.Ready)?.media ?: emptyList()
    val outputDir: File? get() = (status as? ZipStatus.Ready)?.outputDir
    val totalEntries: Int get() = (status as? ZipStatus.Ready)?.totalEntries ?: 0
    val supportedCount: Int get() = (status as? ZipStatus.Ready)?.supportedCount ?: 0
    val unsupportedCount: Int get() = (status as? ZipStatus.Ready)?.unsupportedCount ?: 0
}

/**
 * In-memory holder for the current sharing session. It is process-scoped (a
 * singleton) so selection and extraction survive configuration changes, and is
 * deliberately not persisted — the persistent record lives in History.
 */
class SessionStore(
    private val zipRepository: ZipRepository,
    private val tempFileManager: TempFileManager,
    private val historyRepository: HistoryRepository,
) {

    private val _picked = MutableStateFlow<List<MediaItem>>(emptyList())
    val picked: StateFlow<List<MediaItem>> = _picked.asStateFlow()

    private val _included = MutableStateFlow<Set<String>>(emptySet())
    val included: StateFlow<Set<String>> = _included.asStateFlow()

    private val _zipStates = MutableStateFlow<Map<String, ZipState>>(emptyMap())
    val zipStates: StateFlow<Map<String, ZipState>> = _zipStates.asStateFlow()

    private val _zipIncluded = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val zipIncluded: StateFlow<Map<String, Set<String>>> = _zipIncluded.asStateFlow()

    // ------------------------------------------------------------------ picked

    fun addPicked(items: List<MediaItem>) {
        if (items.isEmpty()) return
        val merged = LinkedHashMap<String, MediaItem>()
        _picked.value.forEach { merged[it.uri.toString()] = it }
        items.forEach { merged[it.uri.toString()] = it }
        val newList = merged.values.toList()
        _picked.value = newList
        _included.value = newList.map { it.id }.toSet()
    }

    fun removePicked(id: String) {
        _picked.value = _picked.value.filterNot { it.id == id }
        _included.value = _included.value - id
    }

    fun clearPicked() {
        _picked.value = emptyList()
        _included.value = emptySet()
    }

    fun toggleIncluded(id: String) {
        _included.value = if (id in _included.value) _included.value - id else _included.value + id
    }

    fun selectAllPicked() {
        _included.value = _picked.value.map { it.id }.toSet()
    }

    fun itemById(id: String): MediaItem? = _picked.value.firstOrNull { it.id == id }

    fun includedPickedItems(): List<MediaItem> =
        _picked.value.filter { it.id in _included.value }

    // -------------------------------------------------------------------- zip

    fun zipStateFor(zipId: String): ZipState? = _zipStates.value[zipId]

    /** Extracts a ZIP once; subsequent calls return the cached state. */
    suspend fun extractZip(zipItem: MediaItem, force: Boolean = false): ZipState {
        val existing = _zipStates.value[zipItem.id]
        if (!force && existing != null && existing.status is ZipStatus.Ready) return existing
        if (!force && existing != null && existing.status is ZipStatus.Extracting) return existing

        tempFileManager.ensureRoot()
        _zipStates.value = _zipStates.value + (zipItem.id to ZipState(zipItem.id, ZipStatus.Extracting))

        return try {
            val result = zipRepository.extract(zipItem, tempFileManager.extractionDirFor(zipItem.id))
            val state = ZipState(
                zipId = zipItem.id,
                status = ZipStatus.Ready(
                    result.totalEntries,
                    result.supportedCount,
                    result.unsupportedCount,
                    result.mediaItems,
                    result.outputDir,
                ),
            )
            _zipStates.value = _zipStates.value + (zipItem.id to state)
            _zipIncluded.value = _zipIncluded.value + (zipItem.id to result.mediaItems.map { it.id }.toSet())
            historyRepository.recordPrepared(zipItem.displayName, result.supportedCount)
            state
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            val message = (e as? ZipExtractionException)?.message
                ?: "Could not open ZIP. The ZIP may be corrupted or unsupported."
            val state = ZipState(zipItem.id, ZipStatus.Error(message))
            _zipStates.value = _zipStates.value + (zipItem.id to state)
            state
        }
    }

    fun toggleZipItem(zipId: String, itemId: String) {
        val current = _zipIncluded.value[zipId] ?: emptySet()
        _zipIncluded.value = _zipIncluded.value + (zipId to
            if (itemId in current) current - itemId else current + itemId)
    }

    fun selectAllInZip(zipId: String) {
        val media = _zipStates.value[zipId]?.media ?: return
        _zipIncluded.value = _zipIncluded.value + (zipId to media.map { it.id }.toSet())
    }

    fun clearZipSelection(zipId: String) {
        _zipIncluded.value = _zipIncluded.value + (zipId to emptySet())
    }

    fun includedZipItems(zipId: String): List<MediaItem> {
        val media = _zipStates.value[zipId]?.media ?: return emptyList()
        val included = _zipIncluded.value[zipId] ?: emptySet()
        return media.filter { it.id in included }
    }
}
