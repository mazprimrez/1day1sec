package com.onedayonesecond.ui.day

import kotlinx.coroutines.flow.first
import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import com.onedayonesecond.OneDayApp
import com.onedayonesecond.journal.RecapLabels
import com.onedayonesecond.location.PlaceFinder
import com.onedayonesecond.ui.theme.ActionRow
import com.onedayonesecond.ui.theme.AppSheet
import com.onedayonesecond.ui.theme.AppType
import com.onedayonesecond.ui.theme.LineIcons
import com.onedayonesecond.ui.theme.MetaText
import com.onedayonesecond.ui.theme.Palette
import com.onedayonesecond.ui.theme.TextAction
import com.onedayonesecond.ui.theme.SwitchRow
import com.onedayonesecond.ui.theme.QuietButton
import com.onedayonesecond.ui.theme.RoseButton
import com.onedayonesecond.ui.theme.ScreenPadding
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

/**
 * Everything that can be added to a day: its second, some writing, a voice note, a
 * photo, a pen page. Opening it for the first time is also when the app asks where
 * the phone is, since that is the first moment a place line could be written.
 */
@UnstableApi
@Composable
fun AddSheet(
    date: LocalDate,
    place: String?,
    hasClip: Boolean,
    onDismiss: () -> Unit,
    onRecord: () -> Unit,
    onWrite: () -> Unit,
    onVoice: () -> Unit,
    onDraw: () -> Unit,
    onPhotosAdded: () -> Unit,
) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as OneDayApp).container }
    val isToday = date == LocalDate.now()

    var choosingPhoto by rememberSaveable { mutableStateOf(false) }
    var notebook by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    // Where the camera app is asked to put a photo; kept across the trip to that app.
    var photoPath by rememberSaveable { mutableStateOf<String?>(null) }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { }
    LaunchedEffect(Unit) {
        if (isToday && !container.places.hasPermission && !container.journalPrefs.locationAsked() &&
            container.journalPrefs.tagPlaces.first()
        ) {
            container.journalPrefs.setLocationAsked()
            locationLauncher.launch(PlaceFinder.PERMISSIONS)
        }
    }

    fun save(sources: List<android.net.Uri>) {
        if (sources.isEmpty()) return
        saving = true
        // On the app's scope: the photos are still saved if this sheet goes away meanwhile.
        container.appScope.launch {
            val added = container.journalActions.addPhotos(date, sources, notebook)
            photoPath?.let { File(it).delete() }
            saving = false
            if (added.isEmpty()) {
                container.messages.show("That photo could not be read. Nothing was added.")
            } else {
                onPhotosAdded()
            }
        }
    }

    val pickPhotos = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_PHOTOS_AT_ONCE),
    ) { uris -> save(uris) }

    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val path = photoPath
        if (taken && path != null) save(listOf(File(path).toUri()))
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

    val dayName = if (isToday) "today" else RecapLabels.dayAndMonth(date)
    AppSheet(
        onDismiss = onDismiss,
        title = if (choosingPhoto) "Add a photo" else "Add to $dayName",
        // "Monday, 16 November · Kemang": the neighbourhood is enough here.
        meta = listOfNotNull(RecapLabels.weekdayAndDay(date), place?.substringBefore(',')?.trim())
            .joinToString(" · "),
    ) {
        if (choosingPhoto) {
            SwitchRow(
                title = "It's a notebook page",
                checked = notebook,
                onCheckedChange = { notebook = it },
                subtitle = "Its handwriting is typed out for you. The photo is sent to the app's AI service to be read.",
            )
            Column(
                Modifier.padding(horizontal = ScreenPadding).padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QuietButton(
                    "Take a photo",
                    onClick = {
                        val allowed = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                            PackageManager.PERMISSION_GRANTED
                        if (allowed) openCamera() else cameraPermission.launch(Manifest.permission.CAMERA)
                    },
                    enabled = !saving,
                    icon = LineIcons.Camera,
                    modifier = Modifier.fillMaxWidth(),
                )
                QuietButton(
                    "Choose from your photos",
                    onClick = {
                        pickPhotos.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    enabled = !saving,
                    icon = LineIcons.Image,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (saving) MetaText("Saving…")
                TextAction("Back", { choosingPhoto = false }, Modifier.fillMaxWidth(), enabled = !saving)
            }
        } else {
            RoseButton(
                text = when {
                    hasClip && isToday -> "Retake today's second"
                    hasClip -> "Retake this day's second"
                    isToday -> "Record today's second"
                    else -> "Record this day's second"
                },
                onClick = onRecord,
                icon = LineIcons.Camera,
                modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenPadding),
            )
            Text(
                "Or add an entry",
                style = AppType.TitleSmall,
                color = Palette.Muted,
                modifier = Modifier.padding(start = ScreenPadding, top = 20.dp, bottom = 4.dp),
            )
            ActionRow("Write", onWrite, icon = LineIcons.Write)
            ActionRow(
                "Voice note",
                onVoice,
                subtitle = "Indonesian or English, typed out for you",
                icon = LineIcons.Mic,
            )
            ActionRow(
                "Photo",
                onClick = { choosingPhoto = true },
                subtitle = "Notebook pages are typed out for you",
                icon = LineIcons.Image,
            )
            ActionRow("Draw with S Pen", onDraw, icon = LineIcons.Pen)
        }
    }
}

private const val MAX_PHOTOS_AT_ONCE = 10
