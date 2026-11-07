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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
    onCaptured: (File) -> Unit,
    onBack: () -> Unit,
    viewModel: CameraViewModel = viewModel(factory = CameraViewModel.Factory),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.state.collectAsStateWithLifecycle()

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

    Box(Modifier.fillMaxSize().background(Color.Black)) {
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

        IconButton(
            onClick = onBack,
            modifier = Modifier.systemBarsPadding().padding(8.dp),
        ) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
        }

        if (canFlip) {
            IconButton(
                onClick = {
                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                        CameraSelector.LENS_FACING_BACK
                    } else {
                        CameraSelector.LENS_FACING_FRONT
                    }
                },
                enabled = !busy,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .systemBarsPadding()
                    .padding(8.dp),
            ) {
                Icon(
                    Icons.Filled.Cameraswitch,
                    contentDescription = "Switch camera",
                    tint = Color.White.copy(alpha = if (busy) 0.35f else 1f),
                )
            }
        }

        // Lives outside the `cameraGranted` branch on purpose: choosing an existing
        // video needs no camera, so it stays available if camera access is denied.
        IconButton(
            onClick = {
                pickVideo.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
                )
            },
            enabled = !busy,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .systemBarsPadding()
                // Vertically centred on the 88dp record button, which sits 32dp up.
                .padding(start = 32.dp, bottom = 52.dp)
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.4f)),
        ) {
            Icon(
                Icons.Filled.PhotoLibrary,
                contentDescription = "Choose a video from your gallery or Google Photos",
                tint = Color.White.copy(alpha = if (busy) 0.35f else 1f),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .systemBarsPadding()
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            (state as? CaptureState.Failed)?.let {
                Text(it.message, color = MaterialTheme.colorScheme.error)
            }
            if (state is CaptureState.Importing) {
                CircularProgressIndicator(Modifier.size(28.dp), color = Color.White)
                Text("Importing video…", color = Color.White)
            }
            if (!audioGranted && cameraGranted) {
                Text("Recording without audio", color = Color.White.copy(alpha = 0.7f))
            }

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
            )
        }
    }
}

@Composable
private fun RecordButton(
    state: CaptureState,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    val elapsed = (state as? CaptureState.Recording)?.elapsedMs ?: 0L
    // The ViewModel already ticks at 50ms, so this is smooth without animating
    // it — an animation on top would only add lag.
    val progress = elapsed.toFloat() / VideoSpec.MAX_RECORDING_MS

    Box(contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.size(88.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = Color.White.copy(alpha = 0.25f),
        )
        Box(
            modifier = Modifier
                .size(if (state is CaptureState.Recording) 36.dp else 64.dp)
                .clip(CircleShape)
                .background(if (enabled) Color(0xFFE53935) else Color.Gray)
                .clickable(
                    enabled = enabled &&
                        state !is CaptureState.Finalising &&
                        state !is CaptureState.Importing,
                    onClick = onToggle,
                ),
        )
    }
}

@Composable
private fun PermissionPrompt(onRequest: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Camera access is needed to record your second.", color = Color.White)
        Button(onClick = onRequest) { Text("Grant access") }
    }
}

private fun android.content.Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

/** hasCamera throws if the camera service is unavailable; treat that as "no". */
private fun ProcessCameraProvider.has(selector: CameraSelector): Boolean =
    runCatching { hasCamera(selector) }.getOrDefault(false)
