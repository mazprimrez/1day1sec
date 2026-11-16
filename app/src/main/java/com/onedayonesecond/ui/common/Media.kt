package com.onedayonesecond.ui.common

import android.graphics.BitmapFactory
import android.net.Uri
import android.view.TextureView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.onedayonesecond.OneDayApp
import com.onedayonesecond.data.ClipEntity
import com.onedayonesecond.ui.theme.LineIcons
import com.onedayonesecond.ui.theme.Palette
import com.onedayonesecond.ui.theme.TileShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Clips are 9:16 portrait; photos and pen pages are shown 3:4. */
const val CLIP_ASPECT = 9f / 16f
const val PHOTO_ASPECT = 3f / 4f

/**
 * A still frame from a day's clip, or null while it loads or if it cannot be read.
 * Keyed on the recording, not just the day: retaking a day replaces the clip, and the
 * old picture must not linger.
 */
@UnstableApi
@Composable
fun rememberClipThumbnail(clip: ClipEntity?): ImageBitmap? {
    val context = LocalContext.current
    val thumbnails = remember { (context.applicationContext as OneDayApp).container.clipThumbnails }
    val thumbnail by produceState<ImageBitmap?>(initialValue = null, clip?.filePath, clip?.createdAt) {
        value = clip?.let { thumbnails.load(it)?.asImageBitmap() }
    }
    return thumbnail
}

/** A clip as a still picture filling its box, on blush until it has loaded. Decorative. */
@UnstableApi
@Composable
fun ClipStill(clip: ClipEntity?, modifier: Modifier = Modifier, overlay: @Composable BoxScope.() -> Unit = {}) {
    val picture = rememberClipThumbnail(clip)
    Box(modifier.clip(TileShape).background(if (clip != null) Palette.Blush else Palette.Paper)) {
        if (picture != null) {
            Image(picture, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        overlay()
    }
}

/**
 * A day's second, playable in place: it shows its first frame, and a tap plays it once
 * with sound. Drawn on a TextureView, which, unlike a player's usual surface, can be
 * given rounded corners.
 */
@UnstableApi
@Composable
fun ClipPlayer(file: File, version: Long, label: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var playing by remember(file, version) { mutableStateOf(false) }
    val player = remember(file, version) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            playWhenReady = false
            prepare()
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED) {
                        // Back to the first frame, ready to be played again.
                        seekTo(0)
                        pause()
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    playing = isPlaying
                }
            })
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }

    Box(
        modifier
            .aspectRatio(CLIP_ASPECT)
            .clip(TileShape)
            .background(Palette.Blush)
            .clickable(role = Role.Button, onClickLabel = label) {
                if (player.isPlaying) {
                    player.pause()
                } else {
                    if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
                    player.play()
                }
            },
    ) {
        AndroidView(
            factory = { TextureView(it) },
            update = { view -> player.setVideoTextureView(view) },
            modifier = Modifier.fillMaxSize(),
        )
        if (!playing) {
            Box(
                Modifier.align(Alignment.Center).size(48.dp).clip(CircleShape).background(Palette.Paper.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(LineIcons.Play, contentDescription = label, tint = Palette.Ink)
            }
        }
    }
}

/** A photo or pen page from the journal, scaled down while decoding so a row of them stays cheap. */
@Composable
fun rememberJournalImage(file: File?, maxSidePx: Int = 512): ImageBitmap? {
    val image by produceState<ImageBitmap?>(initialValue = null, file?.absolutePath, maxSidePx) {
        value = file?.takeIf { it.exists() }?.let { source ->
            withContext(Dispatchers.IO) {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(source.absolutePath, bounds)
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSidePx) sample *= 2
                BitmapFactory.decodeFile(
                    source.absolutePath,
                    BitmapFactory.Options().apply { inSampleSize = sample },
                )?.asImageBitmap()
            }
        }
    }
    return image
}

/** A journal photo filling its box, on blush until loaded. [description] is read aloud; null hides it. */
@Composable
fun JournalImage(
    file: File?,
    description: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    maxSidePx: Int = 512,
) {
    val image = rememberJournalImage(file, maxSidePx)
    Box(modifier.clip(TileShape).background(Palette.Blush)) {
        if (image != null) {
            Image(image, contentDescription = description, contentScale = contentScale, modifier = Modifier.fillMaxSize())
        }
    }
}

