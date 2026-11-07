package com.onedayonesecond.ui.gallery

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onedayonesecond.media.GalleryVideo
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val DayTitleFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
private val TimeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryDayScreen(
    onPicked: (File) -> Unit,
    onRecordInstead: () -> Unit,
    onBack: () -> Unit,
    viewModel: GalleryDayViewModel = viewModel(factory = GalleryDayViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Keyed on the file so it fires once per imported video.
    LaunchedEffect(state.readyFile) {
        val file = state.readyFile ?: return@LaunchedEffect
        viewModel.readyFileConsumed()
        onPicked(file)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.date.format(DayTitleFormat)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                state.loading -> Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                    CircularProgressIndicator()
                }

                state.videos.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                    Text("No videos from this day were found on your phone.")
                }

                else -> {
                    Text(
                        text = "${state.videos.size} video${if (state.videos.size == 1) "" else "s"} " +
                            "from this day. Tap one to choose your second from it.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        items(state.videos, key = { it.id }) { video ->
                            VideoTile(
                                video = video,
                                importing = state.importingId == video.id,
                                enabled = state.importingId == null,
                                thumbnail = { viewModel.thumbnail(video) },
                                onClick = { viewModel.pick(video) },
                            )
                        }
                    }
                }
            }

            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            OutlinedButton(
                onClick = onRecordInstead,
                enabled = state.importingId == null,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            ) { Text("Record a new second instead") }
        }
    }
}

@Composable
private fun VideoTile(
    video: GalleryVideo,
    importing: Boolean,
    enabled: Boolean,
    thumbnail: suspend () -> android.graphics.Bitmap?,
    onClick: () -> Unit,
) {
    val image by produceState<ImageBitmap?>(initialValue = null, video.id) {
        value = thumbnail()?.asImageBitmap()
    }

    Box(
        modifier = Modifier
            .aspectRatio(0.75f)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        image?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 6.dp, vertical = 3.dp),
        ) {
            Text(
                text = Instant.ofEpochMilli(video.takenAtMs)
                    .atZone(ZoneId.systemDefault()).toLocalTime().format(TimeFormat),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                text = video.durationMs.asClock(),
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelSmall,
            )
        }

        if (importing) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(Modifier.size(32.dp), color = Color.White)
            }
        }
    }
}

private fun Long.asClock(): String {
    val totalSeconds = this / 1000
    return String.format(Locale.US, "%d:%02d", totalSeconds / 60, totalSeconds % 60)
}
