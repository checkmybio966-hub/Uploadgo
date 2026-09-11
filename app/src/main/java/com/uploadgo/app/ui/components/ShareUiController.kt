package com.uploadgo.app.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.uploadgo.app.AppGraph
import com.uploadgo.app.R
import com.uploadgo.app.data.model.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Small controller that turns a "prepare items" suspend function into the full
 * share flow: prepare → build chooser → launch the Android Sharesheet →
 * record history + schedule temp cleanup. The receiving app (e.g. Telegram)
 * handles the actual upload; UploadGo only hands over the files.
 */
class ShareUiController(
    private val scope: CoroutineScope,
    private val context: Context,
    private val onError: (String) -> Unit,
) {
    var preparing by mutableStateOf(false)
        private set

    fun launch(prepare: suspend () -> List<MediaItem>) {
        if (preparing) return
        scope.launch {
            preparing = true
            try {
                val items = prepare()
                if (items.isEmpty()) {
                    onError(context.getString(R.string.error_nothing_to_share))
                    return@launch
                }
                val chooser = AppGraph.shareService.chooserFor(items)
                    ?: run {
                        onError(context.getString(R.string.error_nothing_to_share))
                        return@launch
                    }
                try {
                    context.startActivity(chooser)
                    AppGraph.shareService.finalizeShare(items)
                } catch (e: ActivityNotFoundException) {
                    onError(context.getString(R.string.error_no_share_target))
                }
            } catch (e: Exception) {
                onError(e.message ?: context.getString(R.string.error_share_failed))
            } finally {
                preparing = false
            }
        }
    }
}

@Composable
fun rememberShareUiController(onError: (String) -> Unit): ShareUiController {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val latestOnError = rememberUpdatedState(onError)
    return remember(context, scope) {
        ShareUiController(scope, context, onError = { msg -> latestOnError.value(msg) })
    }
}
