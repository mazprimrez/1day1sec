package com.onedayonesecond.ui.day

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.onedayonesecond.OneDayApp
import com.onedayonesecond.data.Entry
import com.onedayonesecond.data.EntryType
import com.onedayonesecond.journal.RecapLabels
import com.onedayonesecond.ui.nav.Routes
import com.onedayonesecond.ui.theme.AppDialog
import com.onedayonesecond.ui.theme.AppType
import com.onedayonesecond.ui.theme.BackBar
import com.onedayonesecond.ui.theme.BodyText
import com.onedayonesecond.ui.theme.MetaText
import com.onedayonesecond.ui.theme.Palette
import com.onedayonesecond.ui.theme.QuietButton
import com.onedayonesecond.ui.theme.RoseButton
import com.onedayonesecond.ui.theme.ScreenPadding
import com.onedayonesecond.ui.theme.TitleText
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * A blank page for a day. Also where an existing entry's words are edited, including
 * the typed-out words of a voice note or page that came out wrong.
 */
@UnstableApi
@Composable
fun WriteScreen(
    date: LocalDate,
    entryId: Long,
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as OneDayApp).container }

    var text by rememberSaveable { mutableStateOf("") }
    var original by rememberSaveable { mutableStateOf("") }
    var loaded by rememberSaveable { mutableStateOf(entryId == Routes.NO_ENTRY) }
    var editing by remember { mutableStateOf<Entry?>(null) }
    var saving by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }

    LaunchedEffect(entryId) {
        if (entryId == Routes.NO_ENTRY) return@LaunchedEffect
        val entry = container.journal.entry(entryId)
        editing = entry
        if (!loaded) {
            text = entry?.text.orEmpty()
            original = text
            loaded = true
        }
    }

    val focus = remember { FocusRequester() }
    LaunchedEffect(loaded) { if (loaded) focus.requestFocus() }

    val changed = text.trim() != original.trim()
    fun leave() {
        if (changed && text.isNotBlank()) confirmLeave = true else onBack()
    }
    BackHandler(enabled = changed && text.isNotBlank()) { confirmLeave = true }

    fun save() {
        if (saving) return
        saving = true
        // On the app's scope, so the words are kept even if the screen is left mid-save.
        container.appScope.launch {
            val existing = editing
            if (existing != null) {
                container.journal.setText(existing, text)
            } else {
                container.journalActions.write(date, text)
            }
            onDone()
        }
    }

    val isToday = date == LocalDate.now()
    Column(Modifier.fillMaxSize().imePadding()) {
        BackBar(::leave, backLabel = "Back without saving")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = ScreenPadding)) {
            TitleText(
                when {
                    editing != null && editing?.type != EntryType.WRITTEN -> "Correct the words"
                    editing != null -> "Edit"
                    isToday -> "Write about today"
                    else -> "Write about this day"
                },
            )
            MetaText(RecapLabels.fullDate(date), Modifier.padding(top = 2.dp, bottom = 16.dp))
            Box(Modifier.fillMaxWidth()) {
                if (text.isEmpty()) {
                    BodyText(if (isToday) "What happened today?" else "What happened that day?", color = Palette.Muted)
                }
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = AppType.Body.copy(color = Palette.Ink),
                    cursorBrush = SolidColor(Palette.Rose),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus)
                        .semantics { contentDescription = "Your writing" },
                )
            }
        }
        RoseButton(
            if (saving) "Saving…" else "Save",
            ::save,
            Modifier.fillMaxWidth().padding(horizontal = ScreenPadding, vertical = 12.dp),
            // An existing entry may be saved empty-handed only if it has media to stand on.
            enabled = loaded && !saving && (text.isNotBlank() || (editing != null && editing?.type != EntryType.WRITTEN)),
        )
    }

    if (confirmLeave) {
        AppDialog(
            onDismiss = { confirmLeave = false },
            title = "Leave without saving?",
            buttons = {
                QuietButton("Leave", { confirmLeave = false; onBack() }, Modifier.weight(1f))
                RoseButton("Keep writing", { confirmLeave = false }, Modifier.weight(1f))
            },
        ) {
            Text("What you wrote here hasn't been saved yet.", style = AppType.Body, color = Palette.Ink)
        }
    }
}
