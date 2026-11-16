package com.onedayonesecond.ui.reel

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import com.onedayonesecond.ui.backup.BackupAction
import com.onedayonesecond.ui.backup.BackupPhase
import com.onedayonesecond.ui.backup.BackupUiState
import com.onedayonesecond.ui.backup.BackupViewModel
import com.onedayonesecond.ui.common.CLIP_ASPECT
import com.onedayonesecond.ui.common.ClipPlayer
import com.onedayonesecond.ui.common.ClipStill
import com.onedayonesecond.ui.compile.CompilePhase
import com.onedayonesecond.ui.compile.CompileUiState
import com.onedayonesecond.ui.compile.CompileViewModel
import com.onedayonesecond.ui.compile.DateStyleDialog
import com.onedayonesecond.ui.compile.RangeOption
import com.onedayonesecond.ui.theme.AppType
import com.onedayonesecond.ui.theme.BodyText
import com.onedayonesecond.ui.theme.Chip
import com.onedayonesecond.ui.theme.Hairline
import com.onedayonesecond.ui.theme.HeadingText
import com.onedayonesecond.ui.theme.MediaPlaceholder
import com.onedayonesecond.ui.theme.MetaText
import com.onedayonesecond.ui.theme.Palette
import com.onedayonesecond.ui.theme.QuietButton
import com.onedayonesecond.ui.theme.RoseButton
import com.onedayonesecond.ui.theme.ScreenPadding
import com.onedayonesecond.ui.theme.TitleText

/**
 * The Reel tab: choose a range and what text goes on each second, make the reel, and
 * keep everything backed up to Google Drive.
 */
@UnstableApi
@Composable
fun ReelScreen(
    compile: CompileViewModel = viewModel(factory = CompileViewModel.Factory),
    backup: BackupViewModel = viewModel(factory = BackupViewModel.Factory),
) {
    val reel by compile.state.collectAsStateWithLifecycle()
    val drive by backup.state.collectAsStateWithLifecycle()
    var showStyleDialog by remember { mutableStateOf(false) }

    // Seconds may have been recorded or deleted on another tab since this one was last seen.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        compile.refreshCount()
        backup.refreshCount()
    }

    // Google's one-time "allow this app to use Drive" screen.
    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        backup.onConsentResult(if (result.resultCode == Activity.RESULT_OK) result.data else null)
    }
    LaunchedEffect(drive.consent) {
        val pending = drive.consent ?: return@LaunchedEffect
        backup.consentLaunched()
        consentLauncher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
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

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            TitleText("Reel", Modifier.padding(horizontal = ScreenPadding).padding(top = 16.dp))
            MetaText(
                "${reel.clipCount} second${if (reel.clipCount == 1) "" else "s"} in this range",
                Modifier.padding(horizontal = ScreenPadding).padding(top = 2.dp),
            )

            // The preview, in portrait, beside the range.
            Row(Modifier.padding(horizontal = ScreenPadding).padding(top = 16.dp)) {
                val file = reel.reelFile
                if (file != null) {
                    ClipPlayer(file, version = file.lastModified(), label = "Play your reel", modifier = Modifier.width(132.dp))
                } else if (reel.previewClip != null) {
                    ClipStill(reel.previewClip, Modifier.width(132.dp).aspectRatio(CLIP_ASPECT))
                } else {
                    MediaPlaceholder(Modifier.width(132.dp).aspectRatio(CLIP_ASPECT))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f).selectableGroup()) {
                    RangeOption.entries.forEach { option ->
                        RangeRow(
                            label = option.label,
                            selected = reel.range == option,
                            enabled = !stitching && !saving,
                            onSelect = { compile.selectRange(option) },
                        )
                    }
                }
            }

            if (stitching || saving) {
                Column(Modifier.padding(horizontal = ScreenPadding).padding(top = 16.dp)) {
                    LinearProgressIndicator(
                        progress = { if (saving) 1f else reel.progress },
                        color = Palette.Rose,
                        trackColor = Palette.Hairline,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    MetaText(
                        if (saving) {
                            "Saving to Movies…"
                        } else {
                            "Stitching ${reel.clipCount} seconds, ${(reel.progress * 100).toInt()}%. " +
                                "Keep this tab open until it finishes."
                        },
                        Modifier.padding(top = 8.dp),
                    )
                }
            }
            reel.error?.let {
                BodyText(
                    it,
                    Modifier.padding(horizontal = ScreenPadding).padding(top = 12.dp).semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            reel.exportedName?.let {
                BodyText(
                    "Your reel is saved to Movies/Kilasan as $it.",
                    Modifier.padding(horizontal = ScreenPadding).padding(top = 12.dp).semantics { liveRegion = LiveRegionMode.Polite },
                )
            }

            Hairline(Modifier.padding(horizontal = ScreenPadding).padding(top = 20.dp))
            TextOnEachSecond(reel, compile, enabled = !stitching && !saving, onStyle = { showStyleDialog = true })

            Hairline(Modifier.padding(horizontal = ScreenPadding).padding(top = 20.dp))
            DriveBackupSection(drive, backup)
            Spacer(Modifier.height(16.dp))
        }

        Column(Modifier.padding(horizontal = ScreenPadding).padding(top = 8.dp, bottom = 12.dp)) {
            when {
                stitching -> QuietButton("Cancel", compile::cancel, Modifier.fillMaxWidth())
                // The reel exists but saving it failed: only that step is tried again.
                reel.phase == CompilePhase.READY ->
                    RoseButton("Save the reel to Movies", compile::export, Modifier.fillMaxWidth())
                else -> RoseButton(
                    if (reel.phase == CompilePhase.EXPORTED) "Make it again" else "Make my reel",
                    compile::makeReel,
                    Modifier.fillMaxWidth(),
                    enabled = reel.clipCount > 0 && !saving,
                )
            }
            MetaText("Saves to Movies/Kilasan", Modifier.padding(top = 6.dp).align(Alignment.CenterHorizontally))
        }
    }

    if (showStyleDialog) {
        DateStyleDialog(
            current = reel.dateStyle,
            showDates = reel.showDates,
            showCaptions = reel.showCaptions,
            showPlaces = reel.showPlaces,
            onApply = {
                compile.setDateStyle(it)
                showStyleDialog = false
            },
            onDismiss = { showStyleDialog = false },
        )
    }
}

@Composable
private fun RangeRow(label: String, selected: Boolean, enabled: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
            .defaultMinSize(minHeight = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            enabled = enabled,
            colors = RadioButtonDefaults.colors(selectedColor = Palette.Ink, unselectedColor = Palette.Muted),
        )
        Text(label, style = AppType.Body, color = Palette.Ink, modifier = Modifier.padding(start = 12.dp))
    }
}

/** What is drawn on each second: the date, the caption and the place, each on or off, in one style. */
@UnstableApi
@Composable
private fun TextOnEachSecond(reel: CompileUiState, compile: CompileViewModel, enabled: Boolean, onStyle: () -> Unit) {
    Column(Modifier.padding(horizontal = ScreenPadding).padding(top = 20.dp)) {
        HeadingText("Text on each second")
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Date", reel.showDates, { compile.setShowDates(!reel.showDates) }, enabled = enabled)
            Chip("Caption", reel.showCaptions, { compile.setShowCaptions(!reel.showCaptions) }, enabled = enabled)
            Chip("Place", reel.showPlaces, { compile.setShowPlaces(!reel.showPlaces) }, enabled = enabled)
        }
        val notes = buildList {
            if (reel.showCaptions) add("${reel.captionCount} of ${reel.clipCount} have a caption")
            if (reel.showPlaces) add("${reel.placeCount} of ${reel.clipCount} have a place")
        }
        if (notes.isNotEmpty()) MetaText(notes.joinToString(". ") + ".", Modifier.padding(top = 8.dp))

        if (reel.showDates || reel.showCaptions || reel.showPlaces) {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                MetaText(
                    "${reel.dateStyle.font.label}, ${reel.dateStyle.edge.label.lowercase()} " +
                        reel.dateStyle.align.label.lowercase(),
                    Modifier.weight(1f),
                )
                QuietButton("Change style", onStyle, enabled = enabled)
            }
        }
    }
}

/** Status of the Drive backup, with Back up now and Restore. */
@Composable
private fun DriveBackupSection(drive: BackupUiState, backup: BackupViewModel) {
    val busy = drive.phase != BackupPhase.IDLE
    Column(Modifier.padding(horizontal = ScreenPadding).padding(top = 20.dp)) {
        HeadingText("Google Drive backup")
        MetaText(
            "Copies your seconds and your journal to a \"Kilasan\" folder in your own Google Drive, " +
                "one folder for each month. Nothing is sent until you tap Back up now.",
            Modifier.padding(top = 4.dp),
        )

        when (drive.phase) {
            BackupPhase.AUTHORIZING -> {
                LinearProgressIndicator(
                    color = Palette.Rose,
                    trackColor = Palette.Hairline,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
                MetaText("Connecting to Google…", Modifier.padding(top = 8.dp))
            }

            BackupPhase.WORKING -> {
                val verb = if (drive.action == BackupAction.RESTORE) "Restored" else "Checked"
                if (drive.total > 0) {
                    LinearProgressIndicator(
                        progress = { drive.done.toFloat() / drive.total },
                        color = Palette.Rose,
                        trackColor = Palette.Hairline,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                    MetaText(
                        "$verb ${drive.done} of ${drive.total}. Keep this tab open until it finishes. If the " +
                            "connection drops it tries again by itself, and if it still fails you can tap " +
                            "again: it carries on where it stopped.",
                        Modifier.padding(top = 8.dp),
                    )
                } else {
                    LinearProgressIndicator(
                        color = Palette.Rose,
                        trackColor = Palette.Hairline,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                    MetaText("Looking at what is in Drive…", Modifier.padding(top = 8.dp))
                }
            }

            BackupPhase.IDLE -> Unit
        }

        // The last result, good or bad, stays until the next run.
        (drive.error ?: drive.notice)?.let {
            BodyText(it, Modifier.padding(top = 12.dp).semantics { liveRegion = LiveRegionMode.Polite })
        }

        if (busy) {
            QuietButton("Cancel", backup::cancel, Modifier.fillMaxWidth().padding(top = 12.dp))
        } else {
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuietButton(
                    "Back up now",
                    { backup.start(BackupAction.BACKUP) },
                    Modifier.weight(1f),
                    enabled = drive.hasAnything,
                )
                QuietButton("Restore", { backup.start(BackupAction.RESTORE) }, Modifier.weight(1f))
            }
            MetaText(
                "Restore only adds days that are missing from this phone. Nothing already here is overwritten.",
                Modifier.padding(top = 8.dp),
            )
        }
    }
}
