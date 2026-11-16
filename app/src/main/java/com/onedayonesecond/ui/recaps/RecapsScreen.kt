package com.onedayonesecond.ui.recaps

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import com.onedayonesecond.data.JournalPrefs
import com.onedayonesecond.data.Recap
import com.onedayonesecond.journal.RecapLabels
import com.onedayonesecond.journal.RecapPeriod
import com.onedayonesecond.journal.RecapRules
import com.onedayonesecond.ui.theme.ActionRow
import com.onedayonesecond.ui.theme.AppDialog
import com.onedayonesecond.ui.theme.AppType
import com.onedayonesecond.ui.theme.Chip
import com.onedayonesecond.ui.theme.Hairline
import com.onedayonesecond.ui.theme.HeadingText
import com.onedayonesecond.ui.theme.IconAction
import com.onedayonesecond.ui.theme.LineIcons
import com.onedayonesecond.ui.theme.MetaText
import com.onedayonesecond.ui.theme.Palette
import com.onedayonesecond.ui.theme.QuietButton
import com.onedayonesecond.ui.theme.RoseButton
import com.onedayonesecond.ui.theme.ScreenPadding
import com.onedayonesecond.ui.theme.appTextFieldColors
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * The Recaps tab: one page per year. Swipe sideways, or use the arrows by the year,
 * to reach earlier years. Each page has the year's letter, the year in pixels, and a
 * row for every month that opens out into its weeks.
 */
@UnstableApi
@Composable
fun RecapsScreen(
    onOpenRecap: (RecapPeriod) -> Unit,
    onOpenPixels: (Int) -> Unit,
    viewModel: RecapsViewModel = viewModel(factory = RecapsViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    val years = state.years
    val pager = rememberPagerState(initialPage = years.lastIndex) { years.size }
    val scope = rememberCoroutineScope()
    var editingName by remember { mutableStateOf(false) }

    // The list of years grows backwards once the earliest year is known; stay on this year.
    var settled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(years.size) {
        if (!settled && years.size > 1) pager.scrollToPage(years.lastIndex)
        if (years.size > 1) settled = true
    }

    HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
        val year = years.getOrNull(page) ?: return@HorizontalPager
        YearPage(
            year = year,
            state = state,
            canGoEarlier = page > 0,
            canGoLater = page < years.lastIndex,
            onEarlier = { scope.launch { pager.animateScrollToPage(page - 1) } },
            onLater = { scope.launch { pager.animateScrollToPage(page + 1) } },
            onOpenRecap = onOpenRecap,
            onOpenPixels = { onOpenPixels(year) },
            onEditName = { editingName = true },
        )
    }

    if (editingName) {
        NameDialog(
            current = state.name,
            onSave = {
                viewModel.setName(it)
                editingName = false
            },
            onDismiss = { editingName = false },
        )
    }
}

@Composable
private fun YearPage(
    year: Int,
    state: RecapsUiState,
    canGoEarlier: Boolean,
    canGoLater: Boolean,
    onEarlier: () -> Unit,
    onLater: () -> Unit,
    onOpenRecap: (RecapPeriod) -> Unit,
    onOpenPixels: () -> Unit,
    onEditName: () -> Unit,
) {
    val today = state.today
    val yearPeriod = RecapPeriod.Year(year)
    // Newest month first; this year stops at the month we are in.
    val lastMonth = if (year == today.year) today.monthValue else 12
    val months = (lastMonth downTo 1).map { YearMonth.of(year, it) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(
            Modifier.fillMaxWidth().padding(start = ScreenPadding, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                year.toString(),
                style = AppType.BigYear,
                color = Palette.Ink,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            IconAction(LineIcons.ChevronLeft, "Earlier year", onEarlier, enabled = canGoEarlier)
            IconAction(LineIcons.ChevronRight, "Later year", onLater, enabled = canGoLater)
        }

        YearLetter(
            recap = state.recaps[yearPeriod.key],
            year = year,
            isThisYear = year == today.year,
            onOpen = { onOpenRecap(yearPeriod) },
        )
        Hairline(Modifier.padding(horizontal = ScreenPadding))
        ActionRow(
            "Year in pixels",
            onOpenPixels,
            subtitle = "Every day's mood on one page",
            trailing = { Icon(LineIcons.ChevronRight, contentDescription = null, tint = Palette.Muted) },
        )
        Hairline(Modifier.padding(horizontal = ScreenPadding))

        HeadingText("Months and weeks", Modifier.padding(horizontal = ScreenPadding).padding(top = 24.dp, bottom = 4.dp))
        months.forEachIndexed { index, month ->
            if (index > 0) Hairline(Modifier.padding(horizontal = ScreenPadding))
            MonthRow(month = month, state = state, onOpenRecap = onOpenRecap)
        }

        Hairline(Modifier.padding(horizontal = ScreenPadding))
        Column(Modifier.padding(horizontal = ScreenPadding, vertical = 20.dp)) {
            MetaText(
                if (state.name.isBlank()) {
                    "Your letters don't know your name yet."
                } else {
                    "Your letters are addressed to ${state.name}."
                },
            )
            QuietButton(
                if (state.name.isBlank()) "Add your name" else "Change name",
                onEditName,
                Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** The year's recap: its title and quote, or when it will arrive. Always opens the year page. */
@Composable
private fun YearLetter(recap: Recap?, year: Int, isThisYear: Boolean, onOpen: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = "Open the letter for $year", onClick = onOpen)
            .padding(horizontal = ScreenPadding, vertical = 16.dp),
    ) {
        if (recap != null) {
            Text(recap.title, style = AppType.Heading, color = Palette.Ink)
            if (recap.quote.isNotEmpty()) {
                Text(
                    "“${recap.quote}”",
                    style = AppType.Quote.copy(fontSize = 22.sp, lineHeight = 28.sp),
                    color = Palette.Rose,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        } else {
            Text("The year's letter", style = AppType.Heading, color = Palette.Ink)
            MetaText(
                if (isThisYear) {
                    "Arrives on ${RecapLabels.dayAndMonth(RecapRules.arrives(RecapPeriod.Year(year)))}."
                } else {
                    "Not written yet."
                },
                Modifier.padding(top = 2.dp),
            )
        }
    }
}

/**
 * One month: tap the name for its letter, tap "4 weeks" to open out the weeks. The
 * month and week still under way say when their letter arrives instead.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MonthRow(month: YearMonth, state: RecapsUiState, onOpenRecap: (RecapPeriod) -> Unit) {
    val today = state.today
    val period = RecapPeriod.Month(month)
    val recap = state.recaps[period.key]
    val isCurrent = month == YearMonth.from(today)
    // Only weeks that have begun: a week that has not started has nothing to open.
    val weeks = remember(month, today) { RecapPeriod.weeksOf(month).filter { !it.start.isAfter(today) } }
    var expanded by rememberSaveable(month.toString()) { mutableStateOf(false) }
    val name = month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(
            Modifier
                .weight(1f)
                .clickable(role = Role.Button, onClickLabel = "Open the letter for $name") { onOpenRecap(period) }
                .defaultMinSize(minHeight = 64.dp)
                .padding(start = ScreenPadding, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(name, style = AppType.Body, color = Palette.Ink)
            MetaText(
                when {
                    recap != null -> recap.title
                    isCurrent -> "Letter arrives on ${RecapLabels.dayAndMonth(RecapRules.arrives(period))}"
                    else -> "No letter yet"
                },
                maxLines = 1,
            )
        }
        if (weeks.isNotEmpty()) {
            val shape = RoundedCornerShape(50)
            Row(
                Modifier
                    .padding(end = ScreenPadding)
                    .clip(shape)
                    .border(1.dp, Palette.Hairline, shape)
                    .clickable(role = Role.Button) { expanded = !expanded }
                    .semantics { stateDescription = if (expanded) "Weeks shown" else "Weeks hidden" }
                    .defaultMinSize(minHeight = 40.dp)
                    .padding(start = 14.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${weeks.size} week${if (weeks.size == 1) "" else "s"}", style = AppType.Meta, color = Palette.Ink)
                Icon(
                    if (expanded) LineIcons.ChevronUp else LineIcons.ChevronDown,
                    contentDescription = null,
                    tint = Palette.Muted,
                    modifier = Modifier.padding(start = 2.dp).size(20.dp),
                )
            }
        }
    }
    if (expanded) {
        FlowRow(
            Modifier.padding(horizontal = ScreenPadding).padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            weeks.forEach { week ->
                val underWay = !today.isAfter(week.end)
                Chip(
                    text = RecapLabels.short(week) + if (underWay) ", on Sunday" else "",
                    selected = false,
                    onClick = { onOpenRecap(week) },
                )
            }
        }
    }
}

@Composable
private fun NameDialog(current: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(current) }
    AppDialog(
        onDismiss = onDismiss,
        title = "What should your letters call you?",
        buttons = {
            QuietButton("Cancel", onDismiss, Modifier.weight(1f))
            RoseButton("Save", { onSave(text) }, Modifier.weight(1f))
        },
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(JournalPrefs.MAX_NAME_LENGTH) },
            label = { Text("Your name") },
            supportingText = { Text("Each letter opens with \"Dear ${text.trim().ifEmpty { "…" }},\". It is sent along when a letter is written.") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSave(text) }),
            colors = appTextFieldColors(),
            textStyle = AppType.Body,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
    }
}
