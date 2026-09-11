package com.uploadgo.app.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.uploadgo.app.AppGraph
import com.uploadgo.app.data.model.MediaItem
import com.uploadgo.app.data.model.MediaKind

/**
 * Renders a lightweight thumbnail for any supported item. Full-resolution media
 * is never decoded into the UI: images go through Coil's downsampling and video
 * frames are capped at 512px.
 */
@Composable
fun MediaThumbnail(
    item: MediaItem,
    modifier: Modifier = Modifier,
) {
    when (item.kind) {
        MediaKind.IMAGE -> AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(item.uri)
                .crossfade(true)
                .build(),
            contentDescription = item.displayName,
            contentScale = ContentScale.Crop,
            modifier = modifier.fillMaxSize(),
        )

        MediaKind.VIDEO -> VideoThumbnail(item, modifier)

        MediaKind.ZIP -> GenericThumb(
            icon = { Icon(Icons.Rounded.FolderZip, contentDescription = null) },
            modifier = modifier,
        )

        MediaKind.UNSUPPORTED -> GenericThumb(
            icon = { Icon(Icons.Rounded.InsertDriveFile, contentDescription = null) },
            modifier = modifier,
        )
    }
}

@Composable
private fun VideoThumbnail(item: MediaItem, modifier: Modifier) {
    var bitmap by remember(item.uri) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(item.uri) {
        bitmap = AppGraph.mediaRepository.videoThumbnail(item.uri)
    }

    val bmp = bitmap
    Box(modifier = modifier) {
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = item.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            GenericThumb(
                icon = { Icon(Icons.Rounded.Movie, contentDescription = null) },
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Play affordance
        Icon(
            imageVector = Icons.Rounded.PlayArrow,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.Center)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                    shape = MaterialTheme.shapes.large,
                ),
        )
    }
}

@Composable
private fun GenericThumb(
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        icon()
    }
}
