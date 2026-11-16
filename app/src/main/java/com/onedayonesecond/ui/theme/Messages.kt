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
import androidx.compose.foundation.shape.RoundedCornerShape
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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(14.dp))
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
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                message.text,
                style = AppType.Body,
                color = Palette.Paper,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 10.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
            IconAction(LineIcons.Close, "Close this message", onDismiss, tint = Palette.Paper)
        }
        if (message.actions.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(bottom = 4.dp),
            ) {
                message.actions.forEach { action ->
                    Text(
                        action.label,
                        style = AppType.BodyStrong,
                        color = Palette.Blush,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = androidx.compose.material3.ripple(color = Palette.Blush),
                                role = Role.Button,
                                onClick = { onAction(action) },
                            )
                            .defaultMinSize(minHeight = 48.dp)
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}

private const val MESSAGE_SHOWN_MS = 10_000L
private const val TICK_MS = 100L
