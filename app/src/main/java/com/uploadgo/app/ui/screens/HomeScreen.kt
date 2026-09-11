package com.uploadgo.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Deselect
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ViewList
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uploadgo.app.AppGraph
import com.uploadgo.app.R
import com.uploadgo.app.data.model.MediaItem
import com.uploadgo.app.data.model.MediaKind
import com.uploadgo.app.data.prefs.ShareDefaultBehavior
import com.uploadgo.app.data.zip.ZipStatus
import com.uploadgo.app.ui.components.MediaThumbnail
import com.uploadgo.app.ui.components.rememberShareUiController
import com.uploadgo.app.util.Format
import com.uploadgo.app.util.MimeTypes
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenImage: (String) -> Unit,
    onOpenVideo: (String) -> Unit,
    onOpenZip: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val viewModel: HomeViewModel = viewModel()
    val picked by viewModel.picked.collectAsStateWithLifecycle()
    val includedIds by viewModel.included.collectAsStateWithLifecycle()
    val zipStates by viewModel.zipStates.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle(
        com.uploadgo.app.data.prefs.Settings()
    )
    val skipped by viewModel.skipped.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var grid by rememberSaveable { mutableStateOf(true) }

    val includedItems = picked.filter { it.id in includedIds }
    val hasZip = includedItems.any { it.isZip }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris -> viewModel.onPicked(uris) },
    )

    LaunchedEffect(skipped) {
        if (skipped > 0) {
            val msg = if (skipped == 1) {
                stringResource(R.string.unsupported_file_skipped)
            } else {
                stringResource(R.string.unsupported_files_skipped, skipped)
            }
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeSkipped()
        }
    }

    val shareController = rememberShareUiController { msg ->
        scope.launch { snackbarHostState.showSnackbar(msg) }
    }
    val service = AppGraph.shareService
    val primaryIsContents = settings.defaultBehavior == ShareDefaultBehavior.SHARE_ZIP_CONTENTS

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = stringResource(R.string.tagline),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            Icons.Rounded.Settings,
                            contentDescription = stringResource(R.string.settings),
                        )
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
            // ---- Select files (hero when empty, compact when files exist) ----
            if (picked.isEmpty()) {
                SelectFilesHero(onClick = { picker.launch(MimeTypes.PICKER_TYPES) })
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = { picker.launch(MimeTypes.PICKER_TYPES) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.select_files))
                    }
                }
            }

            // ---- Selected files header ----
            AnimatedVisibility(
                visible = picked.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.selected_files),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = stringResource(R.string.selected_count_files, includedItems.size),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { grid = false }) {
                            Icon(
                                Icons.Rounded.ViewList,
                                contentDescription = stringResource(R.string.list_view),
                                tint = if (grid) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.primary,
                            )
                        }
                        IconButton(onClick = { grid = true }) {
                            Icon(
                                Icons.Rounded.GridView,
                                contentDescription = stringResource(R.string.grid_view),
                                tint = if (grid) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                    ) {
                        TextButton(onClick = { viewModel.selectAll() }) {
                            Icon(Icons.Rounded.SelectAll, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.select_all))
                        }
                        TextButton(onClick = { viewModel.clearAll() }) {
                            Icon(Icons.Rounded.Deselect, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.clear_all))
                        }
                    }
                }
            }

            // ---- Grid / list of selected files ----
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (picked.isEmpty()) {
                    EmptyHomeHint()
                } else {
                    LazyVerticalGrid(
                        columns = if (grid) GridCells.Adaptive(minSize = 108.dp) else GridCells.Fixed(1),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(picked, key = { it.id }) { item ->
                            val selected = item.id in includedIds
                            val zipState = if (item.isZip) zipStates[item.id] else null
                            MediaTile(
                                item = item,
                                selected = selected,
                                grid = grid,
                                zipStatus = zipState?.status,
                                onClick = {
                                    when (item.kind) {
                                        MediaKind.IMAGE -> onOpenImage(item.id)
                                        MediaKind.VIDEO -> onOpenVideo(item.id)
                                        MediaKind.ZIP -> onOpenZip(item.id)
                                        else -> Unit
                                    }
                                },
                                onToggle = { viewModel.toggleIncluded(item.id) },
                            )
                        }
                    }
                }
            }

            // ---- Share area ----
            AnimatedVisibility(visible = picked.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    if (hasZip) {
                        Button(
                            onClick = {
                                shareController.launch {
                                    if (primaryIsContents) service.prepareShareZipContents()
                                    else service.prepareShareSelected()
                                }
                            },
                            enabled = includedItems.isNotEmpty() && !shareController.preparing,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (shareController.preparing) {
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
                                Text(
                                    if (primaryIsContents) stringResource(R.string.share_zip_contents)
                                    else stringResource(R.string.share_selected)
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                shareController.launch {
                                    if (primaryIsContents) service.prepareShareSelected()
                                    else service.prepareShareZipContents()
                                }
                            },
                            enabled = includedItems.isNotEmpty() && !shareController.preparing,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Rounded.Share, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (primaryIsContents) stringResource(R.string.share_selected)
                                else stringResource(R.string.share_zip_contents)
                            )
                        }
                        if (includedItems.any { it.isZip }) {
                            TextButton(
                                onClick = { shareController.launch { service.prepareShareOriginalZips() } },
                                enabled = !shareController.preparing,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Rounded.FolderZip, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.share_original_zip))
                            }
                        }
                    } else {
                        Button(
                            onClick = { shareController.launch { service.prepareShareSelected() } },
                            enabled = includedItems.isNotEmpty() && !shareController.preparing,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (shareController.preparing) {
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
                                Text(stringResource(R.string.share))
                            }
                        }
                    }
                    Text(
                        text = stringResource(R.string.sharesheet_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectFilesHero(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(40.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.select_files),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.photos_videos_zip),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun EmptyHomeHint() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Rounded.FolderZip,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.no_files_selected),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.tap_select_files),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MediaTile(
    item: MediaItem,
    selected: Boolean,
    grid: Boolean,
    zipStatus: ZipStatus?,
    onClick: () -> Unit,
    onToggle: () -> Unit,
) {
    val typeLabel = when (item.kind) {
        MediaKind.IMAGE -> stringResource(R.string.type_image)
        MediaKind.VIDEO -> stringResource(R.string.type_video)
        MediaKind.ZIP -> stringResource(R.string.type_zip)
        else -> stringResource(R.string.type_file)
    }

    if (grid) {
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
                    ZipStatusBadge(item, zipStatus, Modifier.align(Alignment.BottomStart).padding(6.dp))
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
    } else {
        Card(
            onClick = onClick,
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(10.dp)),
                ) {
                    MediaThumbnail(item, Modifier.fillMaxSize())
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = item.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "$typeLabel • ${Format.bytes(item.sizeBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Checkbox(checked = selected, onCheckedChange = { onToggle() })
            }
        }
    }
}

@Composable
private fun ZipStatusBadge(item: MediaItem, status: ZipStatus?, modifier: Modifier = Modifier) {
    if (item.kind != MediaKind.ZIP) return

    val container = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
    val content = MaterialTheme.colorScheme.onSurface

    val text: String = when (status) {
        is ZipStatus.Ready -> stringResource(R.string.zip_files_and_media, status.totalEntries, status.supportedCount)
        is ZipStatus.Extracting -> stringResource(R.string.zip_extracting)
        is ZipStatus.Error -> stringResource(R.string.zip_error_title)
        else -> stringResource(R.string.type_zip) // NotStarted or not yet known
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(container)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (status is ZipStatus.Extracting) {
            CircularProgressIndicator(modifier = Modifier.size(10.dp), strokeWidth = 1.5.dp)
            Spacer(Modifier.width(6.dp))
        } else if (status is ZipStatus.Ready) {
            Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = content,
            maxLines = 1,
        )
    }
}
