package com.onedayonesecond.ui.settings

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onedayonesecond.ui.backup.BackupAction
import com.onedayonesecond.ui.backup.BackupLabels
import com.onedayonesecond.ui.backup.BackupPhase
import com.onedayonesecond.ui.backup.BackupViewModel
import com.onedayonesecond.ui.theme.AppType
import com.onedayonesecond.ui.theme.BackBar
import com.onedayonesecond.ui.theme.BusyLine
import com.onedayonesecond.ui.theme.InlineAlert
import com.onedayonesecond.ui.theme.LineIcons
import com.onedayonesecond.ui.theme.MetaText
import com.onedayonesecond.ui.theme.Palette
import com.onedayonesecond.ui.theme.ProgressLine
import com.onedayonesecond.ui.theme.QuietButton
import com.onedayonesecond.ui.theme.RoseButton
import com.onedayonesecond.ui.theme.ScreenPadding
import com.onedayonesecond.ui.theme.SmallText
import com.onedayonesecond.ui.theme.Stat
import com.onedayonesecond.ui.theme.TintCard

/**
 * The backup to Google Drive: when it last ran, how much there is on this phone, and
 * the two things to do about it, back up and restore. Nothing is sent until one of
 * them is tapped.
 *
 * The view model is the activity's, so a run started here carries on when the page is
 * left, and the Reel tab and Settings show how it is going.
 */
@Composable
fun BackupScreen(onBack: () -> Unit, backup: BackupViewModel) {
    val drive by backup.state.collectAsStateWithLifecycle()
    val busy = drive.phase != BackupPhase.IDLE

    // Things may have been added on another tab since the counts were last taken.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { backup.refreshCount() }

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

    // A backup is many uploads in a row. If the screen turned off part-way the phone
    // would cut the network, so the screen is held on while it runs.
    val view = LocalView.current
    DisposableEffect(busy) {
        view.keepScreenOn = busy
        onDispose { view.keepScreenOn = false }
    }

    Column(Modifier.fillMaxSize()) {
        BackBar(onBack, title = "Google Drive backup")
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = ScreenPadding, top = 4.dp, end = ScreenPadding, bottom = 32.dp),
        ) {
            TintCard(Modifier.semantics(mergeDescendants = true) { }, padding = 16.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(Palette.Blush),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(LineIcons.Cloud, contentDescription = null, tint = Palette.Ink, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Your Google Drive", style = AppType.Heading, color = Palette.Ink)
                        MetaText(BackupLabels.lastBackup(drive.lastBackupAt))
                    }
                    if (drive.lastBackupAt != null) {
                        Icon(LineIcons.Check, contentDescription = null, tint = Palette.Rose, modifier = Modifier.size(22.dp))
                    }
                }
            }

            // What is on this phone, which is what a backup would hold.
            Row(Modifier.fillMaxWidth().padding(top = 20.dp)) {
                Stat("Seconds", drive.clipCount.toString(), Modifier.weight(1f))
                Stat("Entries", drive.entryCount.toString(), Modifier.weight(1f))
                Stat("Books", drive.bookCount.toString(), Modifier.weight(1f))
            }

            Text(
                "Your seconds, entries, photos, voice notes and books go to a Kilasan folder in your own " +
                    "Drive. Nobody else can see it. Nothing is sent until you tap Back up now.",
                style = AppType.Meta,
                color = Palette.Ink,
                modifier = Modifier.padding(top = 20.dp),
            )

            when (drive.phase) {
                BackupPhase.AUTHORIZING -> {
                    BusyLine(Modifier.padding(top = 20.dp))
                    MetaText(
                        "Connecting to Google…",
                        Modifier.padding(top = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }

                BackupPhase.WORKING -> {
                    val verb = if (drive.action == BackupAction.RESTORE) "Restored" else "Checked"
                    if (drive.total > 0) {
                        ProgressLine(drive.done.toFloat() / drive.total, Modifier.padding(top = 20.dp))
                        MetaText(
                            "$verb ${drive.done} of ${drive.total}. It carries on if you leave this page. If the " +
                                "connection drops it tries again by itself, and if it still fails you can tap " +
                                "again: it picks up where it stopped.",
                            Modifier.padding(top = 8.dp),
                        )
                    } else {
                        BusyLine(Modifier.padding(top = 20.dp))
                        MetaText("Looking at what is in Drive…", Modifier.padding(top = 8.dp))
                    }
                }

                BackupPhase.IDLE -> Unit
            }

            // The last result, good or bad, stays until the next run.
            (drive.error ?: drive.notice)?.let {
                InlineAlert(
                    it,
                    Modifier.padding(top = 20.dp).semantics { liveRegion = LiveRegionMode.Polite },
                    icon = if (drive.error != null) LineIcons.Warning else LineIcons.Cloud,
                )
            }

            if (busy) {
                QuietButton("Cancel", backup::cancel, Modifier.fillMaxWidth().padding(top = 24.dp))
            } else {
                RoseButton(
                    "Back up now",
                    { backup.start(BackupAction.BACKUP) },
                    Modifier.fillMaxWidth().padding(top = 24.dp),
                    enabled = drive.hasAnything,
                    icon = LineIcons.Cloud,
                )
                QuietButton(
                    "Restore from Drive",
                    { backup.start(BackupAction.RESTORE) },
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    icon = LineIcons.Restore,
                )
                SmallText(
                    "Restore only adds days and books missing from this phone. Nothing already here is overwritten.",
                    Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}
