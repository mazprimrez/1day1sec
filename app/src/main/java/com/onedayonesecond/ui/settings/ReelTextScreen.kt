package com.onedayonesecond.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.onedayonesecond.media.DateAlign
import com.onedayonesecond.media.DateEdge
import com.onedayonesecond.media.DateFont
import com.onedayonesecond.media.typeface
import com.onedayonesecond.ui.common.ClipStill
import com.onedayonesecond.ui.compile.CompilePhase
import com.onedayonesecond.ui.compile.CompileViewModel
import com.onedayonesecond.ui.compile.FramePreview
import com.onedayonesecond.ui.compile.PREVIEW_WIDTH
import com.onedayonesecond.ui.compile.RangeOption
import com.onedayonesecond.ui.theme.AppType
import com.onedayonesecond.ui.theme.BackBar
import com.onedayonesecond.ui.theme.Chip
import com.onedayonesecond.ui.theme.Hairline
import com.onedayonesecond.ui.theme.HeadingText
import com.onedayonesecond.ui.theme.InlineAlert
import com.onedayonesecond.ui.theme.LineIcons
import com.onedayonesecond.ui.theme.MetaText
import com.onedayonesecond.ui.theme.Palette
import com.onedayonesecond.ui.theme.ScreenPadding
import com.onedayonesecond.ui.theme.Segmented
import com.onedayonesecond.ui.theme.SmallText
import java.time.LocalDate

/**
 * What is printed on every second of a reel: which of the date, the caption and the
 * place, in which font, and where in the frame. A frame beside the choices shows them
 * on the newest second, so nothing has to be stitched to see the result. Below that is
 * the range a reel starts with.
 *
 * Each choice is saved as it is made. The view model is the activity's, shared with the
 * Reel tab, so the tab shows the same settings on return.
 */
@UnstableApi
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReelTextScreen(onBack: () -> Unit, compile: CompileViewModel) {
    val reel by compile.state.collectAsStateWithLifecycle()
    val style = reel.dateStyle

    // Seconds may have been recorded or deleted since the counts were last taken.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { compile.refreshCount() }

    // A reel being stitched was started with the settings as they were, so they hold still until it is done.
    val making = reel.phase == CompilePhase.COMPILING || reel.phase == CompilePhase.EXPORTING
    val open = !making

    Column(Modifier.fillMaxSize()) {
        BackBar(onBack, title = "Text on each second")
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = ScreenPadding, top = 4.dp, end = ScreenPadding, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                val clip = reel.previewClip
                val date = clip?.date ?: LocalDate.now()
                Column(Modifier.width(PREVIEW_WIDTH)) {
                    FramePreview(
                        style = style,
                        showDates = reel.showDates,
                        showCaptions = reel.showCaptions,
                        showPlaces = reel.showPlaces,
                        date = date,
                        caption = clip?.caption?.ifEmpty { null } ?: "Your caption",
                        still = clip?.let { { ClipStill(it, Modifier.fillMaxSize(), shape = RectangleShape) } },
                    )
                    // Numbered like a frame of the contact sheet: the day of the year, and how long it runs.
                    Row(
                        Modifier.fillMaxWidth().padding(top = 4.dp).clearAndSetSemantics { },
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(date.dayOfYear.toString(), style = AppType.FrameNumber, color = Palette.Ink)
                        Text("00:01", style = AppType.FrameNumber, color = Palette.Muted)
                    }
                }
                MetaText(
                    "This is how text lands on every second when you make your reel. The size follows " +
                        "the video, so it reads the same on any phone.",
                    Modifier.weight(1f),
                )
            }

            if (making) {
                InlineAlert(
                    "Your reel is being made",
                    Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    body = "These can be changed again when it is done.",
                    icon = LineIcons.Video,
                )
            }

            Group("Show") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Chip("Date", reel.showDates, { compile.setShowDates(!reel.showDates) }, enabled = open)
                    Chip("Caption", reel.showCaptions, { compile.setShowCaptions(!reel.showCaptions) }, enabled = open)
                    Chip("Place", reel.showPlaces, { compile.setShowPlaces(!reel.showPlaces) }, enabled = open)
                }
                if (reel.clipCount > 0) {
                    SmallText(
                        "${reel.captionCount} of ${reel.clipCount} second${if (reel.clipCount == 1) "" else "s"} " +
                            "have a caption; ${reel.placeCount} have a place.",
                        Modifier.padding(top = 8.dp),
                    )
                }
            }

            Group("Font") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DateFont.entries.forEach { font ->
                        Chip(
                            text = font.label,
                            selected = style.font == font,
                            onClick = { compile.setDateStyle(style.copy(font = font)) },
                            enabled = open,
                            // Each name in its own font, so the list is its own preview.
                            textStyle = AppType.Label.copy(
                                fontFamily = remember(font) { FontFamily(font.typeface()) },
                            ),
                        )
                    }
                }
                SmallText(
                    "Fonts that come with your phone; they can look a little different on other brands.",
                    Modifier.padding(top = 8.dp),
                )
            }

            Group("Position") {
                Segmented(
                    options = DateAlign.entries.map { it.label },
                    selected = style.align.ordinal,
                    onSelect = { if (open) compile.setDateStyle(style.copy(align = DateAlign.entries[it])) },
                )
                Spacer(Modifier.height(8.dp))
                Segmented(
                    options = DateEdge.entries.map { it.label },
                    selected = style.edge.ordinal,
                    onSelect = { if (open) compile.setDateStyle(style.copy(edge = DateEdge.entries[it])) },
                )
            }

            Column {
                Hairline()
                HeadingText("Reel length to start with", Modifier.padding(top = 24.dp))
                MetaText("What “Make my reel” uses unless you change it on the Reel tab.", Modifier.padding(top = 4.dp))
                Segmented(
                    options = RangeOption.entries.map { it.label },
                    selected = reel.startRange.ordinal,
                    onSelect = { if (open) compile.setStartRange(RangeOption.entries[it]) },
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}

/** A heading with its choices under it. */
@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column {
        HeadingText(title, Modifier.padding(bottom = 12.dp))
        content()
    }
}
