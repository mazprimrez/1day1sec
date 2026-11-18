package com.onedayonesecond.ui.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.onedayonesecond.ui.theme.ContactFrame
import com.onedayonesecond.ui.theme.ContactSheet
import com.onedayonesecond.ui.theme.FabClearance
import com.onedayonesecond.ui.theme.FloatingAction
import com.onedayonesecond.ui.theme.FrameKind
import com.onedayonesecond.ui.theme.SmallText
import com.onedayonesecond.ui.theme.TabHeader
import com.onedayonesecond.ui.theme.TextAction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import com.onedayonesecond.data.ClipEntity
import com.onedayonesecond.journal.RecapLabels
import com.onedayonesecond.media.GalleryAccess
import com.onedayonesecond.media.GalleryAccessLevel
import com.onedayonesecond.ui.common.rememberClipThumbnail
import com.onedayonesecond.ui.day.AddSheet
import com.onedayonesecond.ui.theme.IconAction
import com.onedayonesecond.ui.theme.LineIcons
import com.onedayonesecond.ui.theme.QuietButton
import com.onedayonesecond.ui.theme.ScreenPadding
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The Days tab: one month of the calendar, and the way in to adding something to today. */
@UnstableApi
@Composable
fun HomeScreen(
    onOpenDay: (LocalDate) -> Unit,
    onRecord: (LocalDate) -> Unit,
    onWrite: (LocalDate) -> Unit,
    onVoice: (LocalDate) -> Unit,
    onDraw: (LocalDate) -> Unit,
    onSettings: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showDeniedHint by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }

    // Finding past days that have videos means reading the gallery index, which
    // needs permission. It is only asked for when the user taps "Find videos".
    val galleryPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        showDeniedHint = GalleryAccess.level(context) == GalleryAccessLevel.NONE
        viewModel.refreshGallery()
    }

    // Also runs on coming back from another screen, so a video shot a moment ago
    // is picked up. Without permission it quietly finds nothing.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshGallery() }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(Unit) {
        // Only the reminder needs it, so with the reminder switched off in Settings there is nothing to ask.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && viewModel.reminderEnabled()) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val today = LocalDate.now()

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            MonthHeader(
                month = state.month,
                kept = state.keptInMonth,
                daysSoFar = state.daysSoFar,
                canGoForward = state.month < YearMonth.from(today),
                onPrevious = viewModel::showPreviousMonth,
                onNext = viewModel::showNextMonth,
                onSettings = onSettings,
            )
            MonthSheet(
                state = state,
                today = today,
                onDayClick = onOpenDay,
                modifier = Modifier.padding(horizontal = SHEET_MARGIN),
            )
            Column(Modifier.padding(horizontal = ScreenPadding).padding(top = 16.dp)) {
                GalleryHint(
                    state = state,
                    showDeniedHint = showDeniedHint,
                    onShowLatest = viewModel::showMonth,
                    onFind = { galleryPermissionLauncher.launch(GalleryAccess.permissionsToRequest()) },
                )
            }
            Spacer(Modifier.height(FabClearance))
        }

        FloatingAction(
            "Add today",
            LineIcons.Plus,
            onClick = { adding = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 16.dp),
        )
    }

    if (adding) {
        AddSheet(
            date = today,
            place = state.todayPlace,
            hasClip = state.hasClipToday,
            onDismiss = { adding = false },
            onRecord = {
                adding = false
                onRecord(today)
            },
            onWrite = {
                adding = false
                onWrite(today)
            },
            onVoice = {
                adding = false
                onVoice(today)
            },
            onDraw = {
                adding = false
                onDraw(today)
            },
            onPhotosAdded = {
                adding = false
                onOpenDay(today)
            },
        )
    }
}

@Composable
private fun MonthHeader(
    month: YearMonth,
    kept: Int,
    daysSoFar: Int,
    canGoForward: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSettings: () -> Unit,
) {
    val name = month.atDay(1).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))
    TabHeader(
        title = name,
        subtitle = when {
            daysSoFar == 0 -> "Nothing here yet"
            else -> "$kept of $daysSoFar day${if (daysSoFar == 1) "" else "s"} kept"
        },
    ) {
        IconAction(LineIcons.ChevronLeft, "Previous month", onPrevious)
        IconAction(LineIcons.ChevronRight, "Next month", onNext, enabled = canGoForward)
        IconAction(LineIcons.Settings, "Settings", onSettings)
    }
}

/**
 * What the dashed frames are, and how videos already in the gallery can fill them.
 * Says only as much as the state of gallery access calls for.
 */
@Composable
private fun GalleryHint(
    state: HomeUiState,
    showDeniedHint: Boolean,
    onShowLatest: (YearMonth) -> Unit,
    onFind: () -> Unit,
) {
    val days = state.unusedGalleryDays
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SmallText(
            when {
                state.galleryAccess == GalleryAccessLevel.NONE && showDeniedHint ->
                    "Gallery access was not allowed. To find videos for missed days, allow " +
                        "Photos and videos for Kilasan in your phone's Settings, under Apps."

                days.isNotEmpty() ->
                    "Dashed frames are missed days. ${days.size} of them " +
                        "${if (days.size == 1) "has" else "have"} videos in your gallery, marked with a dot. " +
                        "Tap one to add its second."

                state.galleryAccess == GalleryAccessLevel.NONE ->
                    "Dashed frames are missed days. Find videos in your gallery to fill them."

                else -> "Dashed frames are missed days. Tap one to add a second from your gallery."
            },
        )
        if (state.galleryAccess == GalleryAccessLevel.PARTIAL) {
            SmallText("Only the videos you shared with the app can be seen. Tap Find videos to share more.")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuietButton(
                if (state.galleryAccess == GalleryAccessLevel.NONE) "Find videos for missed days" else "Find videos",
                onFind,
            )
            if (days.isNotEmpty() && YearMonth.from(days.first()) != state.month) {
                TextAction("Show latest", { onShowLatest(YearMonth.from(days.first())) })
            }
        }
    }
}

/**
 * The month as a contact sheet: every day a numbered frame, six to a row, in order.
 * Frames are portrait, like the clips they stand for.
 */
@UnstableApi
@Composable
private fun MonthSheet(
    state: HomeUiState,
    today: LocalDate,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    ContactSheet(count = state.month.lengthOfMonth(), modifier = modifier) { index, cell ->
        val date = state.month.atDay(index + 1)
        val clip = state.clipsByDate[date]
        val mood = state.moodsByDate[date]
        val isToday = date == today
        // A day that already has a clip is done; only empty days are offered gallery videos.
        val hasGalleryVideos = clip == null && (state.galleryCounts[date] ?: 0) > 0
        ContactFrame(
            kind = when {
                clip != null -> FrameKind.Kept
                date.isAfter(today) -> FrameKind.Future
                else -> FrameKind.Missed
            },
            // The whole frame is announced as the full date, then what is in it.
            label = RecapLabels.fullDate(date),
            stateLabel = when {
                date.isAfter(today) -> "Not yet"
                clip != null -> listOfNotNull(
                    if (isToday) "Today, second kept" else "Second kept",
                    mood?.let { "mood ${it.label.lowercase()}" },
                ).joinToString(", ")
                isToday -> "Today, no second yet"
                hasGalleryVideos -> "No second, videos in your gallery"
                else -> "No second"
            },
            number = "%02d".format(date.dayOfMonth),
            today = isToday,
            mood = mood,
            marked = hasGalleryVideos,
            onClick = { onDayClick(date) },
            modifier = cell,
        ) {
            ClipPicture(clip)
        }
    }
}

/** The still of a day's clip, filling its frame. Nothing is drawn until it has loaded. */
@UnstableApi
@Composable
private fun ClipPicture(clip: ClipEntity?) {
    val picture = rememberClipThumbnail(clip)
    if (picture != null) {
        Image(picture, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

/** The sheet runs closer to the screen's edge than text does, so six frames stay a good size. */
private val SHEET_MARGIN = 16.dp
