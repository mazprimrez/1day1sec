package com.onedayonesecond.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.shadow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Something the user can do about a message. [run] does work (an undo); [route] goes
 * somewhere afterwards ("Write a few words"). Either may be left out.
 */
class MessageAction(
    val label: String,
    val route: String? = null,
    val run: (suspend () -> Unit)? = null,
)

data class AppMessage(val id: Long, val text: String, val actions: List<MessageAction>)

/**
 * The one place short messages are sent to ("Today's second is saved."). It belongs
 * to the app, not to a screen, so a message raised while leaving a screen is still
 * shown on the next one, and its Undo still works.
 */
class AppMessages {

    private val _current = MutableStateFlow<AppMessage?>(null)
    val current: StateFlow<AppMessage?> = _current.asStateFlow()

    private var nextId = 0L

    fun show(text: String, vararg actions: MessageAction) {
        _current.value = AppMessage(++nextId, text, actions.toList())
    }

    /** Only dismisses the message it was asked about, so a newer one is not cut short. */
    fun dismiss(id: Long) {
        _current.update { if (it?.id == id) null else it }
    }
}

/**
 * Shows a message for at least ten seconds. The clock stops while it is being touched
 * or has keyboard focus, so nobody loses an Undo they were reaching for; it can also
 * be closed by hand. It is announced politely by screen readers and never takes focus.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MessageBar(
    message: AppMessage,
    onAction: (MessageAction) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var touched by remember(message.id) { mutableStateOf(false) }
    var focused by remember(message.id) { mutableStateOf(false) }

    LaunchedEffect(message.id) {
        var remaining = MESSAGE_SHOWN_MS
        while (remaining > 0) {
            delay(TICK_MS)
            if (!touched && !focused) remaining -= TICK_MS
        }
        onDismiss()
    }

    // With one thing to do, it sits at the end of the line. With two, they go on a
    // line of their own and the message can be closed by hand.
    val single = message.actions.singleOrNull()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .shadow(6.dp, Corners.Field, ambientColor = Palette.Ink, spotColor = Palette.Ink)
            .clip(Corners.Field)
            .background(Palette.Ink)
            .onFocusChanged { focused = it.hasFocus }
            .pointerInput(message.id) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        touched = event.changes.any { it.pressed }
                    }
                }
            }
            .padding(start = 16.dp, end = 8.dp),
    ) {
        Row(Modifier.defaultMinSize(minHeight = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                message.text,
                style = AppType.Meta,
                color = Palette.Paper,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 14.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
            when {
                single != null -> MessageButton(single.label) { onAction(single) }
                message.actions.isNotEmpty() ->
                    IconAction(LineIcons.Close, "Close this message", onDismiss, tint = Palette.Paper)
            }
        }
        if (message.actions.size > 1) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(bottom = 4.dp).offset(x = (-12).dp),
            ) {
                message.actions.forEach { action -> MessageButton(action.label) { onAction(action) } }
            }
        }
    }
}

/** A thing to do about a message, in blush on the dark bar. */
@Composable
private fun MessageButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(Corners.Button)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = androidx.compose.material3.ripple(color = Palette.Blush),
                role = Role.Button,
                onClick = onClick,
            )
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = AppType.Label, color = Palette.Blush)
    }
}

private const val MESSAGE_SHOWN_MS = 10_000L
private const val TICK_MS = 100L
