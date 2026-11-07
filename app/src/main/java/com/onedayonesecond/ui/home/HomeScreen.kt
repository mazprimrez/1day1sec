package com.onedayonesecond.ui.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle as ComposeTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import com.onedayonesecond.data.ClipEntity
import com.onedayonesecond.media.GalleryAccess
import com.onedayonesecond.media.GalleryAccessLevel
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val MonthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
fun HomeScreen(
    onRecord: (LocalDate) -> Unit,
    onCompile: () -> Unit,
    onBackup: () -> Unit,
    onPickFromGallery: (LocalDate) -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<ClipEntity?>(null) }
    var showDeniedHint by remember { mutableStateOf(false) }

    // Finding past days that have videos means reading the gallery index, which
    // needs permission. It is only asked for when the user taps the gallery icon.
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("1day1sec") },
                actions = {
                    IconButton(
                        onClick = {
                            galleryPermissionLauncher.launch(GalleryAccess.permissionsToRequest())
                        },
                    ) {
                        Icon(
                            Icons.Filled.PhotoLibrary,
                            contentDescription = "Find videos in your gallery for past days",
                        )
                    }
                    IconButton(onClick = onBackup) {
                        Icon(Icons.Filled.CloudUpload, contentDescription = "Back up to Google Drive")
                    }
                    IconButton(onClick = onCompile) {
                        Icon(Icons.Filled.MovieCreation, contentDescription = "Compile reel")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onRecord(LocalDate.now()) },
                icon = { Icon(Icons.Filled.Videocam, contentDescription = null) },
                text = { Text(if (state.hasClipToday) "Retake today" else "Record today") },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            MonthHeader(
                title = state.month.atDay(1).format(MonthFormatter),
                subtitle = "${state.totalClips} second${if (state.totalClips == 1) "" else "s"} captured",
                onPrevious = viewModel::showPreviousMonth,
                onNext = viewModel::showNextMonth,
            )

            GalleryHint(
                state = state,
                showDeniedHint = showDeniedHint,
                onShowLatest = viewModel::showMonth,
            )

            WeekdayLabels()

            CalendarGrid(
                state = state,
                onDayClick = onRecord,
                onGalleryDayClick = onPickFromGallery,
                onDayLongClick = { pendingDelete = it },
                loadThumbnail = viewModel::thumbnailFor,
            )
        }
    }

    pendingDelete?.let { clip ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this second?") },
            text = { Text("The clip for ${clip.date} will be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteClip(clip)
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun MonthHeader(
    title: String,
    subtitle: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous month")
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        IconButton(onClick = onNext) {
            Icon(Icons.Filled.ChevronRight, contentDescription = "Next month")
        }
    }
}

/** Explains the highlighted days, or why there are none. Shows nothing when there is nothing to say. */
@Composable
private fun GalleryHint(
    state: HomeUiState,
    showDeniedHint: Boolean,
    onShowLatest: (YearMonth) -> Unit,
) {
    val days = state.unusedGalleryDays
    Column(Modifier.fillMaxWidth()) {
        when {
            state.galleryAccess == GalleryAccessLevel.NONE && showDeniedHint -> Text(
                "Gallery access was not allowed. To find videos for past days, allow " +
                    "Photos and videos for 1day1sec in your phone's Settings → Apps.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )

            days.isNotEmpty() -> Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiary),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "${days.size} day${if (days.size == 1) "" else "s"} with videos in your gallery",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onShowLatest(YearMonth.from(days.first())) }) {
                    Text("Show latest")
                }
            }
        }
        if (state.galleryAccess == GalleryAccessLevel.PARTIAL) {
            Text(
                "Only the videos you shared with the app are visible. Tap the gallery icon to share more.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun WeekdayLabels() {
    Row(Modifier.fillMaxWidth()) {
        java.time.DayOfWeek.values().forEach { day ->
            Text(
                text = day.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CalendarGrid(
    state: HomeUiState,
    onDayClick: (LocalDate) -> Unit,
    onGalleryDayClick: (LocalDate) -> Unit,
    onDayLongClick: (ClipEntity) -> Unit,
    loadThumbnail: suspend (ClipEntity) -> android.graphics.Bitmap?,
) {
    val firstDay = state.month.atDay(1)
    // DayOfWeek.MONDAY.value is 1, so this many blanks pad the first row.
    val leadingBlanks = firstDay.dayOfWeek.value - 1
    val cells = buildList<LocalDate?> {
        repeat(leadingBlanks) { add(null) }
        (1..state.month.lengthOfMonth()).forEach { add(state.month.atDay(it)) }
    }
    val today = LocalDate.now()

    LazyVerticalGrid(
        columns = GridCells.Fixed(7),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(top = 8.dp),
    ) {
        items(cells) { date ->
            if (date == null) {
                Box(Modifier.aspectRatio(1f))
            } else {
                val clip = state.clipsByDate[date]
                val galleryCount = state.galleryCounts[date] ?: 0
                // A day that already has a clip is done; only empty days are offered
                // gallery videos.
                val hasGalleryVideos = clip == null && galleryCount > 0
                DayCell(
                    date = date,
                    clip = clip,
                    isToday = date == today,
                    isFuture = date.isAfter(today),
                    hasGalleryVideos = hasGalleryVideos,
                    loadThumbnail = loadThumbnail,
                    onClick = {
                        when {
                            date.isAfter(today) -> Unit
                            hasGalleryVideos -> onGalleryDayClick(date)
                            else -> onDayClick(date)
                        }
                    },
                    onLongClick = { state.clipsByDate[date]?.let(onDayLongClick) },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DayCell(
    date: LocalDate,
    clip: ClipEntity?,
    isToday: Boolean,
    isFuture: Boolean,
    hasGalleryVideos: Boolean,
    loadThumbnail: suspend (ClipEntity) -> android.graphics.Bitmap?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val filled = clip != null

    // Keyed on the recording, not just the day: retaking a day replaces the clip,
    // and the old picture must not linger. Until it arrives (or if the clip cannot
    // be read) the cell stays the plain filled tile.
    val thumbnail: ImageBitmap? by produceState<ImageBitmap?>(
        initialValue = null,
        clip?.filePath,
        clip?.createdAt,
    ) {
        value = clip?.let { loadThumbnail(it)?.asImageBitmap() }
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    filled -> colors.primary
                    hasGalleryVideos -> colors.tertiaryContainer
                    isFuture -> colors.surfaceVariant.copy(alpha = 0.3f)
                    else -> colors.surfaceVariant
                },
            )
            .then(
                if (isToday) Modifier.border(2.dp, colors.primary, RoundedCornerShape(10.dp))
                else Modifier,
            )
            .combinedClickable(
                enabled = !isFuture,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        val picture = thumbnail
        if (picture != null) {
            Image(
                bitmap = picture,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // Fades in from the top so the number reads on any picture, bright or dark.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.55f),
                            0.6f to Color.Transparent,
                        ),
                    ),
            )
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelMedium.merge(
                    ComposeTextStyle(shadow = Shadow(Color.Black, Offset(0f, 1f), 3f)),
                ),
                color = Color.White,
                modifier = Modifier.align(Alignment.TopStart).padding(start = 5.dp, top = 3.dp),
            )
        } else {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = when {
                    filled -> colors.onPrimary
                    hasGalleryVideos -> colors.onTertiaryContainer
                    else -> colors.onSurfaceVariant
                },
            )
        }
        if (hasGalleryVideos) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 5.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(colors.tertiary),
            )
        }
    }
}
