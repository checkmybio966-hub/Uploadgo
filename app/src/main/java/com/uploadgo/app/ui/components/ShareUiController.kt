package com.uploadgo.app.ui.components

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.uploadgo.app.AppGraph
import com.uploadgo.app.R
import com.uploadgo.app.data.model.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Turns a "prepare items" suspend function into the full share flow:
 * prepare → (optionally split into batches) → launch the Android Sharesheet →
 * record history + schedule temp cleanup.
 *
 * UploadGo never uploads anything itself — the receiving app (e.g. Telegram)
 * performs the actual send, so there is no "upload progress" here, only a
 * "batch X of Y handed to the Sharesheet" state.
 *
 * Large selections are split into smaller batches (`Settings.shareBatchSize`).
 * When the user returns from the Sharesheet, the next batch follows
 * automatically (when a target was chosen) or waits behind a Continue button.
 * This avoids errors in apps that reject very large single imports.
 */
class ShareUiController(
    private val scope: CoroutineScope,
    private val context: Context,
    private val launchIntent: (Intent) -> Unit,
    private val onError: (String) -> Unit,
) {
    var preparing by mutableStateOf(false)
        private set

    /** (currentBatch, totalBatches) while a multi-batch share is in progress. */
    var progress by mutableStateOf<Pair<Int, Int>?>(null)
        private set

    /** True when the next batch is waiting for the user to tap Continue. */
    var paused by mutableStateOf(false)
        private set

    private val pending = ArrayDeque<List<MediaItem>>()
    private val allShared = mutableListOf<MediaItem>()
    private var totalBatches = 0
    private var currentBatch = 0
    private var active = false
    private var returnedFromChooser = false
    private var lastAccepted = true

    fun launch(prepare: suspend () -> List<MediaItem>, batchSize: Int = Int.MAX_VALUE) {
        if (preparing) return
        scope.launch {
            preparing = true
            active = false
            progress = null
            paused = false

            val items = try {
                prepare()
            } catch (e: Exception) {
                onError(e.message ?: context.getString(R.string.error_share_failed))
                preparing = false
                return@launch
            }
            if (items.isEmpty()) {
                onError(context.getString(R.string.error_nothing_to_share))
                preparing = false
                return@launch
            }

            val size = if (batchSize <= 0) items.size.coerceAtLeast(1) else batchSize
            val chunks = items.chunked(size)
            pending.clear()
            chunks.forEach { pending.addLast(it) }
            allShared.clear()
            totalBatches = chunks.size
            currentBatch = 0
            active = true
            lastAccepted = true
            returnedFromChooser = false
            launchNextBatch()
        }
    }

    private fun launchNextBatch() {
        if (!active) return
        if (pending.isEmpty()) {
            finish()
            return
        }
        val items = pending.removeFirst()
        currentBatch++
        progress = if (totalBatches > 1) Pair(currentBatch, totalBatches) else null
        paused = false

        val chooser = AppGraph.shareService.chooserFor(items)
        if (chooser == null) {
            onError(context.getString(R.string.error_nothing_to_share))
            finish()
            return
        }

        allShared += items
        scope.launch { runCatching { AppGraph.shareService.recordShared(items) } }
        returnedFromChooser = false

        try {
            launchIntent(chooser)
        } catch (e: ActivityNotFoundException) {
            onError(context.getString(R.string.error_no_share_target))
            finish()
        } catch (e: Exception) {
            onError(e.message ?: context.getString(R.string.error_share_failed))
            finish()
        }
    }

    /** Chooser result: true when the user picked a target, false when dismissed. */
    fun onChooserResult(accepted: Boolean) {
        returnedFromChooser = true
        lastAccepted = accepted
    }

    /** Called when the screen resumes (the user returned from the Sharesheet). */
    fun onResumed() {
        if (!active || !preparing || !returnedFromChooser) return
        if (pending.isEmpty()) {
            finish()
            return
        }
        if (lastAccepted) {
            scope.launch {
                delay(400)
                launchNextBatch()
            }
        } else {
            paused = true
        }
    }

    /** Resume a paused multi-batch share. */
    fun continueSharing() {
        if (!active || !paused) return
        scope.launch { launchNextBatch() }
    }

    /** Stop the share flow and schedule temp cleanup for what was handed off. */
    fun cancel() {
        if (!active) return
        active = false
        preparing = false
        progress = null
        paused = false
        scope.launch { runCatching { AppGraph.shareService.cleanupAfterShare(allShared) } }
        allShared.clear()
    }

    private fun finish() {
        active = false
        preparing = false
        progress = null
        paused = false
        scope.launch { runCatching { AppGraph.shareService.cleanupAfterShare(allShared) } }
        allShared.clear()
    }
}

/** Banner shown while a multi-batch share is in progress (with Continue/Stop). */
@Composable
fun ShareProgressBanner(controller: ShareUiController, modifier: Modifier = Modifier) {
    val progress = controller.progress ?: return
    val (current, total) = progress

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.sharing_batch, current, total),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (controller.paused) {
                    Button(onClick = { controller.continueSharing() }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.continue_sharing))
                    }
                    Spacer(Modifier.width(8.dp))
                }
                TextButton(onClick = { controller.cancel() }) {
                    Text(stringResource(R.string.stop_sharing))
                }
            }
        }
    }
}

@Composable
fun rememberShareUiController(
    onError: (String) -> Unit,
): ShareUiController {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val latestError = rememberUpdatedState(onError)

    val holder = remember { arrayOfNulls<ShareUiController>(1) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        holder[0]?.onChooserResult(result.resultCode == Activity.RESULT_OK)
    }

    val controller = remember(context, scope) {
        ShareUiController(
            scope = scope,
            context = context,
            launchIntent = { intent -> launcher.launch(intent) },
            onError = { msg -> latestError.value(msg) },
        ).also { holder[0] = it }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) holder[0]?.onResumed()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return controller
}
