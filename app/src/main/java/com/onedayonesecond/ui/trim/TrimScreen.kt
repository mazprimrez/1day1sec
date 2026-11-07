package com.onedayonesecond.ui.trim

import android.net.Uri
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
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

    // Local slider position so dragging stays smooth; the window is only
    // committed (and the preview rebuilt) when the user lets go.
    var sliderPosition by remember { mutableFloatStateOf(0f) }

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding(),
    ) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                factory = {
                    PlayerView(it).apply {
                        setUseController(false)
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
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(Modifier.size(48.dp))
                        Text("Trimming…", color = Color.White, modifier = Modifier.padding(top = 12.dp))
                    }
                }
            }
        }

        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Keeping ${state.startMs.asSeconds()} – ${state.endMs.asSeconds()} of " +
                    state.durationMs.asSeconds(),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
            )

            Slider(
                value = sliderPosition,
                onValueChange = { sliderPosition = it },
                onValueChangeFinished = { viewModel.setStart(sliderPosition.toLong()) },
                valueRange = 0f..state.maxStartMs.coerceAtLeast(1).toFloat(),
                enabled = state.maxStartMs > 0 && !state.isProcessing,
            )

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onBack,
                    enabled = !state.isProcessing,
                    modifier = Modifier.weight(1f),
                ) { Text("Retake") }

                Button(
                    onClick = viewModel::confirm,
                    enabled = state.durationMs > 0 && !state.isProcessing,
                    modifier = Modifier.weight(1f),
                ) { Text("Use this second") }
            }
        }
    }
}

private fun Long.asSeconds(): String =
    String.format(Locale.US, "%.1fs", this / 1000f)
