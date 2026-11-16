package com.onedayonesecond.ui.trim

import android.net.Uri
import android.view.ViewGroup
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.onedayonesecond.data.Captions
import com.onedayonesecond.journal.RecapLabels
import com.onedayonesecond.media.VideoSpec
import com.onedayonesecond.ui.common.CLIP_ASPECT
import com.onedayonesecond.ui.theme.AppType
import com.onedayonesecond.ui.theme.BackBar
import com.onedayonesecond.ui.theme.BodyText
import com.onedayonesecond.ui.theme.MetaText
import com.onedayonesecond.ui.theme.Palette
import com.onedayonesecond.ui.theme.QuietButton
import com.onedayonesecond.ui.theme.RoseButton
import com.onedayonesecond.ui.theme.ScreenPadding
import com.onedayonesecond.ui.theme.TileShape
import com.onedayonesecond.ui.theme.appTextFieldColors
import java.util.Locale

@UnstableApi
@Composable
fun TrimScreen(
    onSaved: () -> Unit,
    onBack: () -> Unit,
    viewModel: TrimViewModel = viewModel(factory = TrimViewModel.Factory),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = true
        }
    }
    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    LaunchedEffect(state.startMs, state.durationMs) {
        if (state.durationMs <= 0L) return@LaunchedEffect
        player.setMediaItem(
            MediaItem.Builder()
                .setUri(Uri.fromFile(state.sourceFile))
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(state.startMs)
                        .setEndPositionMs(state.endMs)
                        .build(),
                )
                .build(),
        )
        player.prepare()
        player.play()
    }

    LaunchedEffect(state.saved) {
        if (state.saved) onSaved()
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        BackBar(onBack, backLabel = "Back to retake")

        // The preview is the portrait frame the second will be saved in: a wider
        // recording is cropped here exactly as it will be when saved.
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = ScreenPadding), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxHeight().aspectRatio(CLIP_ASPECT, matchHeightConstraintsFirst = true).clip(TileShape)) {
                AndroidView(
                    factory = {
                        PlayerView(it).apply {
                            setUseController(false)
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            setShutterBackgroundColor(Palette.Blush.toArgb())
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            this.player = player
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                if (state.isProcessing) {
                    Box(
                        Modifier.fillMaxSize().background(Palette.Paper.copy(alpha = 0.8f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(Modifier.size(40.dp), color = Palette.Rose, strokeWidth = 2.dp)
                            BodyText(
                                "Saving this second…",
                                Modifier.padding(top = 12.dp).semantics { liveRegion = LiveRegionMode.Polite },
                            )
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier.padding(horizontal = ScreenPadding).padding(top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MetaText(
                "${RecapLabels.weekdayAndDay(state.date)}. Keeping ${state.startMs.asSeconds()} to " +
                    "${state.endMs.asSeconds()} of ${state.durationMs.asSeconds()} seconds.",
            )

            Filmstrip(
                state = state,
                enabled = state.maxStartMs > 0 && !state.isProcessing,
                onCommit = viewModel::setStart,
            )

            OutlinedTextField(
                value = state.caption,
                onValueChange = viewModel::setCaption,
                label = { Text("Caption (optional)") },
                // One line to type: where it wraps on the reel is decided when it is drawn.
                singleLine = true,
                enabled = !state.isProcessing,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                supportingText = { Text("${state.caption.length} of ${Captions.MAX_LENGTH}") },
                colors = appTextFieldColors(),
                textStyle = AppType.Body,
                modifier = Modifier.fillMaxWidth(),
            )

            state.error?.let {
                BodyText(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuietButton("Retake", onBack, Modifier.weight(1f), enabled = !state.isProcessing)
                RoseButton(
                    "Use this second",
                    viewModel::confirm,
                    Modifier.weight(1.4f),
                    enabled = state.durationMs > 0 && !state.isProcessing,
                )
            }
        }
    }
}

/**
 * The recording laid out as a strip of stills, with a rose window marking the second
 * that will be kept. Drag the window, or tap the strip to move it there. The preview
 * above only reloads when the finger lifts, so dragging stays smooth.
 */
@Composable
private fun Filmstrip(state: TrimUiState, enabled: Boolean, onCommit: (Long) -> Unit) {
    val duration = state.durationMs.coerceAtLeast(1)
    val maxStart = state.maxStartMs
    // Where the window is while it is being dragged; null means "where the state says".
    var dragging by remember { mutableStateOf<Float?>(null) }
    val startMs = dragging ?: state.startMs.toFloat()
    // The gesture handlers below outlive a recomposition; this is how they see the
    // position the window was last left at rather than the one from when they started.
    val committed by rememberUpdatedState(state.startMs.toFloat())
    val frames = remember(state.frames) { state.frames.map { it.asImageBitmap() } }

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(TileShape)
            .background(Palette.Blush)
            .semantics {
                contentDescription = "Which second to keep"
                stateDescription =
                    "${(startMs / 1000f).oneDecimal()} to ${((startMs + VideoSpec.CLIP_DURATION_MS) / 1000f).oneDecimal()} seconds"
                progressBarRangeInfo = ProgressBarRangeInfo(startMs, 0f..maxStart.toFloat().coerceAtLeast(1f))
                if (enabled) {
                    setProgress { target ->
                        onCommit(target.toLong().coerceIn(0, maxStart))
                        true
                    }
                }
            }
            .then(
                if (!enabled) {
                    Modifier
                } else {
                    Modifier
                        .pointerInput(duration, maxStart) {
                            detectTapGestures { tap ->
                                // Centre the window on the tap.
                                val centre = tap.x / size.width * duration
                                onCommit((centre - VideoSpec.CLIP_DURATION_MS / 2f).toLong().coerceIn(0, maxStart))
                            }
                        }
                        .pointerInput(duration, maxStart) {
                            detectHorizontalDragGestures(
                                onDragStart = { dragging = committed },
                                onDragEnd = {
                                    dragging?.let { onCommit(it.toLong()) }
                                    dragging = null
                                },
                                onDragCancel = { dragging = null },
                            ) { change, dx ->
                                change.consume()
                                val moved = (dragging ?: committed) + dx / size.width * duration
                                dragging = moved.coerceIn(0f, maxStart.toFloat())
                            }
                        }
                },
            ),
    ) {
        Row(Modifier.fillMaxSize()) {
            frames.forEach { frame ->
                Image(
                    frame,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }

        val total = maxWidth
        val windowWidth = total * (VideoSpec.CLIP_DURATION_MS.toFloat() / duration).coerceIn(0f, 1f)
        val windowStart = total * (startMs / duration).coerceIn(0f, 1f)

        // Everything outside the window is washed out, so the kept second stands out.
        Box(Modifier.width(windowStart).fillMaxHeight().background(Palette.Paper.copy(alpha = 0.65f)))
        Box(
            Modifier
                .offset(x = windowStart + windowWidth)
                .width((total - windowStart - windowWidth).coerceAtLeast(0.dp))
                .fillMaxHeight()
                .background(Palette.Paper.copy(alpha = 0.65f)),
        )
        Box(
            Modifier
                .offset(x = windowStart)
                .width(windowWidth.coerceAtLeast(6.dp))
                .fillMaxHeight()
                .border(2.dp, Palette.Rose, RoundedCornerShape(6.dp)),
        )
    }
}

private fun Float.oneDecimal(): String = String.format(Locale.US, "%.1f", this)

private fun Long.asSeconds(): String = (this / 1000f).oneDecimal()
