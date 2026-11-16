package com.onedayonesecond.ui.recaps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.util.UnstableApi
import com.onedayonesecond.AppContainer
import com.onedayonesecond.OneDayApp
import com.onedayonesecond.data.Recap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class RecapsUiState(
    /** Every year there is anything from, oldest first, always ending with this year. */
    val years: List<Int> = listOf(LocalDate.now().year),
    /** The recaps that have been written, by key. */
    val recaps: Map<String, Recap> = emptyMap(),
    /** What letters call the user. Empty until they give a name. */
    val name: String = "",
    val today: LocalDate = LocalDate.now(),
)

@UnstableApi
class RecapsViewModel(private val container: AppContainer) : ViewModel() {

    private val firstYear = MutableStateFlow(LocalDate.now().year)

    val state: StateFlow<RecapsUiState> = combine(
        firstYear,
        container.journal.observeRecaps(),
        container.journalPrefs.name,
    ) { first, recaps, name ->
        val today = LocalDate.now()
        RecapsUiState(
            years = (minOf(first, today.year)..today.year).toList(),
            recaps = recaps.associateBy { it.key },
            name = name,
            today = today,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecapsUiState())

    init {
        refresh()
    }

    /** Finds the earliest year with a second or an entry, so earlier years can be swiped to. */
    fun refresh() {
        viewModelScope.launch {
            val earliest = listOfNotNull(
                container.repository.earliestDate(),
                container.journal.earliestEntryDate(),
            ).minOrNull()
            firstYear.value = earliest?.year ?: LocalDate.now().year
        }
    }

    fun setName(name: String) {
        viewModelScope.launch { container.journalPrefs.setName(name) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as OneDayApp
                RecapsViewModel(app.container)
            }
        }
    }
}
