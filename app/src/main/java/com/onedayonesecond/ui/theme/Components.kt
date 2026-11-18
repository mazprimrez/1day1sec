package com.onedayonesecond.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Side margin of every screen. */
val ScreenPadding = 24.dp

/** Space left under a scrolling screen, so its last line clears the floating button. */
val FabClearance = 104.dp

/** What goes inside every kind of button: an optional 18 dp icon, then the label. */
@Composable
private fun ButtonContent(text: String, icon: ImageVector?) {
    if (icon != null) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
    }
    Text(text, style = AppType.Label, textAlign = TextAlign.Center)
}

private fun buttonPadding(icon: ImageVector?) =
    PaddingValues(start = if (icon != null) 16.dp else 24.dp, end = 24.dp, top = 10.dp, bottom = 10.dp)

/** The one main action of a screen. There should never be two of these visible at once. */
@Composable
fun RoseButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = Corners.Button,
        colors = ButtonDefaults.buttonColors(
            containerColor = Palette.Rose,
            contentColor = Palette.Paper,
            disabledContainerColor = Palette.Hairline,
            disabledContentColor = Palette.Muted,
        ),
        elevation = null,
        contentPadding = buttonPadding(icon),
        modifier = modifier.heightIn(min = 48.dp),
    ) {
        ButtonContent(text, icon)
    }
}

/** A second action that still wants weight, such as "Log a thought" beside each book. */
@Composable
fun TonalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = Corners.Button,
        colors = ButtonDefaults.buttonColors(
            containerColor = Palette.Blush,
            contentColor = Palette.Ink,
            disabledContainerColor = Palette.Hairline,
            disabledContentColor = Palette.Muted,
        ),
        elevation = null,
        contentPadding = buttonPadding(icon),
        modifier = modifier.heightIn(min = 48.dp),
    ) {
        ButtonContent(text, icon)
    }
}

/** Every other action: rose words inside a thin outline, which is what marks it as tappable. */
@Composable
fun QuietButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = Palette.Rose,
    icon: ImageVector? = null,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = Corners.Button,
        border = BorderStroke(1.dp, Palette.FrameLine),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color, disabledContentColor = Palette.Muted),
        contentPadding = buttonPadding(icon),
        modifier = modifier.heightIn(min = 48.dp),
    ) {
        ButtonContent(text, icon)
    }
}

/** The lightest action: rose words with no outline, for "Retake", "Cancel", "See all". */
@Composable
fun TextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = Palette.Rose,
    icon: ImageVector? = null,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        shape = Corners.Button,
        colors = ButtonDefaults.textButtonColors(contentColor = color, disabledContentColor = Palette.Muted),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        modifier = modifier.heightIn(min = 48.dp),
    ) {
        ButtonContent(text, icon)
    }
}

/**
 * The floating button that carries a screen's main action ("Add today", "Add a book").
 * With the message bar and book covers, it is one of the few things that casts a shadow.
 */
@Composable
fun FloatingAction(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .shadow(6.dp, Corners.Card, ambientColor = Palette.Ink, spotColor = Palette.Ink)
            .clip(Corners.Card)
            .background(Palette.Rose)
            .clickable(role = Role.Button, onClick = onClick)
            .height(56.dp)
            .padding(start = 16.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Palette.Paper, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, style = AppType.Label, color = Palette.Paper)
    }
}

/** A 1 dp divider. Sections are separated by space and these, never by boxes. */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier, thickness = 1.dp, color = Palette.Hairline)
}

/** The title of a tab, or the headline of a recap. */
@Composable
fun TitleText(text: String, modifier: Modifier = Modifier) {
    Text(text, style = AppType.Title, color = Palette.Ink, modifier = modifier.semantics { heading() })
}

/** The title inside a detail screen or a sheet. */
@Composable
fun HeadlineText(text: String, modifier: Modifier = Modifier) {
    Text(text, style = AppType.Headline, color = Palette.Ink, modifier = modifier.semantics { heading() })
}

@Composable
fun HeadingText(text: String, modifier: Modifier = Modifier) {
    Text(text, style = AppType.Heading, color = Palette.Ink, modifier = modifier.semantics { heading() })
}

@Composable
fun BodyText(text: String, modifier: Modifier = Modifier, color: Color = Palette.Ink) {
    Text(text, style = AppType.Body, color = color, modifier = modifier)
}

@Composable
fun MetaText(
    text: String,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    color: Color = Palette.Muted,
) {
    Text(
        text,
        style = AppType.Meta,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** The smallest line: a footnote, or the date above a thought. */
@Composable
fun SmallText(text: String, modifier: Modifier = Modifier, color: Color = Palette.Muted) {
    Text(text, style = AppType.Small, color = color, modifier = modifier)
}

/** An icon that can be tapped, in a 48 dp target. [label] is what a screen reader says. */
@Composable
fun IconAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Palette.Ink,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = modifier.size(48.dp)) {
        Icon(icon, contentDescription = label, tint = if (enabled) tint else tint.copy(alpha = 0.38f))
    }
}

/**
 * The bar at the top of a pushed screen: back on the left, then the screen's title,
 * then the screen's own actions. A screen that discards something on the way out
 * shows a cross instead of an arrow.
 */
@Composable
fun BackBar(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backLabel: String = "Back",
    title: String? = null,
    backIcon: ImageVector = LineIcons.Back,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconAction(backIcon, backLabel, onBack)
        if (title != null) {
            Text(
                title,
                style = AppType.TitleLarge,
                color = Palette.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 4.dp).semantics { heading() },
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        actions()
    }
}

/** The top of a tab: its name, an optional line of numbers under it, and its icon actions. */
@Composable
fun TabHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = ScreenPadding, top = 8.dp, end = 4.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            TitleText(title)
            if (subtitle != null) MetaText(subtitle)
        }
        actions()
    }
}

/**
 * A tappable row: a title, an optional line under it, and an optional icon in a
 * tinted circle. Rows with icons are stacked without dividers.
 */
@Composable
fun ActionRow(
    title: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    titleColor: Color = Palette.Ink,
    enabled: Boolean = true,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                // A row with nothing to open only states a fact, and is read out as one line.
                if (onClick != null) {
                    Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                } else {
                    Modifier.semantics(mergeDescendants = true) { }
                },
            )
            .defaultMinSize(minHeight = if (subtitle != null) 72.dp else 56.dp)
            .padding(start = if (icon != null) 16.dp else ScreenPadding, end = ScreenPadding, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(Palette.BlushTint),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = titleColor, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = AppType.Body, color = if (enabled) titleColor else Palette.Muted)
            if (subtitle != null) MetaText(subtitle)
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

/** A selectable chip. Selected is a blush fill; unselected is a thin outline. */
@Composable
fun Chip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    textStyle: TextStyle = AppType.Label,
) {
    Row(
        modifier = modifier
            .clip(Corners.Chip)
            .background(if (selected) Palette.Blush else Palette.Paper)
            .border(1.dp, if (selected) Palette.Blush else Palette.FrameLine, Corners.Chip)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { this.selected = selected }
            .defaultMinSize(minHeight = 40.dp)
            .padding(start = if (selected) 8.dp else 16.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Selection is the tick as well as the fill, so it does not rest on colour alone.
        if (selected) {
            Icon(LineIcons.Check, contentDescription = null, tint = Palette.Ink, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = textStyle, color = if (enabled) Palette.Ink else Palette.Muted)
    }
}

/** A chip that does something ("Photo of the page", "Voice note"): a rose icon and a label. */
@Composable
fun AssistChip(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .clip(Corners.Chip)
            .border(1.dp, Palette.FrameLine, Corners.Chip)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .defaultMinSize(minHeight = 40.dp)
            .padding(start = 12.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (enabled) Palette.Rose else Palette.Muted,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(text, style = AppType.Label, color = if (enabled) Palette.Ink else Palette.Muted)
    }
}

/**
 * Two or three choices of which exactly one is on. The chosen one is filled with
 * blush and carries a tick, so it is not told apart by colour alone.
 */
@Composable
fun Segmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(shape)
            .border(1.dp, Palette.Muted, shape)
            .selectableGroup(),
    ) {
        options.forEachIndexed { index, option ->
            val on = index == selected
            if (index > 0) Box(Modifier.fillMaxHeight().width(1.dp).background(Palette.Muted))
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(if (on) Palette.Blush else Palette.Paper)
                    .selectable(selected = on, role = Role.RadioButton, onClick = { onSelect(index) }),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (on) {
                    Icon(LineIcons.Check, contentDescription = null, tint = Palette.Ink, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(option, style = AppType.Label, color = Palette.Ink, maxLines = 1)
            }
        }
    }
}

/** On or off, as a picture: a rose track when on, with a tick in the thumb. */
@Composable
fun AppSwitch(checked: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(width = 52.dp, height = 32.dp)
            .clip(CircleShape)
            .background(if (checked) Palette.Rose else Palette.Paper)
            .then(if (checked) Modifier else Modifier.border(2.dp, Palette.Muted, CircleShape))
            .padding(4.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        if (checked) {
            Box(Modifier.size(24.dp).clip(CircleShape).background(Palette.Paper), contentAlignment = Alignment.Center) {
                Icon(LineIcons.Check, contentDescription = null, tint = Palette.Rose, modifier = Modifier.size(16.dp))
            }
        } else {
            Box(Modifier.padding(start = 4.dp).size(16.dp).clip(CircleShape).background(Palette.Muted))
        }
    }
}

/** A row that turns something on or off. The whole row is the switch. */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .defaultMinSize(minHeight = if (icon != null && subtitle != null) 72.dp else 56.dp)
            .padding(start = if (icon != null) 16.dp else ScreenPadding, end = ScreenPadding, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // With an icon it lines up with the action rows around it.
        if (icon != null) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(Palette.BlushTint),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = Palette.Ink, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = AppType.Body, color = if (enabled) Palette.Ink else Palette.Muted)
            if (subtitle != null) MetaText(subtitle)
        }
        Spacer(Modifier.width(16.dp))
        AppSwitch(checked)
    }
}

/** How far along something is: a book, a recording, a voice note being typed out. */
@Composable
fun ProgressLine(fraction: Float, modifier: Modifier = Modifier, track: Color = Palette.Blush) {
    val shown = fraction.coerceIn(0f, 1f)
    Box(
        modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(track)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(shown, 0f..1f) },
    ) {
        Box(Modifier.fillMaxWidth(shown).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(Palette.Rose))
    }
}

/**
 * Something is being worked on and there is no telling how far along it is: a short
 * rose line sliding along a faint track. It stands still when animations are off.
 */
@Composable
fun BusyLine(modifier: Modifier = Modifier) {
    val still = reducedMotion()
    val slide = rememberInfiniteTransition(label = "busy")
    val at by slide.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label = "busy position",
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Palette.Hairline)
            .drawBehind {
                val length = size.width * 0.4f
                val start = if (still) 0f else (size.width + length) * at - length
                drawRect(Palette.Rose, topLeft = Offset(start, 0f), size = Size(length, size.height))
            },
    )
}

/** A number that stands on its own, with what it counts above it. */
@Composable
fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.semantics(mergeDescendants = true) { }) {
        Text(label, style = AppType.Tab, color = Palette.Muted)
        Text(value, style = AppType.NumeralLarge, color = Palette.Ink)
    }
}

/** A tinted block that links somewhere or holds a quote. Flat: no outline, no shadow. */
@Composable
fun TintCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(Corners.Card)
            .background(Palette.BlushTint)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

/**
 * Something the reader should know in place, such as being offline: an icon, a title,
 * a sentence, and at most one thing to do about it. [warning] is for the rare notice
 * that something will be replaced or lost.
 */
@Composable
fun InlineAlert(
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    icon: ImageVector = LineIcons.Offline,
    warning: Boolean = false,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val hasAction = actionLabel != null && onAction != null
    Row(
        modifier
            .fillMaxWidth()
            .clip(Corners.Alert)
            .background(if (warning) Palette.ErrorTint else Palette.BlushTint)
            .padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = if (hasAction) 4.dp else 14.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (warning) Palette.Error else Palette.Ink,
            modifier = Modifier.padding(top = 2.dp).size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = if (body != null) AppType.TitleSmall else AppType.Meta, color = Palette.Ink)
            if (body != null) MetaText(body, Modifier.padding(top = 2.dp), color = Palette.Ink)
            if (actionLabel != null && onAction != null) {
                // Pulled left by the button's own padding, so its words line up with the text above.
                TextAction(actionLabel, onAction, Modifier.offset(x = (-12).dp))
            }
        }
    }
}

/** A blush rectangle standing in for a clip or photo that is not there or not loaded yet. */
@Composable
fun MediaPlaceholder(modifier: Modifier = Modifier, content: @Composable () -> Unit = {}) {
    Box(modifier.clip(Corners.Frame).background(Palette.Blush), contentAlignment = Alignment.Center) { content() }
}

/**
 * A bottom sheet in the app's style: paper, no shadow, a small handle and a title.
 * It is closed by dragging it down, tapping outside it, or going back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSheet(
    onDismiss: () -> Unit,
    title: String? = null,
    meta: String? = null,
    showClose: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Palette.Paper,
        contentColor = Palette.Ink,
        tonalElevation = 0.dp,
        scrimColor = Palette.Scrim,
        shape = Corners.Sheet,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 22.dp, bottom = 14.dp)
                    .size(width = 32.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Palette.FrameLine),
            )
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            if (title != null) {
                HeadlineText(title, Modifier.padding(horizontal = ScreenPadding))
                if (meta != null) MetaText(meta, Modifier.padding(horizontal = ScreenPadding).padding(top = 4.dp))
                Spacer(Modifier.height(20.dp))
            }
            content()
            if (showClose) {
                Spacer(Modifier.height(12.dp))
                QuietButton(
                    "Close",
                    onDismiss,
                    Modifier.fillMaxWidth().padding(horizontal = ScreenPadding),
                )
            }
        }
    }
}

/** Colours for text fields: a thin plum outline on paper, rose while being typed in. */
@Composable
fun appTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Palette.Rose,
    unfocusedBorderColor = Palette.Muted,
    focusedLabelColor = Palette.Rose,
    unfocusedLabelColor = Palette.Muted,
    focusedPlaceholderColor = Palette.Muted,
    unfocusedPlaceholderColor = Palette.Muted,
    cursorColor = Palette.Rose,
    focusedTextColor = Palette.Ink,
    unfocusedTextColor = Palette.Ink,
    focusedSupportingTextColor = Palette.Muted,
    unfocusedSupportingTextColor = Palette.Muted,
    errorBorderColor = Palette.Error,
    errorLabelColor = Palette.Error,
    errorSupportingTextColor = Palette.Error,
)

/**
 * A dialog in the app's style: flat paper over the scrim, a title, and its buttons
 * side by side at the bottom.
 */
@Composable
fun AppDialog(
    onDismiss: () -> Unit,
    title: String,
    buttons: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(Corners.Search)
                .background(Palette.Paper)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            HeadlineText(title)
            Spacer(Modifier.height(16.dp))
            content()
            Spacer(Modifier.height(24.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                content = buttons,
            )
        }
    }
}

/**
 * Where content would be when there is none yet: an outlined card with a title, what
 * to know, and at most one thing to do about it.
 */
@Composable
fun StateCard(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(Corners.Card)
            .border(1.dp, Palette.Hairline, Corners.Card)
            .padding(20.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HeadingText(title)
        content()
    }
}

/**
 * The search bar: a tinted pill with the way back on its left, the words being
 * searched for, and room for one icon on its right.
 */
@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backLabel: String = "Back",
    onSearch: () -> Unit = {},
    focusRequester: FocusRequester? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(Corners.Search)
            .background(Palette.BlushTint)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconAction(LineIcons.Back, backLabel, onBack)
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = AppType.Body.copy(color = Palette.Ink),
            cursorBrush = SolidColor(Palette.Rose),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            modifier = Modifier
                .weight(1f)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .semantics { contentDescription = placeholder },
            decorationBox = { field ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) Text(placeholder, style = AppType.Body, color = Palette.Muted, maxLines = 1)
                    field()
                }
            },
        )
        if (query.isNotEmpty()) IconAction(LineIcons.Close, "Clear the search", { onQueryChange("") })
        trailing()
    }
}
