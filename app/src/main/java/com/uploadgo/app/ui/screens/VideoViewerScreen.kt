package com.uploadgo.app.ui.screens

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.uploadgo.app.AppGraph
import com.uploadgo.app.R
import com.uploadgo.app.util.Format

/**
 * Video preview backed by ExoPlayer/Media3. Playback streams from the content
 * URI — videos are never decoded fully into memory or re-encoded. Provides
 * play/pause, seek, duration, and a full-screen toggle.
 */
@Composable
fun VideoViewerScreen(
    itemId: String,
    onBack: () -> Unit,
) {
    val item = AppGraph.sessionStore.itemById(itemId)
    val context = LocalContext.current

    if (item == null) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            IconButton(onClick = onBack, modifier = Modifier.statusBarsPadding()) {
                Icon(
                    Icons.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.close),
                    tint = Color.White,
                )
            }
        }
        return
    }

    val player = remember(item.uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(item.uri))
            prepare()
            playWhenReady = true
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> player.pause()
                Lifecycle.Event.ON_RESUME -> player.play()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.release()
        }
    }

    var fullscreen by remember { mutableStateOf(false) }

    fun setFullscreen(enabled: Boolean) {
        fullscreen = enabled
        val window = (context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        if (enabled) {
            controller?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (!fullscreen) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.close),
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = item.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = Format.bytes(item.sizeBytes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { setFullscreen(true) }) {
                    Icon(
                        Icons.Rounded.Fullscreen,
                        contentDescription = stringResource(R.string.fullscreen),
                    )
                }
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        // The built-in controller shows play/pause, seek and duration.
                        this.player = player
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )

            if (fullscreen) {
                IconButton(
                    onClick = { setFullscreen(false) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(8.dp),
                ) {
                    Icon(
                        Icons.Rounded.FullscreenExit,
                        contentDescription = stringResource(R.string.exit_fullscreen),
                        tint = Color.White,
                    )
                }
            }
        }
    }
}
