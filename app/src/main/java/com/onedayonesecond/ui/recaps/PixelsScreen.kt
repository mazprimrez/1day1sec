package com.onedayonesecond.ui.recaps

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.onedayonesecond.journal.PixelKind
import com.onedayonesecond.ui.theme.HeadlineText
import com.onedayonesecond.ui.theme.SmallText
import kotlin.math.roundToInt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.util.UnstableApi
import com.onedayonesecond.AppContainer
import com.onedayonesecond.OneDayApp
import com.onedayonesecond.data.Mood
import com.onedayonesecond.journal.JournalMath
import com.onedayonesecond.journal.Pixels
import com.onedayonesecond.ui.nav.Routes
import com.onedayonesecond.ui.theme.AppType
import com.onedayonesecond.ui.theme.BackBar
import com.onedayonesecond.ui.theme.IconAction
import com.onedayonesecond.ui.theme.LineIcons
import com.onedayonesecond.ui.theme.MetaText
import com.onedayonesecond.ui.theme.Palette
import com.onedayonesecond.ui.theme.RoseButton
import com.onedayonesecond.ui.theme.ScreenPadding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

data class PixelsUiState(
    val year: Int,
    val moods: Map<LocalDate, Mood> = emptyMap(),
    val today: LocalDate = LocalDate.now(),
    val saving: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@UnstableApi
class PixelsViewModel(
    private val container: AppContainer,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val year = MutableStateFlow(savedStateHandle.get<Int>(Routes.ARG_YEAR) ?: LocalDate.now().year)
    private val saving = MutableStateFlow(false)

    private val moods = year.flatMapLatest { y ->
        container.journal.observeMetaBetween(LocalDate.of(y, 1, 1), LocalDate.of(y, 12, 31))
            .map { metas -> metas.mapNotNull { meta -> meta.mood?.let { meta.date to it } }.toMap() }
    }

    val state: StateFlow<PixelsUiState> = combine(year, moods, saving) { y, moods, saving ->
        PixelsUiState(year = y, moods = moods, saving = saving)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PixelsUiState(year.value))

    fun showYear(target: Int) {
        if (target <= LocalDate.now().year) year.value = target
    }

    /** Saves the picture to the gallery and says where it went. */
    fun download() {
        if (saving.value) return
        saving.value = true
        val current = state.value
        container.appScope.launch {
            try {
                container.pixelsExporter.export(current.year, current.moods, LocalDate.now())
                container.messages.show("${current.year} in pixels is saved to Pictures/Kilasan.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                container.messages.show("The picture could not be saved. ${e.message.orEmpty()}".trim())
            } finally {
                saving.value = false
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as OneDayApp
                PixelsViewModel(app.container, createSavedStateHandle())
            }
        }
    }
}

/**
 * A whole year on one page: a column for each month, a row for each day, each square
 * the colour of that day's mood. A day without a mood is a dashed outline; days still
 * to come are left empty. Tapping a square opens its day.
 */
@OptIn(ExperimentalLayoutApi::class)
@UnstableApi
@Composable
fun PixelsScreen(
    onBack: () -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    viewModel: PixelsViewModel = viewModel(factory = PixelsViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val counts = JournalMath.moodCounts(state.moods.values)
    val locale = Locale.getDefault()
    val isThisYear = state.year == state.today.year
    // Days that have happened and have no mood.
    val unmarked = remember(state.year, state.moods, state.today) {
        val last = if (isThisYear) state.today else LocalDate.of(state.year, 12, 31)
        if (state.year > state.today.year) 0 else last.dayOfYear - state.moods.keys.count { !it.isAfter(last) }
    }
    val bright = (counts[Mood.GOOD] ?: 0) + (counts[Mood.GREAT] ?: 0)
    val share = if (state.moods.isEmpty()) null else (bright * 100f / state.moods.size).roundToInt()

    // Only phones before Android 10 need permission to save a picture to the gallery.
    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.download()
    }

    Column(Modifier.fillMaxSize()) {
        BackBar(onBack, title = "Year in pixels")
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = ScreenPadding, top = 4.dp, end = ScreenPadding, bottom = 36.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HeadlineText(if (isThisYear) "${state.year} so far" else state.year.toString(), Modifier.weight(1f))
                IconAction(LineIcons.ChevronLeft, "Earlier year", { viewModel.showYear(state.year - 1) })
                IconAction(
                    LineIcons.ChevronRight,
                    "Later year",
                    { viewModel.showYear(state.year + 1) },
                    enabled = state.year < state.today.year,
                    modifier = Modifier.offset(x = 12.dp),
                )
            }
            MetaText(
                "One square per day, from the mood you picked. Darker is a harder day." +
                    when {
                        share == null -> ""
                        isThisYear -> " $share% of your days so far were good or great."
                        else -> " $share% of your days were good or great."
                    },
                Modifier.padding(top = 4.dp),
                color = Palette.Ink,
            )

            // The grid is one picture to a screen reader; the legend below says the same in words.
            val summary = Mood.entries.reversed().joinToString(", ") { "${counts[it] ?: 0} ${it.label.lowercase()}" }
            Column(
                Modifier
                    .padding(top = 20.dp)
                    .semantics { contentDescription = "Mood for every day of ${state.year}, one column per month. $summary." },
            ) {
                Row(Modifier.clearAndSetSemantics { }, horizontalArrangement = Arrangement.spacedBy(PIXEL_GAP)) {
                    Spacer(Modifier.width(DAY_LABEL_WIDTH))
                    (1..Pixels.MONTHS).forEach { month ->
                        Text(
                            Month.of(month).getDisplayName(TextStyle.NARROW, locale),
                            style = AppType.FrameNumber,
                            color = Palette.Muted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Row(
                    Modifier.padding(top = 4.dp).height(PIXEL_HEIGHT * Pixels.DAYS + PIXEL_GAP * (Pixels.DAYS - 1)),
                    horizontalArrangement = Arrangement.spacedBy(PIXEL_GAP),
                ) {
                    Column(
                        Modifier.width(DAY_LABEL_WIDTH).fillMaxHeight().clearAndSetSemantics { },
                        verticalArrangement = Arrangement.spacedBy(PIXEL_GAP),
                    ) {
                        (1..Pixels.DAYS).forEach { day ->
                            Box(Modifier.height(PIXEL_HEIGHT).fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                                // The 1st and every fifth day are numbered.
                                if (day == 1 || day % 5 == 0) {
                                    Text(day.toString(), style = AppType.FrameNumber, color = Palette.Muted)
                                }
                            }
                        }
                    }
                    Canvas(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .pointerInput(state.year, state.today) {
                                detectTapGestures { tap ->
                                    val month = (tap.x / size.width * Pixels.MONTHS).toInt() + 1
                                    val day = (tap.y / size.height * Pixels.DAYS).toInt() + 1
                                    if (month in 1..Pixels.MONTHS && day in 1..Pixels.DAYS) {
                                        val kind = Pixels.at(state.year, month, day, state.moods, state.today).kind
                                        if (kind == PixelKind.MOOD || kind == PixelKind.EMPTY) {
                                            onOpenDay(LocalDate.of(state.year, month, day))
                                        }
                                    }
                                }
                            },
                    ) {
                        drawPixels(state.year, state.moods, state.today, gap = PIXEL_GAP.toPx(), corner = 4.dp.toPx())
                    }
                }
            }

            // What each colour is, with how many days wear it. The words carry the meaning;
            // the colour only repeats it.
            FlowRow(
                Modifier.padding(top = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Mood.entries.reversed().forEach { mood ->
                    LegendItem(mood.label, counts[mood] ?: 0) {
                        Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(Palette.mood(mood)))
                    }
                }
                LegendItem("No entry", unmarked) {
                    Canvas(Modifier.size(12.dp)) {
                        val line = 1.dp.toPx()
                        drawRoundRect(
                            Palette.PixelEmpty,
                            topLeft = Offset(line / 2, line / 2),
                            size = Size(size.width - line, size.height - line),
                            cornerRadius = CornerRadius(3.dp.toPx()),
                            style = Stroke(line, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx()))),
                        )
                    }
                }
            }

            RoseButton(
                if (state.saving) "Saving…" else "Download PNG",
                onClick = {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    } else {
                        viewModel.download()
                    }
                },
                enabled = !state.saving,
                icon = LineIcons.Download,
                modifier = Modifier.fillMaxWidth().padding(top = 28.dp),
            )
            SmallText(
                "Saves a 1080 × 1920 image to your gallery, sized for stories.",
                Modifier.fillMaxWidth().padding(top = 10.dp).wrapContentWidth(Alignment.CenterHorizontally),
            )
        }
    }
}

/** A swatch, what it stands for, and how many days it covers. */
@Composable
private fun LegendItem(label: String, days: Int, swatch: @Composable () -> Unit) {
    Row(
        Modifier.semantics(mergeDescendants = true) { contentDescription = "$label, $days day${if (days == 1) "" else "s"}" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        swatch()
        Text(label, style = AppType.Tab, color = Palette.Ink, modifier = Modifier.padding(start = 6.dp).clearAndSetSemantics { })
        Text(
            days.toString(),
            style = AppType.Small,
            color = Palette.Muted,
            modifier = Modifier.padding(start = 6.dp).clearAndSetSemantics { },
        )
    }
}

private val DAY_LABEL_WIDTH = 22.dp
private val PIXEL_HEIGHT = 20.dp
private val PIXEL_GAP = 3.dp
