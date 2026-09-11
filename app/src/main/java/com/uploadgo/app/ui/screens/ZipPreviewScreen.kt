package com.uploadgo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Deselect
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uploadgo.app.AppGraph
import com.uploadgo.app.R
import com.uploadgo.app.data.model.MediaItem
import com.uploadgo.app.data.model.MediaKind
import com.uploadgo.app.data.zip.ZipState
import com.uploadgo.app.data.zip.ZipStatus
import com.uploadgo.app.ui.components.MediaThumbnail
import com.uploadgo.app.ui.components.ShareProgressBanner
import com.uploadgo.app.ui.components.ShareUiController
import com.uploadgo.app.ui.components.rememberShareUiController
import com.uploadgo.app.util.Format
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZipPreviewScreen(
    zipId: String,
    onBack: () -> Unit,
) {
    val store = AppGraph.sessionStore
    val zipStates by store.zipStates.collectAsStateWithLifecycle()
    val zipIncluded by store.zipIncluded.collectAsStateWithLifecycle()
    val settings by AppGraph.settingsRepository.settings.collectAsStateWithLifecycle(
        com.uploadgo.app.data.prefs.Settings()
    )

    val zipItem = store.itemById(zipId)
    val state = zipStates[zipId]

    // Extract on first open when not already done (e.g. auto-extract disabled).
    LaunchedEffect(zipId) {
        val zip = store.itemById(zipId)
        val current = store.zipStateFor(zipId)
        if (zip != null && (current == null || current.status is ZipStatus.NotStarted)) {
            store.extractZip(zip)
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val shareController = rememberShareUiController { msg ->
        scope.launch { snackbarHostState.showSnackbar(msg) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.zip_preview)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = stringResource(R.string.close))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                zipItem == null -> MissingZip(onBack)

                state == null || state.status is ZipStatus.Extracting || state.status is ZipStatus.NotStarted ->
                    ExtractingState()

                state.status is ZipStatus.Error -> {
                    val message = (state.status as ZipStatus.Error).message
                    ZipErrorState(
                        message = message,
                        onBack = onBack,
                        onRetry = { scope.launch { store.extractZip(zipItem, force = true) } },
                    )
                }

                else -> {
                    val included = zipIncluded[zipId] ?: emptySet()
                    ZipReadyContent(
                        zipName = zipItem.displayName,
                        state = state,
                        included = included,
                        showUnsupported = settings.showUnsupported,
                        onToggle = { id -> store.toggleZipItem(zipId, id) },
                        onSelectAll = { store.selectAllInZip(zipId) },
                        onClearAll = { store.clearZipSelection(zipId) },
                        onShareContents = {
                            shareController.launch(batchSize = settings.shareBatchSize) {
                                AppGraph.shareService.prepareShareZipMedia(zipId)
                            }
                        },
                        onShareOriginal = {
                            shareController.launch(batchSize = settings.shareBatchSize) { listOf(zipItem) }
                        },
                        shareController = shareController,
                    )
                }
            }
        }
    }
}

@Composable
private fun MissingZip(onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Rounded.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.zip_error_title), style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onBack) { Text(stringResource(R.string.ok)) }
    }
}

@Composable
private fun ExtractingState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.zip_extracting), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ZipErrorState(
    message: String,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Rounded.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.zip_error_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.zip_try_again))
        }
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.ok))
        }
    }
}

@Composable
private fun ZipReadyContent(
    zipName: String,
    state: ZipState,
    included: Set<String>,
    showUnsupported: Boolean,
    onToggle: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearAll: () -> Unit,
    onShareContents: () -> Unit,
    onShareOriginal: () -> Unit,
    shareController: ShareUiController,
) {
    val media = state.media
    val preparing = shareController.preparing

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.FolderZip,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = zipName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.zip_files_and_media, state.totalEntries, state.supportedCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (showUnsupported && state.unsupportedCount > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(10.dp),
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (state.unsupportedCount == 1) {
                            stringResource(R.string.unsupported_file_skipped)
                        } else {
                            stringResource(R.string.unsupported_files_skipped, state.unsupportedCount)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onSelectAll, enabled = media.isNotEmpty()) {
                    Icon(Icons.Rounded.SelectAll, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.select_all))
                }
                TextButton(onClick = onClearAll, enabled = included.isNotEmpty()) {
                    Icon(Icons.Rounded.Deselect, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.clear_all))
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.selected_count, included.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Grid of extracted media
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 104.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            items(media, key = { it.id }) { item ->
                ExtractedTile(
                    item = item,
                    selected = item.id in included,
                    onClick = { onToggle(item.id) },
                    onToggle = { onToggle(item.id) },
                )
            }
        }

        // Actions
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            ShareProgressBanner(
                controller = shareController,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Button(
                onClick = onShareContents,
                enabled = included.isNotEmpty() && !preparing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (preparing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.preparing_share))
                } else {
                    Icon(Icons.Rounded.Share, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.share_contents))
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onShareOriginal,
                enabled = !preparing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.FolderZip, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.share_original_zip))
            }
            Text(
                text = stringResource(R.string.original_zip_never_modified),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun ExtractedTile(
    item: MediaItem,
    selected: Boolean,
    onClick: () -> Unit,
    onToggle: () -> Unit,
) {
    val typeLabel = when (item.kind) {
        MediaKind.IMAGE -> stringResource(R.string.type_image)
        MediaKind.VIDEO -> stringResource(R.string.type_video)
        else -> stringResource(R.string.type_file)
    }

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                MediaThumbnail(item, Modifier.fillMaxSize())
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onToggle() },
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                )
                if (selected) {
                    Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp),
                    )
                }
            }
            Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "$typeLabel • ${Format.bytes(item.sizeBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}
