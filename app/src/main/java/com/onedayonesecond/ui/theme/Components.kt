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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Side margin of every screen. */
val ScreenPadding = 20.dp

val ButtonShape = RoundedCornerShape(14.dp)
val TileShape = RoundedCornerShape(8.dp)

/** The one main action of a screen. There should never be two of these visible at once. */
@Composable
fun RoseButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Palette.Rose,
            contentColor = Palette.Paper,
            disabledContainerColor = Palette.Hairline,
            disabledContentColor = Palette.Muted,
        ),
        elevation = null,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        modifier = modifier.heightIn(min = 52.dp),
    ) {
        Text(text, style = AppType.BodyStrong, textAlign = TextAlign.Center)
    }
}

/** Every other action: ink text inside a hairline outline, which is what marks it as tappable. */
@Composable
fun QuietButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = Palette.Ink,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = ButtonShape,
        border = BorderStroke(1.dp, Palette.Hairline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color, disabledContentColor = Palette.Muted),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        modifier = modifier.heightIn(min = 48.dp),
    ) {
        Text(text, style = AppType.BodyStrong, textAlign = TextAlign.Center)
    }
}

/** A 1 dp divider. Sections are separated by space and these, never by boxes. */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier, thickness = 1.dp, color = Palette.Hairline)
}

@Composable
fun TitleText(text: String, modifier: Modifier = Modifier) {
    Text(text, style = AppType.Title, color = Palette.Ink, modifier = modifier.semantics { heading() })
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
        Icon(icon, contentDescription = label, tint = if (enabled) tint else tint.copy(alpha = 0.35f))
    }
}

/** The bar at the top of a pushed screen: back on the left, the screen's own actions on the right. */
@Composable
fun BackBar(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backLabel: String = "Back",
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconAction(LineIcons.Back, backLabel, onBack)
        Spacer(Modifier.weight(1f))
        actions()
    }
}

/**
 * A plain tappable row: a title, an optional line under it, an optional icon. Rows are
 * stacked with a [Hairline] between them.
 */
@Composable
fun ActionRow(
    title: String,
    onClick: () -> Unit,
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
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .defaultMinSize(minHeight = 56.dp)
            .padding(horizontal = ScreenPadding, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = titleColor, modifier = Modifier.size(24.dp))
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

/** A selectable chip. Selected is a blush fill; unselected is a hairline outline. */
@Composable
fun Chip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    textStyle: androidx.compose.ui.text.TextStyle = AppType.Meta,
) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (selected) Palette.Blush else Palette.Paper)
            .border(1.dp, if (selected) Palette.Blush else Palette.Hairline, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { this.selected = selected }
            .defaultMinSize(minHeight = 40.dp)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = textStyle, color = if (enabled) Palette.Ink else Palette.Muted)
    }
}

/** A blush rectangle standing in for a clip or photo that is not there or not loaded yet. */
@Composable
fun MediaPlaceholder(modifier: Modifier = Modifier, content: @Composable () -> Unit = {}) {
    Box(modifier.clip(TileShape).background(Palette.Blush), contentAlignment = Alignment.Center) { content() }
}

/**
 * A bottom sheet in the app's style: paper, no shadow, a title, and a Close button at
 * the bottom where a thumb reaches it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSheet(
    onDismiss: () -> Unit,
    title: String? = null,
    meta: String? = null,
    showClose: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Palette.Paper,
        contentColor = Palette.Ink,
        tonalElevation = 0.dp,
        scrimColor = Palette.Ink.copy(alpha = 0.32f),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Palette.Hairline),
            )
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            if (title != null) {
                TitleText(title, Modifier.padding(horizontal = ScreenPadding).padding(top = 8.dp))
                if (meta != null) MetaText(meta, Modifier.padding(horizontal = ScreenPadding).padding(top = 2.dp))
                Spacer(Modifier.height(16.dp))
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

/** Colours for text fields, so they sit on paper with a hairline outline like everything else. */
@Composable
fun appTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Palette.Ink,
    unfocusedBorderColor = Palette.Hairline,
    focusedLabelColor = Palette.Ink,
    unfocusedLabelColor = Palette.Muted,
    cursorColor = Palette.Rose,
    focusedTextColor = Palette.Ink,
    unfocusedTextColor = Palette.Ink,
    focusedSupportingTextColor = Palette.Muted,
    unfocusedSupportingTextColor = Palette.Muted,
    errorBorderColor = Color(0xFF6E2445),
    errorSupportingTextColor = Color(0xFF6E2445),
)

/** A column of things with the same gap between them. */
@Composable
fun Stack(gap: Dp, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(gap), content = content)
}

/**
 * A dialog in the app's style: paper with a hairline edge instead of a shadow, a
 * title, and its buttons side by side at the bottom.
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
                .clip(RoundedCornerShape(20.dp))
                .background(Palette.Paper)
                .border(1.dp, Palette.Hairline, RoundedCornerShape(20.dp))
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            HeadingText(title)
            Spacer(Modifier.height(12.dp))
            content()
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), content = buttons)
        }
    }
}
