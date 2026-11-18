package com.onedayonesecond.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.onedayonesecond.ui.theme.ProgressLine
import com.onedayonesecond.ui.theme.ScreenPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import com.onedayonesecond.journal.RecapLabels
import com.onedayonesecond.ui.theme.AppType
import com.onedayonesecond.ui.theme.IconAction
import com.onedayonesecond.ui.theme.LineIcons
import com.onedayonesecond.ui.theme.Corners
import com.onedayonesecond.ui.theme.Palette
import com.onedayonesecond.ui.theme.RoseButton
import java.time.LocalDate
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import com.onedayonesecond.media.VideoSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@UnstableApi
@Composable
fun CameraScreen(
    date: LocalDate,
    onCaptured: (File) -> Unit,
    onBack: () -> Unit,
    viewModel: CameraViewModel = viewModel(factory = CameraViewModel.Factory),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val place by viewModel.place.collectAsStateWithLifecycle()
    LaunchedEffect(date) { viewModel.findPlace(date) }

    // The picture runs up under the status bar, so while this screen is up the
    // status bar gets light icons, and dark ones again on the way out. The bottom of
    // the screen is a paper panel, so the navigation bar keeps its dark icons.
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        val bars = window?.let { WindowCompat.getInsetsController(it, view) }
        bars?.isAppearanceLightStatusBars = false
        onDispose { bars?.isAppearanceLightStatusBars = true }
    }

    var cameraGranted by remember {
        mutableStateOf(context.hasPermission(Manifest.permission.CAMERA))
    }
    var audioGranted by remember {
        mutableStateOf(context.hasPermission(Manifest.permission.RECORD_AUDIO))
    }

    var lensFacing by rememberSaveable { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    // Only offer the switch once we know the device really has both cameras.
    var canFlip by remember { mutableStateOf(false) }

    // The bound camera, needed to forward taps as focus requests; and the ring
    // shown where the last tap landed.
    var camera by remember { mutableStateOf<Camera?>(null) }
    var focusRing by remember { mutableStateOf<FocusRing?>(null) }
    var tapCount by remember { mutableStateOf(0L) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        cameraGranted = result[Manifest.permission.CAMERA] ?: cameraGranted
        audioGranted = result[Manifest.permission.RECORD_AUDIO] ?: audioGranted
    }

    // Android's Photo Picker: shows the phone's gallery and, where the phone
    // supports it, Google Photos too. It needs no storage permission because the
    // app only receives the one video the user picks.
    val pickVideo = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) viewModel.importVideo(uri)
    }

    LaunchedEffect(Unit) {
        if (!cameraGranted || !audioGranted) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
            )
        }
    }

    // Keyed on the file, not on `state` — the recording timer updates state
    // many times a second and would otherwise restart this effect each tick.
    val capturedFile = (state as? CaptureState.Captured)?.file
    LaunchedEffect(capturedFile) {
        if (capturedFile == null) return@LaunchedEffect
        viewModel.reset()
        onCaptured(capturedFile)
    }

    val previewView = remember { PreviewView(context) }
    val videoCapture = remember {
        // A fixed quality target keeps every recording the same shape, which is
        // what lets the trim step normalise cheaply and the compile step stitch
        // without surprises.
        VideoCapture.withOutput(
            Recorder.Builder()
                .setQualitySelector(
                    QualitySelector.from(
                        Quality.HD,
                        FallbackStrategy.lowerQualityOrHigherThan(Quality.SD),
                    ),
                )
                .build(),
        )
    }

    // Re-runs whenever the user flips cameras: the effect is keyed on the lens, so
    // the old binding is torn down and the new one built from scratch.
    LaunchedEffect(cameraGranted, lensFacing) {
        if (!cameraGranted) return@LaunchedEffect
        val provider = withContext(Dispatchers.IO) {
            ProcessCameraProvider.getInstance(context).get()
        }

        val hasBack = provider.has(CameraSelector.DEFAULT_BACK_CAMERA)
        val hasFront = provider.has(CameraSelector.DEFAULT_FRONT_CAMERA)
        canFlip = hasBack && hasFront

        // Fall back to whichever camera exists, so a front-only device (or a
        // saved front choice on a device that lost it) still opens.
        val selector = when {
            lensFacing == CameraSelector.LENS_FACING_FRONT && hasFront ->
                CameraSelector.DEFAULT_FRONT_CAMERA
            hasBack -> CameraSelector.DEFAULT_BACK_CAMERA
            else -> CameraSelector.DEFAULT_FRONT_CAMERA
        }

        // setSurfaceProvider has no matching getter, so Kotlin synthetic
        // property access is not available here.
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        camera = null
        focusRing = null
        provider.unbindAll()
        camera = provider.bindToLifecycle(
            lifecycleOwner,
            selector,
            preview,
            videoCapture,
        )
    }

    // Switching cameras mid-recording would cut the clip, and a second import
    // while one is running would race it, so those controls lock while busy.
    val busy = state is CaptureState.Recording ||
        state is CaptureState.Finalising ||
        state is CaptureState.Importing

    Box(Modifier.fillMaxSize().background(Palette.Ink)) {
        if (cameraGranted) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

            // Tap anywhere on the picture to focus there. It sits above the preview
            // but below the buttons, which are drawn later and so get their taps first.
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { tap ->
                            val bound = camera ?: return@detectTapGestures
                            val id = ++tapCount
                            focusRing = FocusRing(id, tap.x, tap.y, FocusOutcome.SEARCHING)
                            focusAt(
                                camera = bound,
                                previewView = previewView,
                                x = tap.x,
                                y = tap.y,
                                executor = ContextCompat.getMainExecutor(context),
                            ) { outcome ->
                                // Ignore a late answer for a tap that a newer one replaced.
                                if (focusRing?.id == id) focusRing = focusRing?.copy(outcome = outcome)
                            }
                        }
                    },
            )
            FocusRingOverlay(
                ring = focusRing,
                onFinished = { id -> if (focusRing?.id == id) focusRing = null },
            )
        } else {
            PermissionPrompt(
                onRequest = {
                    permissionLauncher.launch(
                        arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
                    )
                },
                modifier = Modifier.align(Alignment.Center),
            )
        }

        // Darkens the top of the picture so the date reads on any footage.
        Box(
            Modifier
                .fillMaxWidth()
                .height(160.dp)
                .background(Brush.verticalGradient(listOf(Palette.Ink.copy(alpha = 0.55f), Color.Transparent))),
        )

        Row(
            Modifier.statusBarsPadding().fillMaxWidth().height(64.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconAction(LineIcons.Close, "Close the camera", onBack, tint = Palette.Paper)
            // The day being recorded for, with the place under it.
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text(RecapLabels.weekdayAndDay(date), style = AppType.Heading.merge(OnVideo), color = Palette.Paper)
                place?.let {
                    Text(it, style = AppType.Small.merge(OnVideo), color = Palette.Paper, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        // Everything that is not the picture sits on a paper panel at the bottom.
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(Corners.Sheet)
                .background(Palette.Paper)
                .navigationBarsPadding()
                .padding(start = ScreenPadding, top = 20.dp, end = ScreenPadding, bottom = 28.dp),
        ) {
            val elapsed = (state as? CaptureState.Recording)?.elapsedMs ?: 0L
            val seconds = (VideoSpec.MAX_RECORDING_MS / 1000).toInt()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clearAndSetSemantics {
                    contentDescription = if (state is CaptureState.Recording) {
                        "Recording, ${elapsed / 1000} of $seconds seconds"
                    } else {
                        "Not recording"
                    }
                },
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (state is CaptureState.Recording) Palette.Error else Palette.FrameLine),
                )
                Text(
                    "${timecode(elapsed)} / ${timecode(VideoSpec.MAX_RECORDING_MS)}",
                    style = AppType.NumeralMedium.copy(fontSize = 16.sp),
                    color = Palette.Ink,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }

            // One segment per second, filling as the recording runs. The ViewModel already
            // ticks at 50 ms, so this is smooth without an animation on top.
            Row(
                Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp).clearAndSetSemantics { },
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                repeat(seconds) { second ->
                    ProgressLine(
                        fraction = (elapsed - second * 1000L) / 1000f,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Text(
                when (val current = state) {
                    is CaptureState.Failed -> current.message
                    is CaptureState.Importing -> "Importing video…"
                    else -> "Up to $seconds seconds. You'll pick the best second next." +
                        if (!audioGranted && cameraGranted) " Recording without sound." else ""
                },
                style = AppType.Meta,
                color = if (state is CaptureState.Failed) Palette.Ink else Palette.Muted,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 20.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )

            Box(Modifier.fillMaxWidth()) {
                // Offered whether or not the camera is allowed: choosing an existing video
                // needs no camera.
                PanelButton(
                    label = "Gallery",
                    icon = LineIcons.Image,
                    description = "Choose a video from your gallery or Google Photos",
                    enabled = !busy,
                    onClick = {
                        pickVideo.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
                        )
                    },
                    modifier = Modifier.align(Alignment.CenterStart),
                )
                RecordButton(
                    state = state,
                    enabled = cameraGranted,
                    onToggle = {
                        if (viewModel.isRecording) {
                            viewModel.stopRecording()
                        } else {
                            viewModel.startRecording(context, videoCapture, audioGranted)
                        }
                    },
                    modifier = Modifier.align(Alignment.Center),
                )
                if (canFlip) {
                    val front = lensFacing == CameraSelector.LENS_FACING_FRONT
                    PanelButton(
                        label = "Flip",
                        icon = LineIcons.Flip,
                        description = if (front) "Switch to the back camera" else "Switch to the front camera",
                        enabled = !busy,
                        onClick = {
                            lensFacing = if (front) CameraSelector.LENS_FACING_BACK else CameraSelector.LENS_FACING_FRONT
                        },
                        modifier = Modifier.align(Alignment.CenterEnd),
                    )
                }
            }
        }
    }
}

/** "00:02": seconds the way a camera prints them. */
private fun timecode(ms: Long): String = "%02d:%02d".format(ms / 60_000, ms / 1000 % 60)

/** A smaller control beside the record button: an icon on a tinted square, with its name under it. */
@Composable
private fun PanelButton(
    label: String,
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = if (enabled) Palette.Ink else Palette.Muted.copy(alpha = 0.5f)
    Column(
        modifier = modifier
            .clip(Corners.Alert)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = description, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(48.dp).clip(Corners.Alert).background(Palette.BlushTint),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint)
        }
        Text(label, style = AppType.Tab, color = tint, modifier = Modifier.padding(top = 4.dp))
    }
}

/** A rose ring with a disc in it; the disc becomes a small square while recording. */
@Composable
private fun RecordButton(
    state: CaptureState,
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val recording = state is CaptureState.Recording
    val colour = if (enabled) Palette.Rose else Palette.FrameLine
    Box(
        modifier = modifier
            .size(80.dp)
            .clip(CircleShape)
            .border(4.dp, colour, CircleShape)
            .clickable(
                enabled = enabled &&
                    state !is CaptureState.Finalising &&
                    state !is CaptureState.Importing,
                role = Role.Button,
                onClick = onToggle,
            )
            .semantics { contentDescription = if (recording) "Stop recording" else "Record" },
        contentAlignment = Alignment.Center,
    ) {
        if (state is CaptureState.Finalising || state is CaptureState.Importing) {
            CircularProgressIndicator(Modifier.size(28.dp), color = Palette.Rose, strokeWidth = 2.dp)
        } else {
            Box(
                Modifier
                    .size(if (recording) 28.dp else 56.dp)
                    .clip(if (recording) RoundedCornerShape(6.dp) else CircleShape)
                    .background(colour),
            )
        }
    }
}

@Composable
private fun PermissionPrompt(onRequest: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "The camera is needed to record your second.",
            style = AppType.Body,
            color = Palette.Paper,
            textAlign = TextAlign.Center,
        )
        RoseButton("Allow the camera", onRequest, icon = LineIcons.Camera)
    }
}

/** A soft shadow, so white text stays readable over bright footage. */
private val OnVideo = TextStyle(shadow = Shadow(Palette.Ink.copy(alpha = 0.6f), Offset(0f, 1f), 4f))

private fun android.content.Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

/** hasCamera throws if the camera service is unavailable; treat that as "no". */
private fun ProcessCameraProvider.has(selector: CameraSelector): Boolean =
    runCatching { hasCamera(selector) }.getOrDefault(false)
