package com.uploadgo.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.uploadgo.app.AppGraph
import com.uploadgo.app.R
import com.uploadgo.app.data.model.MediaKind
import com.uploadgo.app.data.model.MediaItem
import com.uploadgo.app.util.Format

/**
 * Full-screen image viewer with pinch-to-zoom, pan and horizontal swipe between
 * the currently selected images. Opening a preview never triggers sharing.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageViewerScreen(
    itemId: String,
    onBack: () -> Unit,
) {
    val items = AppGraph.sessionStore.includedPickedItems()
        .filter { it.kind == MediaKind.IMAGE }
    val initialPage = items.indexOfFirst { it.id == itemId }.coerceAtLeast(0)

    if (items.isEmpty()) {
        // Fallback: show just this single image.
        SingleImageViewer(itemId = itemId, onBack = onBack)
        return
    }

    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { items.size })
    var chromeVisible by remember { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            ZoomableImage(
                item = items[page],
                onSingleTap = { chromeVisible = !chromeVisible },
            )
        }

        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            val current = items.getOrNull(pagerState.currentPage) ?: items.first()
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                androidx.compose.foundation.layout.Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = androidx.compose.ui.res.stringResource(R.string.close),
                        )
                    }
                    Column(Modifier.padding(start = 4.dp)) {
                        Text(
                            text = current.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                        )
                        Text(
                            text = Format.bytes(current.sizeBytes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SingleImageViewer(itemId: String, onBack: () -> Unit) {
    val item = AppGraph.sessionStore.itemById(itemId)
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (item != null) {
            ZoomableImage(item = item, onSingleTap = { })
        }
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .statusBarsPadding()
                .padding(4.dp),
        ) {
            Icon(Icons.Rounded.Close, contentDescription = androidx.compose.ui.res.stringResource(R.string.close))
        }
    }
}

@Composable
private fun ZoomableImage(
    item: MediaItem,
    onSingleTap: () -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    fun clampOffset(value: Offset, s: Float, size: IntSize): Offset {
        if (s <= 1f || size.width == 0 || size.height == 0) return Offset.Zero
        val maxX = (size.width * (s - 1f)) / 2f
        val maxY = (size.height * (s - 1f)) / 2f
        return Offset(
            x = value.x.coerceIn(-maxX, maxX),
            y = value.y.coerceIn(-maxY, maxY),
        )
    }

    AsyncImage(
        model = item.uri,
        contentDescription = item.displayName,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val oldScale = scale
                    val newScale = (oldScale * zoom).coerceIn(1f, 5f)
                    offset = if (newScale <= 1f) {
                        Offset.Zero
                    } else {
                        val base = if (oldScale <= 1f) Offset.Zero else offset
                        ((base + centroid) * (newScale / oldScale) - centroid) + pan
                    }
                    scale = newScale
                    offset = clampOffset(offset, scale, containerSize)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onSingleTap() },
                    onDoubleTap = { _ ->
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                            offset = Offset.Zero
                        }
                    },
                )
            },
    )
}
