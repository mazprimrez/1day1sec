package com.onedayonesecond.ui.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
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
import com.onedayonesecond.ui.theme.AppType
import com.onedayonesecond.ui.theme.IconAction
import com.onedayonesecond.ui.theme.LineIcons
import com.onedayonesecond.ui.theme.MetaText
import com.onedayonesecond.ui.theme.Palette
import com.onedayonesecond.ui.theme.QuietButton
import com.onedayonesecond.ui.theme.RoseButton
import com.onedayonesecond.ui.theme.ScreenPadding
import com.onedayonesecond.ui.theme.TileShape
import com.onedayonesecond.ui.theme.TitleText
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
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
    onRecentlyDeleted: () -> Unit,
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val today = LocalDate.now()

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenPadding),
        ) {
            MonthHeader(
                month = state.month,
                kept = state.keptInMonth,
                daysSoFar = state.daysSoFar,
                canGoForward = state.month < YearMonth.from(today),
                onPrevious = viewModel::showPreviousMonth,
                onNext = viewModel::showNextMonth,
            )
            Spacer(Modifier.height(16.dp))
            WeekdayLabels()
            Spacer(Modifier.height(6.dp))
            CalendarGrid(state = state, today = today, onDayClick = onOpenDay)
            Spacer(Modifier.height(16.dp))
            GalleryHint(
                state = state,
                showDeniedHint = showDeniedHint,
                onShowLatest = viewModel::showMonth,
                onFind = { galleryPermissionLauncher.launch(GalleryAccess.permissionsToRequest()) },
            )
            if (state.deletedCount > 0) {
                QuietButton(
                    "Recently deleted (${state.deletedCount})",
                    onRecentlyDeleted,
                    Modifier.padding(top = 8.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        RoseButton(
            "Add to today",
            onClick = { adding = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ScreenPadding)
                .padding(top = 8.dp, bottom = 12.dp),
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
) {
    val locale = Locale.getDefault()
    // The year is only worth saying when it is not this one.
    val pattern = if (month.year == LocalDate.now().year) "MMMM" else "MMMM yyyy"
    val name = month.atDay(1).format(DateTimeFormatter.ofPattern(pattern, locale))

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            TitleText(name)
            MetaText(
                when {
                    daysSoFar == 0 -> "Nothing here yet"
                    else -> "$kept of $daysSoFar day${if (daysSoFar == 1) "" else "s"} kept"
                },
            )
        }
        IconAction(LineIcons.ChevronLeft, "Previous month", onPrevious)
        IconAction(LineIcons.ChevronRight, "Next month", onNext, enabled = canGoForward)
    }
}

/** Explains the dotted days, or why there are none. Shows nothing when there is nothing to say. */
@Composable
private fun GalleryHint(
    state: HomeUiState,
    showDeniedHint: Boolean,
    onShowLatest: (YearMonth) -> Unit,
    onFind: () -> Unit,
) {
    val days = state.unusedGalleryDays
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            state.galleryAccess == GalleryAccessLevel.NONE && showDeniedHint -> MetaText(
                "Gallery access was not allowed. To find videos for past days, allow " +
                    "Photos and videos for 1day1sec in your phone's Settings, under Apps.",
                color = Palette.Ink,
            )

            days.isNotEmpty() -> Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(Palette.Rose))
                MetaText(
                    "${days.size} missed day${if (days.size == 1) " has" else "s have"} videos in your gallery",
                    Modifier.weight(1f).padding(start = 8.dp),
                )
            }
        }
        if (state.galleryAccess == GalleryAccessLevel.PARTIAL) {
            MetaText("Only the videos you shared with the app can be seen. Tap Find videos to share more.")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuietButton(
                if (state.galleryAccess == GalleryAccessLevel.NONE) "Find videos for missed days" else "Find videos",
                onFind,
            )
            if (days.isNotEmpty() && YearMonth.from(days.first()) != state.month) {
                QuietButton("Show latest", { onShowLatest(YearMonth.from(days.first())) })
            }
        }
    }
}

@Composable
private fun WeekdayLabels() {
    Row(Modifier.fillMaxWidth().clearAndSetSemantics { }) {
        DayOfWeek.entries.forEach { day ->
            Text(
                text = day.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                modifier = Modifier.weight(1f),
                style = AppType.Meta,
                color = Palette.Muted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Monday first. Tiles are portrait, like the clips they stand for. */
@UnstableApi
@Composable
private fun CalendarGrid(
    state: HomeUiState,
    today: LocalDate,
    onDayClick: (LocalDate) -> Unit,
) {
    val firstDay = state.month.atDay(1)
    // DayOfWeek.MONDAY.value is 1, so this many blanks pad the first row.
    val leadingBlanks = firstDay.dayOfWeek.value - 1
    val cells = buildList<LocalDate?> {
        repeat(leadingBlanks) { add(null) }
        (1..state.month.lengthOfMonth()).forEach { add(state.month.atDay(it)) }
        while (size % 7 != 0) add(null)
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        cells.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                week.forEach { date ->
                    if (date == null) {
                        Spacer(Modifier.weight(1f).height(DAY_TILE_HEIGHT))
                    } else {
                        val clip = state.clipsByDate[date]
                        DayCell(
                            date = date,
                            clip = clip,
                            isToday = date == today,
                            isFuture = date.isAfter(today),
                            // A day that already has a clip is done; only empty days are
                            // offered gallery videos.
                            hasGalleryVideos = clip == null && (state.galleryCounts[date] ?: 0) > 0,
                            onClick = { onDayClick(date) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@UnstableApi
@Composable
private fun DayCell(
    date: LocalDate,
    clip: ClipEntity?,
    isToday: Boolean,
    isFuture: Boolean,
    hasGalleryVideos: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val kept = clip != null
    val picture = rememberClipThumbnail(clip)
    val status = when {
        isFuture -> "Not yet"
        kept && isToday -> "Today, second kept"
        kept -> "Second kept"
        isToday -> "Today, no second yet"
        hasGalleryVideos -> "No second, videos in your gallery"
        else -> "No second"
    }

    Box(
        modifier = modifier
            .height(DAY_TILE_HEIGHT)
            .clip(TileShape)
            .then(
                when {
                    // Today wears a rose ring whether or not it is kept yet.
                    isToday -> Modifier.border(2.dp, Palette.Rose, TileShape)
                    // A missed day is an empty outline; a future day is only its number.
                    !kept && !isFuture -> Modifier.border(1.dp, Palette.Hairline, TileShape)
                    else -> Modifier
                },
            )
            .clickable(enabled = !isFuture, role = Role.Button, onClick = onClick)
            // The whole tile is announced as the full date, then what state it is in.
            .clearAndSetSemantics {
                contentDescription = RecapLabels.fullDate(date)
                stateDescription = status
                if (!isFuture) role = Role.Button
            },
    ) {
        // Blush until the picture arrives, and for good if the clip cannot be read. The
        // number is only turned white once there really is a picture under it.
        if (kept) Box(Modifier.fillMaxSize().background(Palette.Blush))
        if (picture != null) {
            Image(picture, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            // Fades in from the bottom so the number reads on any picture.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.45f to Color.Transparent,
                            1f to Palette.Ink.copy(alpha = 0.55f),
                        ),
                    ),
            )
        }
        Text(
            text = date.dayOfMonth.toString(),
            style = if (picture != null) {
                AppType.Meta.copy(shadow = Shadow(Palette.Ink.copy(alpha = 0.6f), Offset(0f, 1f), 3f))
            } else {
                AppType.Meta
            },
            color = when {
                picture != null -> Palette.Paper
                isFuture -> Palette.Muted
                else -> Palette.Ink
            },
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 6.dp, bottom = 4.dp),
        )
        if (hasGalleryVideos) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(Palette.Rose),
            )
        }
    }
}

private val DAY_TILE_HEIGHT = 60.dp
