package com.uploadgo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uploadgo.app.AppGraph
import com.uploadgo.app.R
import com.uploadgo.app.data.model.HistoryType
import com.uploadgo.app.data.prefs.ShareDefaultBehavior
import com.uploadgo.app.data.zip.ZipStatus
import com.uploadgo.app.ui.components.rememberShareUiController
import com.uploadgo.app.util.Format
import kotlinx.coroutines.launch

/**
 * The Queue represents what UploadGo has prepared — files ready to share, ZIP
 * extraction tasks, and recently shared batches. It deliberately shows no fake
 * "upload progress": the receiving app controls the actual transfer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(
    onOpenHome: () -> Unit,
    onOpenZip: (String) -> Unit,
) {
    val store = AppGraph.sessionStore
    val picked by store.picked.collectAsStateWithLifecycle()
    val includedIds by store.included.collectAsStateWithLifecycle()
    val zipStates by store.zipStates.collectAsStateWithLifecycle()
    val settings by AppGraph.settingsRepository.settings.collectAsStateWithLifecycle(
        com.uploadgo.app.data.prefs.Settings()
    )
    val history by AppGraph.historyRepository.entries.collectAsStateWithLifecycle(initialValue = emptyList())

    val includedItems = picked.filter { it.id in includedIds }
    val zips = picked.filter { it.isZip }
    val recent = history.take(5)

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val shareController = rememberShareUiController { msg ->
        scope.launch { snackbarHostState.showSnackbar(msg) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.queue)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ---- Ready to share ----
            item {
                SectionTitle(stringResource(R.string.ready_to_share))
            }
            if (includedItems.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = stringResource(R.string.queue_empty_title),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.queue_empty_body),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = onOpenHome) {
                                Text(stringResource(R.string.select_files))
                            }
                        }
                    }
                }
            } else {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = stringResource(R.string.files_ready, includedItems.size),
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = Format.bytes(includedItems.sumOf { it.sizeBytes.coerceAtLeast(0) }),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            includedItems.take(6).forEach { item ->
                                Row(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Rounded.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = item.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        text = Format.bytes(item.sizeBytes),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (includedItems.size > 6) {
                                Text(
                                    text = stringResource(R.string.selected_count, includedItems.size),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    shareController.launch {
                                        val useContents =
                                            settings.defaultBehavior == ShareDefaultBehavior.SHARE_ZIP_CONTENTS &&
                                                includedItems.any { it.isZip }
                                        if (useContents) AppGraph.shareService.prepareShareZipContents()
                                        else AppGraph.shareService.prepareShareSelected()
                                    }
                                },
                                enabled = !shareController.preparing,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Rounded.Share, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.share))
                            }
                        }
                    }
                }
            }

            // ---- ZIP extraction ----
            item {
                SectionTitle(stringResource(R.string.zip_tasks))
            }
            if (zips.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.no_zip_tasks),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(zips, key = { it.id }) { zip ->
                    val state = zipStates[zip.id]
                    Card(
                        onClick = { onOpenZip(zip.id) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Rounded.FolderZip,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = zip.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                when (val s = state?.status) {
                                    is ZipStatus.Ready -> Text(
                                        text = stringResource(R.string.zip_files_and_media, s.totalEntries, s.supportedCount),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    is ZipStatus.Extracting -> Text(
                                        text = stringResource(R.string.zip_extracting),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    is ZipStatus.Error -> Text(
                                        text = stringResource(R.string.zip_error_title),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                    null -> Text(
                                        text = stringResource(R.string.type_zip),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            when (state?.status) {
                                is ZipStatus.Extracting -> CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                )
                                is ZipStatus.Ready -> Icon(
                                    Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                is ZipStatus.Error -> Icon(
                                    Icons.Rounded.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                                else -> Icon(
                                    Icons.Rounded.History,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            // ---- Recently shared ----
            if (recent.isNotEmpty()) {
                item {
                    SectionTitle(stringResource(R.string.recently_shared))
                }
                items(recent, key = { "h${it.id}" }) { entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = if (entry.type == HistoryType.SHARED.name) {
                                    stringResource(R.string.files_shared, entry.itemCount)
                                } else {
                                    stringResource(R.string.files_prepared, entry.itemCount)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = entry.category,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = Format.time(entry.timestamp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}
