package com.onedayonesecond.ui.recaps

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import com.onedayonesecond.ui.theme.TitleText
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
 * A whole year on one page: a column for each month, a row for each day, each cell
 * the colour of that day's mood. A day without a mood is an empty outline; days still
 * to come are faint dots.
 */
@UnstableApi
@Composable
fun PixelsScreen(
    onBack: () -> Unit,
    viewModel: PixelsViewModel = viewModel(factory = PixelsViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val counts = JournalMath.moodCounts(state.moods.values)
    val locale = Locale.getDefault()

    // Only phones before Android 10 need permission to save a picture to the gallery.
    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.download()
    }

    Column(Modifier.fillMaxSize()) {
        BackBar(onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Row(
                Modifier.padding(start = ScreenPadding, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TitleText("${state.year} in pixels", Modifier.weight(1f))
                IconAction(LineIcons.ChevronLeft, "Earlier year", { viewModel.showYear(state.year - 1) })
                IconAction(
                    LineIcons.ChevronRight,
                    "Later year",
                    { viewModel.showYear(state.year + 1) },
                    enabled = state.year < state.today.year,
                )
            }

            // The grid is one picture to a screen reader; the legend below says the same in words.
            val summary = Mood.entries.reversed().joinToString(", ") { "${counts[it] ?: 0} ${it.label.lowercase()}" }
            Column(
                Modifier
                    .padding(horizontal = ScreenPadding)
                    .padding(top = 16.dp)
                    .clearAndSetSemantics { contentDescription = "Moods of ${state.year}, day by day. $summary." },
            ) {
                Row {
                    Spacer(Modifier.width(DAY_LABEL_WIDTH))
                    (1..Pixels.MONTHS).forEach { month ->
                        Text(
                            Month.of(month).getDisplayName(TextStyle.NARROW, locale),
                            style = AppType.Meta,
                            color = Palette.Muted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                // The grid's height follows from its width, so it is worked out here and
                // given to the day numbers too, which keeps each number level with its row.
                BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    val gridHeight = (maxWidth - DAY_LABEL_WIDTH) * Pixels.DAYS / Pixels.MONTHS
                    Row(Modifier.height(gridHeight)) {
                        Column(Modifier.width(DAY_LABEL_WIDTH).fillMaxHeight()) {
                            (1..Pixels.DAYS).forEach { day ->
                                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                                    // The 1st and every fifth day are numbered.
                                    if (day == 1 || day % 5 == 0) {
                                        Text(day.toString(), style = AppType.Meta, color = Palette.Muted)
                                    }
                                }
                            }
                        }
                        Canvas(Modifier.weight(1f).fillMaxHeight()) {
                            drawPixels(state.year, state.moods, state.today, gapFraction = 0.14f)
                        }
                    }
                }
            }

            Column(
                Modifier.padding(horizontal = ScreenPadding).padding(top = 24.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Mood.entries.reversed().forEach { mood ->
                    val days = counts[mood] ?: 0
                    Row(
                        Modifier.semantics(mergeDescendants = true) { },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(20.dp).clip(RoundedCornerShape(4.dp)).background(Palette.mood(mood)))
                        Text(mood.label, style = AppType.Body, color = Palette.Ink, modifier = Modifier.padding(start = 12.dp).weight(1f))
                        MetaText("$days day${if (days == 1) "" else "s"}")
                    }
                }
                MetaText("An empty outline is a day without a mood. Darker is a harder day.")
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
            modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenPadding, vertical = 12.dp),
        )
    }
}

private val DAY_LABEL_WIDTH = 28.dp
