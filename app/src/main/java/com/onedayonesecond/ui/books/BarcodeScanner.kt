package com.onedayonesecond.ui.books

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import com.onedayonesecond.ui.theme.BackBar
import com.onedayonesecond.ui.theme.Corners
import com.onedayonesecond.ui.theme.StateCard
import com.onedayonesecond.ui.theme.TextAction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.onedayonesecond.books.Isbn
import com.onedayonesecond.ui.theme.AppType
import com.onedayonesecond.ui.theme.BodyText
import com.onedayonesecond.ui.theme.IconAction
import com.onedayonesecond.ui.theme.LineIcons
import com.onedayonesecond.ui.theme.Palette
import com.onedayonesecond.ui.theme.RoseButton
import com.onedayonesecond.ui.theme.ScreenPadding
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Reads the ISBN barcode on a book's back cover. The reading is done on the phone by
 * ML Kit; no picture leaves it. Only an ISBN is accepted: a price sticker or a shop's
 * own label is another kind of barcode, and the scanner keeps looking past it.
 */
@OptIn(ExperimentalGetImage::class)
@Composable
fun BarcodeScanner(onIsbn: (String) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val deliver by rememberUpdatedState(onIsbn)

    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var asked by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        asked = true
    }
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }

    BackHandler(onBack = onClose)

    val previewView = remember { PreviewView(context) }

    DisposableEffect(granted) {
        if (!granted) return@DisposableEffect onDispose { }

        val found = AtomicBoolean(false)
        val executor = Executors.newSingleThreadExecutor()
        val scanner = BarcodeScanning.getClient(
            // Books carry their ISBN as an EAN-13 barcode; looking for nothing else is faster.
            BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_EAN_13).build(),
        )
        var provider: ProcessCameraProvider? = null
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener(
            {
                try {
                    val cameras = future.get()
                    provider = cameras
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val analysis = ImageAnalysis.Builder()
                        // Frames that arrive while one is being read are dropped, not queued.
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    analysis.setAnalyzer(executor) { frame ->
                        val image = frame.image
                        if (image == null || found.get()) {
                            frame.close()
                            return@setAnalyzer
                        }
                        scanner.process(InputImage.fromMediaImage(image, frame.imageInfo.rotationDegrees))
                            .addOnSuccessListener { codes ->
                                val isbn = codes.firstNotNullOfOrNull { Isbn.fromBarcode(it.rawValue) }
                                // Once: the camera keeps seeing the same barcode many times a second.
                                if (isbn != null && found.compareAndSet(false, true)) deliver(isbn)
                            }
                            .addOnCompleteListener { frame.close() }
                    }
                    cameras.unbindAll()
                    cameras.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                } catch (e: Exception) {
                    problem = "The camera could not be started. Type the ISBN from the back cover instead."
                }
            },
            ContextCompat.getMainExecutor(context),
        )

        onDispose {
            provider?.unbindAll()
            scanner.close()
            executor.shutdown()
        }
    }

    Box(Modifier.fillMaxSize().background(Palette.Paper)) {
        if (granted && problem == null) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
            Text(
                "Hold the barcode on the back cover in view.",
                style = AppType.Meta,
                color = Palette.Paper,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(ScreenPadding)
                    .clip(Corners.Chip)
                    .background(Palette.Ink.copy(alpha = 0.78f))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Palette.Ink.copy(alpha = 0.78f)),
            ) {
                IconAction(LineIcons.Close, "Stop scanning", onClose, tint = Palette.Paper)
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                BackBar(onClose, backLabel = "Back to search", title = "Scan the barcode", backIcon = LineIcons.Close)
                StateCard(
                    if (problem != null) "The camera couldn't start" else "The camera is needed",
                    Modifier.padding(horizontal = ScreenPadding).padding(top = 8.dp),
                ) {
                    BodyText(
                        problem ?: if (asked) {
                            "The camera isn't allowed for Kilasan. Allow it in your phone's Settings, under Apps, " +
                                "or type the ISBN from the back cover instead."
                        } else {
                            "Kilasan needs the camera to read the barcode on the back cover."
                        },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (problem == null) {
                            RoseButton("Allow the camera", { permission.launch(Manifest.permission.CAMERA) })
                        }
                        TextAction(
                            "Type the ISBN",
                            onClose,
                            if (problem == null) Modifier else Modifier.offset(x = (-12).dp),
                            color = if (problem == null) Palette.Ink else Palette.Rose,
                        )
                    }
                }
            }
        }
    }
}
