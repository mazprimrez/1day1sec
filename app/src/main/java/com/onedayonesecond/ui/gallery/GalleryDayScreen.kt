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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onedayonesecond.journal.RecapLabels
import com.onedayonesecond.media.GalleryVideo
import com.onedayonesecond.ui.common.PHOTO_ASPECT
import com.onedayonesecond.ui.theme.BackBar
import com.onedayonesecond.ui.theme.BodyText
import com.onedayonesecond.ui.theme.MetaText
import com.onedayonesecond.ui.theme.Palette
import com.onedayonesecond.ui.theme.QuietButton
import com.onedayonesecond.ui.theme.ScreenPadding
import com.onedayonesecond.ui.theme.TileShape
import com.onedayonesecond.ui.theme.TitleText
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val TimeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

/** One day's videos from the phone's gallery, to choose that day's second from. */
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

    Column(Modifier.fillMaxSize()) {
        BackBar(onBack)
        Column(
            modifier = Modifier.weight(1f).padding(horizontal = ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column {
                TitleText("Videos from this day")
                MetaText(RecapLabels.fullDate(state.date), Modifier.padding(top = 2.dp))
            }
            when {
                state.loading -> Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(32.dp), color = Palette.Rose, strokeWidth = 2.dp)
                }

                state.videos.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                    BodyText("No videos from this day were found on your phone.")
                }

                else -> {
                    MetaText(
                        "${state.videos.size} video${if (state.videos.size == 1) "" else "s"}. " +
                            "Tap one to choose your second from it.",
                    )
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
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

            state.error?.let { BodyText(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
        }

        QuietButton(
            "Record a new second instead",
            onRecordInstead,
            Modifier.fillMaxWidth().padding(horizontal = ScreenPadding, vertical = 12.dp),
            enabled = state.importingId == null,
        )
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
    val time = Instant.ofEpochMilli(video.takenAtMs).atZone(ZoneId.systemDefault()).toLocalTime().format(TimeFormat)
    val length = video.durationMs.asClock()

    Column(
        Modifier
            .clip(TileShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "Video from $time, $length long" },
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(PHOTO_ASPECT).clip(TileShape).background(Palette.Blush)) {
            image?.let {
                Image(
                    bitmap = it,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (importing) {
                Box(
                    Modifier.fillMaxSize().background(Palette.Paper.copy(alpha = 0.75f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(Modifier.size(28.dp), color = Palette.Rose, strokeWidth = 2.dp)
                }
            }
        }
        // Under the picture rather than over it: no dark band on the footage.
        MetaText("$time, $length", Modifier.padding(top = 4.dp), maxLines = 1)
    }
}

private fun Long.asClock(): String {
    val totalSeconds = this / 1000
    return String.format(Locale.US, "%d:%02d", totalSeconds / 60, totalSeconds % 60)
}
