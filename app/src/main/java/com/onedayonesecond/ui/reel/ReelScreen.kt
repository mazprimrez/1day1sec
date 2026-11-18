package com.onedayonesecond.ui.reel

import com.onedayonesecond.ui.theme.Stat
import com.onedayonesecond.ui.backup.BackupLabels
import com.onedayonesecond.ui.compile.PREVIEW_WIDTH
import com.onedayonesecond.ui.theme.IconAction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import com.onedayonesecond.journal.RecapLabels
import com.onedayonesecond.ui.theme.ContactFrame
import com.onedayonesecond.ui.theme.Corners
import com.onedayonesecond.ui.theme.FabClearance
import com.onedayonesecond.ui.theme.FloatingAction
import com.onedayonesecond.ui.theme.FrameKind
import com.onedayonesecond.ui.theme.InlineAlert
import com.onedayonesecond.ui.theme.LineIcons
import com.onedayonesecond.ui.theme.ProgressLine
import com.onedayonesecond.ui.theme.Segmented
import com.onedayonesecond.ui.theme.TabHeader
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.onedayonesecond.ui.backup.BackupPhase
import com.onedayonesecond.ui.backup.BackupViewModel
import com.onedayonesecond.ui.common.CLIP_ASPECT
import com.onedayonesecond.ui.common.ClipPlayer
import com.onedayonesecond.ui.common.ClipStill
import com.onedayonesecond.ui.compile.CompilePhase
import com.onedayonesecond.ui.compile.CompileUiState
import com.onedayonesecond.ui.compile.CompileViewModel
import com.onedayonesecond.ui.compile.RangeOption
import com.onedayonesecond.ui.theme.ActionRow
import com.onedayonesecond.ui.theme.AppType
import com.onedayonesecond.ui.theme.Chip
import com.onedayonesecond.ui.theme.MediaPlaceholder
import com.onedayonesecond.ui.theme.MetaText
import com.onedayonesecond.ui.theme.Palette
import com.onedayonesecond.ui.theme.ScreenPadding

/**
 * The Reel tab: choose a range, see what is in it, and make the reel. What text goes on
 * each second and the backup to Google Drive each have a page of their own, one tap away.
 *
 * Both view models are the activity's, so the reel's settings are the same here as on
 * their settings page, and a backup goes on while this tab is shown.
 */
@UnstableApi
@Composable
fun ReelScreen(
    onSettings: () -> Unit,
    onTextSettings: () -> Unit,
    onBackup: () -> Unit,
    compile: CompileViewModel,
    backup: BackupViewModel,
) {
    val reel by compile.state.collectAsStateWithLifecycle()
    val drive by backup.state.collectAsStateWithLifecycle()
    // Seconds may have been recorded or deleted on another tab since this one was last seen.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        compile.refreshCount()
        backup.refreshCount()
    }

    val stitching = reel.phase == CompilePhase.COMPILING
    val saving = reel.phase == CompilePhase.EXPORTING
    val backingUp = drive.phase != BackupPhase.IDLE

    // Stitching a long reel takes minutes, and a backup is many uploads in a row. If
    // the screen turned off part-way the phone would slow the app down or cut the
    // network, so the screen is held on while either runs.
    val view = LocalView.current
    val busy = stitching || saving || backingUp
    DisposableEffect(busy) {
        view.keepScreenOn = busy
        onDispose { view.keepScreenOn = false }
    }

    // The day whose second is in the preview; null means the newest one in the range.
    var picked by remember(reel.range) { mutableStateOf<LocalDate?>(null) }
    val shown = reel.clips.firstOrNull { it.date == picked } ?: reel.previewClip
    val working = stitching || saving

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            val seconds = "${reel.clipCount} second${if (reel.clipCount == 1) "" else "s"}"
            TabHeader(
                "Reel",
                subtitle = when (reel.range) {
                    RangeOption.THIS_YEAR -> "$seconds of ${reel.rangeEnd.year} so far"
                    RangeOption.LAST_30_DAYS -> "$seconds from the last 30 days"
                    RangeOption.LAST_7_DAYS -> "$seconds from the last 7 days"
                },
            ) {
                IconAction(LineIcons.Settings, "Settings", onSettings)
            }

            // The preview, in portrait, beside the range's numbers.
            Row(
                Modifier.padding(horizontal = ScreenPadding).padding(top = 4.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Box(Modifier.width(PREVIEW_WIDTH)) {
                    val file = reel.reelFile
                    when {
                        file != null -> ClipPlayer(
                            file,
                            version = file.lastModified(),
                            label = "Play your reel",
                            shape = Corners.Preview,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        shown != null -> ClipPlayer(
                            shown.file,
                            version = shown.createdAt,
                            label = "Play the second of ${RecapLabels.dayAndMonth(shown.date)}",
                            shape = Corners.Preview,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        else -> MediaPlaceholder(Modifier.fillMaxWidth().aspectRatio(CLIP_ASPECT).clip(Corners.Preview))
                    }
                    // What is in the preview: the reel once it is made, otherwise one day's second.
                    val caption = when {
                        file != null -> "Your reel · $seconds"
                        shown != null -> RecapLabels.compact(shown.date) +
                            if (shown.caption.isEmpty()) "" else " · ${shown.caption}"
                        else -> null
                    }
                    if (caption != null) {
                        Text(
                            caption,
                            style = AppType.LabelSmall,
                            color = Palette.Paper,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(8.dp)
                                .clip(Corners.Chip)
                                .background(Palette.Ink.copy(alpha = 0.78f))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    val days = ChronoUnit.DAYS.between(reel.rangeStart, reel.rangeEnd).toInt() + 1
                    Stat("Kept", reel.clipCount.toString())
                    Stat("Missed", (days - reel.clipCount).coerceAtLeast(0).toString())
                    Stat("Length", "%02d:%02d".format(reel.clipCount / 60, reel.clipCount % 60))
                }
            }

            Timeline(
                reel = reel,
                selected = shown?.date,
                onPick = { picked = it },
                modifier = Modifier.padding(top = 16.dp),
            )

            Segmented(
                options = RangeOption.entries.map { it.label },
                selected = reel.range.ordinal,
                onSelect = { if (!working) compile.selectRange(RangeOption.entries[it]) },
                modifier = Modifier.padding(horizontal = ScreenPadding).padding(top = 12.dp),
            )

            if (working) {
                Column(Modifier.padding(horizontal = ScreenPadding).padding(top = 16.dp)) {
                    ProgressLine(if (saving) 1f else reel.progress)
                    MetaText(
                        if (saving) {
                            "Saving to Movies…"
                        } else {
                            "Stitching ${reel.clipCount} seconds, ${(reel.progress * 100).toInt()}%. " +
                                "Keep this tab open until it finishes."
                        },
                        Modifier.padding(top = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
            reel.error?.let {
                InlineAlert(
                    it,
                    Modifier.padding(horizontal = ScreenPadding).padding(top = 12.dp).semantics { liveRegion = LiveRegionMode.Polite },
                    icon = LineIcons.Warning,
                )
            }
            reel.exportedName?.let {
                InlineAlert(
                    "Your reel is saved to Movies/Kilasan as $it.",
                    Modifier.padding(horizontal = ScreenPadding).padding(top = 12.dp).semantics { liveRegion = LiveRegionMode.Polite },
                    icon = LineIcons.Check,
                )
            }

            Spacer(Modifier.height(12.dp))
            ActionRow(
                "Text on each second",
                onTextSettings,
                subtitle = reel.textSummary,
                icon = LineIcons.Text,
                enabled = !working,
            )
            ActionRow("Google Drive backup", onBackup, subtitle = BackupLabels.status(drive), icon = LineIcons.Cloud)
            Spacer(Modifier.height(FabClearance))
        }

        val fab = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 16.dp)
        when {
            stitching -> FloatingAction("Cancel", LineIcons.Close, compile::cancel, fab)
            saving -> Unit
            // The reel exists but saving it failed: only that step is tried again.
            reel.phase == CompilePhase.READY -> FloatingAction("Save to Movies", LineIcons.Download, compile::export, fab)
            reel.clipCount > 0 -> FloatingAction(
                if (reel.phase == CompilePhase.EXPORTED) "Make it again" else "Make my reel",
                LineIcons.Video,
                compile::makeReel,
                fab,
            )
        }
    }
}

/**
 * Every day of the range as a strip of frames, numbered by day of the year, newest at
 * the right-hand end where the strip opens. Tapping a kept day puts its second in the
 * preview.
 */
@UnstableApi
@Composable
private fun Timeline(
    reel: CompileUiState,
    selected: LocalDate?,
    onPick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val days = (ChronoUnit.DAYS.between(reel.rangeStart, reel.rangeEnd).toInt() + 1).coerceAtLeast(0)
    val byDate = remember(reel.clips) { reel.clips.associateBy { it.date } }
    val list = rememberLazyListState()
    // Open on the newest days, and go back there when the range changes.
    LaunchedEffect(reel.range, days) { if (days > 0) list.scrollToItem(days - 1) }

    LazyRow(
        state = list,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = ScreenPadding, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(count = days, key = { it }) { index ->
            val date = reel.rangeStart.plusDays(index.toLong())
            val clip = byDate[date]
            ContactFrame(
                kind = if (clip != null) FrameKind.Kept else FrameKind.Missed,
                label = RecapLabels.fullDate(date),
                stateLabel = if (clip != null) "Second kept" else "No second",
                number = date.dayOfYear.toString(),
                selected = clip != null && date == selected,
                frameHeight = 54.dp,
                onClick = if (clip != null) ({ onPick(date) }) else null,
                modifier = Modifier.width(40.dp),
            ) {
                ClipStill(clip, Modifier.fillMaxSize())
            }
        }
    }
}
