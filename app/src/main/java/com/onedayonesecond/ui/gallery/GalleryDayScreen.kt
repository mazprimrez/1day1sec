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
import androidx.compose.material3.Text
import com.onedayonesecond.ui.theme.AppType
import com.onedayonesecond.ui.theme.BusyLine
import com.onedayonesecond.ui.theme.InlineAlert
import com.onedayonesecond.ui.theme.LineIcons
import com.onedayonesecond.ui.theme.SmallText
import com.onedayonesecond.ui.theme.StateCard
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
import com.onedayonesecond.ui.theme.Corners
import com.onedayonesecond.ui.theme.Palette
import com.onedayonesecond.ui.theme.QuietButton
import com.onedayonesecond.ui.theme.ScreenPadding
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
        BackBar(onBack, title = "Videos from this day")
        Column(
            modifier = Modifier.weight(1f).padding(horizontal = ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SmallText(RecapLabels.fullDate(state.date), Modifier.padding(top = 4.dp))
            when {
                state.loading -> Column(Modifier.weight(1f).fillMaxWidth()) {
                    MetaText("Looking through your gallery…", Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    BusyLine(Modifier.padding(top = 12.dp))
                }

                state.videos.isEmpty() -> Column(Modifier.weight(1f).fillMaxWidth()) {
                    StateCard("No videos from this day") {
                        BodyText("Nothing filmed on this day was found on your phone. You can still record a second for it now.")
                    }
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

            state.error?.let {
                InlineAlert(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, icon = LineIcons.Warning)
            }
        }

        QuietButton(
            "Record a new second instead",
            onRecordInstead,
            Modifier.fillMaxWidth().padding(horizontal = ScreenPadding, vertical = 12.dp),
            enabled = state.importingId == null,
            icon = LineIcons.Camera,
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
            .clip(Corners.Frame)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "Video from $time, $length long" },
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(PHOTO_ASPECT).clip(Corners.Frame).background(Palette.Blush)) {
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
        // Under the picture rather than over it, as a frame's number is: no dark band on the footage.
        Text(
            "$time \u00B7 $length",
            style = AppType.FrameNumber,
            color = Palette.Ink,
            maxLines = 1,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

private fun Long.asClock(): String {
    val totalSeconds = this / 1000
    return String.format(Locale.US, "%d:%02d", totalSeconds / 60, totalSeconds % 60)
}
