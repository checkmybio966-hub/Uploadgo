package com.uploadgo.app.ui.screens

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uploadgo.app.AppGraph
import com.uploadgo.app.data.SessionStore
import com.uploadgo.app.data.model.MediaItem
import com.uploadgo.app.data.model.MediaKind
import com.uploadgo.app.data.prefs.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {

    private val store: SessionStore = AppGraph.sessionStore

    val picked: StateFlow<List<MediaItem>> = store.picked
    val included: StateFlow<Set<String>> = store.included
    val zipStates = store.zipStates
    val settings: Flow<Settings> = AppGraph.settingsRepository.settings

    private val _skipped = MutableStateFlow(0)
    val skipped: StateFlow<Int> = _skipped

    /** Called when the system picker returns URIs. */
    fun onPicked(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val resolved = uris.mapNotNull { AppGraph.mediaRepository.resolve(it) }
            val supported = resolved.filter { it.kind != MediaKind.UNSUPPORTED }
            store.addPicked(supported)
            val skippedCount = resolved.size - supported.size
            if (skippedCount > 0) _skipped.value = skippedCount

            val autoExtract = AppGraph.settingsRepository.settings.first().autoExtractZip
            if (autoExtract) {
                supported.filter { it.isZip }.forEach { zip -> store.extractZip(zip) }
            }
        }
    }

    fun consumeSkipped() {
        _skipped.value = 0
    }

    fun toggleIncluded(id: String) = store.toggleIncluded(id)
    fun selectAll() = store.selectAllPicked()
    fun clearAll() = store.clearPicked()
    fun removePicked(id: String) = store.removePicked(id)
}
