package com.onedayonesecond.ui.day

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.util.UnstableApi
import com.onedayonesecond.OneDayApp
import com.onedayonesecond.journal.RecapLabels
import com.onedayonesecond.ui.theme.AppType
import com.onedayonesecond.ui.theme.BackBar
import com.onedayonesecond.ui.theme.BodyText
import com.onedayonesecond.ui.theme.MetaText
import com.onedayonesecond.ui.theme.Palette
import com.onedayonesecond.ui.theme.QuietButton
import com.onedayonesecond.ui.theme.RoseButton
import com.onedayonesecond.ui.theme.ScreenPadding
import com.onedayonesecond.ui.theme.TitleText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

/** Wraps the recorder so the screen only deals in start, finish and discard. */
class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    var file: File? = null
        private set

    fun start(target: File) {
        val created = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        try {
            created.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                // Speech, not music: small files that upload quickly and back up in one request.
                setAudioChannels(1)
                setAudioSamplingRate(44_100)
                setAudioEncodingBitRate(64_000)
                setOutputFile(target.absolutePath)
                prepare()
                start()
            }
        } catch (e: Exception) {
            created.release()
            target.delete()
            throw e
        }
        recorder = created
        file = target
    }

    /** Ends the recording. Returns the file, or null if nothing usable was recorded. */
    fun finish(): File? {
        val active = recorder ?: return null
        recorder = null
        val recorded = file
        file = null
        return try {
            active.stop()
            recorded
        } catch (e: RuntimeException) {
            // stop() throws when it is called almost at once: there is no audio in the file.
            recorded?.delete()
            null
        } finally {
            active.release()
        }
    }

    fun discard() {
        finish()?.delete()
    }
}

/**
 * Records a voice note for a day. It can be played from the day page the moment it is
 * saved; its words are typed out afterwards.
 */
@UnstableApi
@Composable
fun VoiceScreen(date: LocalDate, onDone: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as OneDayApp).container }
    val recorder = remember { VoiceRecorder(context.applicationContext) }

    var micAllowed by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var recording by remember { mutableStateOf(false) }
    var elapsedMs by remember { mutableLongStateOf(0L) }
    var saving by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        micAllowed = granted
        if (!granted) problem = "The microphone isn't allowed for Kilasan. Allow it in your phone's Settings, under Apps."
    }
    LaunchedEffect(Unit) { if (!micAllowed) permission.launch(Manifest.permission.RECORD_AUDIO) }

    fun start() {
        problem = null
        try {
            recorder.start(container.storage.newScratchFile("m4a"))
            elapsedMs = 0
            recording = true
        } catch (e: Exception) {
            problem = "The microphone could not be started. Another app may be using it."
        }
    }

    fun finish() {
        recording = false
        val file = recorder.finish()
        if (file == null || elapsedMs < MIN_VOICE_MS) {
            file?.delete()
            problem = "That was too short to keep. Try again and speak for a second or more."
            return
        }
        saving = true
        // On the app's scope, so the note is kept even if this screen is left mid-save.
        container.appScope.launch {
            container.journalActions.addVoice(date, file)
            onDone()
        }
    }

    // The clock, and the hard stop at the longest note that still backs up in one request.
    LaunchedEffect(recording) {
        if (!recording) return@LaunchedEffect
        val startedAt = System.currentTimeMillis()
        while (true) {
            elapsedMs = System.currentTimeMillis() - startedAt
            if (elapsedMs >= MAX_VOICE_MS) {
                finish()
                break
            }
            delay(200)
        }
    }

    // Leaving the screen or the app mid-recording throws the recording away rather
    // than leaving the microphone on.
    DisposableEffect(Unit) { onDispose { recorder.discard() } }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (recording) {
            recording = false
            recorder.discard()
            problem = "Recording stopped because the app went to the background."
        }
    }

    val view = LocalView.current
    DisposableEffect(recording) {
        view.keepScreenOn = recording
        onDispose { view.keepScreenOn = false }
    }

    Column(Modifier.fillMaxSize()) {
        BackBar(onBack, backLabel = if (recording) "Back and discard the recording" else "Back")
        Column(Modifier.weight(1f).padding(horizontal = ScreenPadding)) {
            TitleText("Voice note")
            MetaText(RecapLabels.fullDate(date), Modifier.padding(top = 2.dp))

            Column(
                Modifier.fillMaxWidth().weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    formatClock(elapsedMs),
                    style = AppType.BigDate,
                    color = if (recording) Palette.Ink else Palette.Muted,
                )
                MetaText(
                    when {
                        saving -> "Saving…"
                        recording -> "Recording. Up to ${MAX_VOICE_MS / 60_000} minutes."
                        else -> "Say what's on your mind. It's written out for you afterwards."
                    },
                    Modifier.padding(top = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
                )
            }

            problem?.let {
                BodyText(it, Modifier.padding(bottom = 12.dp).semantics { liveRegion = LiveRegionMode.Polite })
            }
        }

        Column(
            Modifier.padding(horizontal = ScreenPadding, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when {
                recording -> {
                    RoseButton("Finish and save", ::finish, Modifier.fillMaxWidth())
                    QuietButton(
                        "Discard",
                        {
                            recording = false
                            recorder.discard()
                            elapsedMs = 0
                        },
                        Modifier.fillMaxWidth(),
                    )
                }
                !micAllowed -> RoseButton(
                    "Allow the microphone",
                    { permission.launch(Manifest.permission.RECORD_AUDIO) },
                    Modifier.fillMaxWidth(),
                )
                else -> RoseButton("Start recording", ::start, Modifier.fillMaxWidth(), enabled = !saving)
            }
        }
    }
}

fun formatClock(ms: Long): String {
    val seconds = ms / 1000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

const val MIN_VOICE_MS = 1_000L

// At 64 kbit/s five minutes is about 2.4 MB, inside Drive's 5 MB single-request upload.
const val MAX_VOICE_MS = 5 * 60_000L
