package com.onedayonesecond.ui.backup

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onBack: () -> Unit,
    viewModel: BackupViewModel = viewModel(factory = BackupViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Google's one-time "allow this app to use Drive" screen.
    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        viewModel.onConsentResult(if (result.resultCode == Activity.RESULT_OK) result.data else null)
    }
    LaunchedEffect(state.consent) {
        val pending = state.consent ?: return@LaunchedEffect
        viewModel.consentLaunched()
        consentLauncher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
    }

    val busy = state.phase != BackupPhase.IDLE

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Google Drive backup") },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Copies your clips to a “1day1sec” folder in your Google Drive, as ordinary " +
                    "videos with one folder for each month (for example “2026-11”). You can " +
                    "play them in the Drive app too. The app can only see files it created " +
                    "itself, never the rest of your Drive. Clips that are already backed up " +
                    "are skipped, so it is safe to run again.",
                style = MaterialTheme.typography.bodyMedium,
            )

            Text(
                text = "${state.clipCount} clip${if (state.clipCount == 1) "" else "s"} on this phone",
                style = MaterialTheme.typography.titleSmall,
            )

            when (state.phase) {
                BackupPhase.AUTHORIZING -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Connecting to Google…", style = MaterialTheme.typography.bodySmall)
                }

                BackupPhase.WORKING -> {
                    val verb = if (state.action == BackupAction.RESTORE) "Restored" else "Checked"
                    if (state.total > 0) {
                        LinearProgressIndicator(
                            progress = { state.done.toFloat() / state.total },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text("$verb ${state.done} of ${state.total}…", style = MaterialTheme.typography.bodySmall)
                    } else {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("Looking at what is in Drive…", style = MaterialTheme.typography.bodySmall)
                    }
                }

                BackupPhase.IDLE -> Unit
            }

            state.notice?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            if (busy) {
                OutlinedButton(onClick = viewModel::cancel, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancel")
                }
            } else {
                Button(
                    onClick = { viewModel.start(BackupAction.BACKUP) },
                    enabled = state.clipCount > 0,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Back up now") }

                OutlinedButton(
                    onClick = { viewModel.start(BackupAction.RESTORE) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Restore from Drive") }

                Text(
                    "Restore adds the days that are in Drive but missing here. Days already on " +
                        "this phone are never overwritten.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
