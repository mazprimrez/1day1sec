package com.onedayonesecond.ui.books

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import com.onedayonesecond.OneDayApp
import java.io.File

/** The two ways a photo gets into the reading log: the phone's camera app, or the photo picker. */
class PhotoPicker(val take: () -> Unit, val choose: () -> Unit)

/**
 * Photos for a book: its cover, or the page a thought is about. Taking one uses the
 * phone's camera app, saving into the app's scratch folder, the same way the diary's
 * photos are taken.
 */
@UnstableApi
@Composable
fun rememberPhotoPicker(onPhoto: (Uri) -> Unit): PhotoPicker {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as OneDayApp).container }
    val deliver by rememberUpdatedState(onPhoto)
    var photoPath by rememberSaveable { mutableStateOf<String?>(null) }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) deliver(uri)
    }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val path = photoPath
        if (taken && path != null) deliver(File(path).toUri())
    }

    fun openCamera() {
        val file = container.storage.newScratchFile("jpg")
        photoPath = file.absolutePath
        try {
            takePhoto.launch(FileProvider.getUriForFile(context, "${context.packageName}.files", file))
        } catch (e: ActivityNotFoundException) {
            container.messages.show("No camera app was found on this phone. Choose from your photos instead.")
        }
    }

    // Android refuses to open the camera app for an app that lists the camera
    // permission but has not been granted it, so it is asked for first.
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            openCamera()
        } else {
            container.messages.show(
                "The camera isn't allowed for Kilasan. Allow it in your phone's Settings, under Apps, " +
                    "or choose from your photos.",
            )
        }
    }

    return remember(pick, takePhoto, cameraPermission) {
        PhotoPicker(
            take = {
                val allowed = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
                if (allowed) openCamera() else cameraPermission.launch(Manifest.permission.CAMERA)
            },
            choose = {
                pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
        )
    }
}
