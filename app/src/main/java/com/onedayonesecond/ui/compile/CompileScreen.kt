package com.onedayonesecond.ui.compile

import android.net.Uri
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
fun CompileScreen(
    onBack: () -> Unit,
    viewModel: CompileViewModel = viewModel(factory = CompileViewModel.Factory),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    var showStyleDialog by remember { mutableStateOf(false) }

    val player = remember { ExoPlayer.Builder(context).build() }
    DisposableEffect(Unit) { onDispose { player.release() } }

    LaunchedEffect(state.reelFile) {
        val reel = state.reelFile
        if (reel == null) {
            player.clearMediaItems()
        } else {
            player.setMediaItem(MediaItem.fromUri(Uri.fromFile(reel)))
            player.prepare()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Compile reel") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RangeOption.entries.forEach { option ->
                    FilterChip(
                        selected = state.range == option,
                        onClick = { viewModel.selectRange(option) },
                        enabled = state.phase != CompilePhase.COMPILING,
                        label = { Text(option.label) },
                    )
                }
            }

            Text(
                text = "${state.clipCount} clip${if (state.clipCount == 1) "" else "s"} " +
                    "· ${state.clipCount} second${if (state.clipCount == 1) "" else "s"} of reel",
                style = MaterialTheme.typography.bodyMedium,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Show date on each second",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = state.showDates,
                    onCheckedChange = viewModel::setShowDates,
                    enabled = state.phase != CompilePhase.COMPILING,
                )
            }

            if (state.showDates) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${state.dateStyle.font.label} · ${state.dateStyle.edge.label} " +
                            state.dateStyle.align.label.lowercase(),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = { showStyleDialog = true },
                        enabled = state.phase != CompilePhase.COMPILING,
                    ) { Text("Change style") }
                }
            }

            if (state.reelFile != null) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    AndroidView(
                        factory = {
                            PlayerView(it).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                )
                                this.player = player
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            } else {
                Box(Modifier.weight(1f))
            }

            if (state.phase == CompilePhase.COMPILING) {
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Stitching ${state.clipCount} clips… ${(state.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            state.exportedName?.let {
                Text("Saved to Movies/1day1sec/$it", style = MaterialTheme.typography.bodySmall)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                when (state.phase) {
                    CompilePhase.COMPILING -> OutlinedButton(
                        onClick = viewModel::cancel,
                        modifier = Modifier.weight(1f),
                    ) { Text("Cancel") }

                    else -> OutlinedButton(
                        onClick = viewModel::compile,
                        enabled = state.clipCount > 0,
                        modifier = Modifier.weight(1f),
                    ) { Text(if (state.reelFile == null) "Build reel" else "Rebuild") }
                }

                Button(
                    onClick = viewModel::export,
                    enabled = state.phase == CompilePhase.READY || state.phase == CompilePhase.EXPORTED,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (state.phase == CompilePhase.EXPORTING) "Saving…" else "Save to gallery")
                }
            }
        }
    }

    if (showStyleDialog) {
        DateStyleDialog(
            current = state.dateStyle,
            onApply = {
                viewModel.setDateStyle(it)
                showStyleDialog = false
            },
            onDismiss = { showStyleDialog = false },
        )
    }
}
