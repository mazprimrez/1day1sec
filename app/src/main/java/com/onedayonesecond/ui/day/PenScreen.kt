package com.onedayonesecond.ui.day

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.onedayonesecond.OneDayApp
import com.onedayonesecond.journal.RecapLabels
import com.onedayonesecond.ui.theme.BackBar
import com.onedayonesecond.ui.theme.MetaText
import com.onedayonesecond.ui.theme.Palette
import com.onedayonesecond.ui.theme.QuietButton
import com.onedayonesecond.ui.theme.RoseButton
import com.onedayonesecond.ui.theme.ScreenPadding
import com.onedayonesecond.ui.theme.TileShape
import com.onedayonesecond.ui.theme.TitleText
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * A page to write or draw on with the S Pen (a finger works too). The page is saved
 * as a picture, and its handwriting is typed out like a notebook photo's.
 */
@OptIn(ExperimentalComposeUiApi::class) // for the pen's between-frame points
@UnstableApi
@Composable
fun PenScreen(date: LocalDate, onDone: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as OneDayApp).container }
    val strokeWidth = with(LocalDensity.current) { 2.5.dp.toPx() }

    // Finished strokes, and the one being drawn. A stroke is the points the pen passed through.
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var pageSize by remember { mutableStateOf(IntSize.Zero) }
    var saving by remember { mutableStateOf(false) }

    fun save() {
        if (saving || strokes.isEmpty() || pageSize == IntSize.Zero) return
        saving = true
        val page = renderPage(strokes.toList(), pageSize, strokeWidth)
        // On the app's scope, so the page is kept even if this screen is left mid-save.
        container.appScope.launch {
            container.journalActions.addPen(date, page)
            page.recycle()
            onDone()
        }
    }

    Column(Modifier.fillMaxSize()) {
        BackBar(onBack, backLabel = "Back without saving")
        Column(Modifier.weight(1f).padding(horizontal = ScreenPadding)) {
            TitleText("Draw with S Pen")
            MetaText(RecapLabels.fullDate(date), Modifier.padding(top = 2.dp, bottom = 12.dp))

            Canvas(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(TileShape)
                    .border(1.dp, Palette.Hairline, TileShape)
                    .onSizeChanged { pageSize = it }
                    .semantics { contentDescription = "The page. Write or draw on it with the pen or a finger." }
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val down = awaitPointerEvent().changes.firstOrNull { it.pressed } ?: continue
                                var points = listOf(down.position)
                                current = points
                                down.consume()
                                while (true) {
                                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) break
                                    // Every point the pen reported between frames, not just the
                                    // latest, so fast handwriting keeps its curves.
                                    points = points + change.historical.map { it.position } + change.position
                                    current = points
                                    change.consume()
                                }
                                strokes += points
                                current = emptyList()
                            }
                        }
                    },
            ) {
                val style = Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                (strokes + listOf(current)).forEach { points ->
                    if (points.size == 1) {
                        drawCircle(Palette.Ink, radius = strokeWidth / 2f, center = points.first())
                    } else if (points.size > 1) {
                        val path = Path().apply {
                            moveTo(points.first().x, points.first().y)
                            points.drop(1).forEach { lineTo(it.x, it.y) }
                        }
                        drawPath(path, Palette.Ink, style = style)
                    }
                }
            }
        }

        Column(
            Modifier.padding(horizontal = ScreenPadding, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuietButton(
                    "Undo stroke",
                    { if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex) },
                    Modifier.weight(1f),
                    enabled = strokes.isNotEmpty() && !saving,
                )
                QuietButton("Clear page", { strokes.clear() }, Modifier.weight(1f), enabled = strokes.isNotEmpty() && !saving)
            }
            RoseButton(
                if (saving) "Saving…" else "Save page",
                ::save,
                Modifier.fillMaxWidth(),
                enabled = strokes.isNotEmpty() && !saving,
            )
        }
    }
}

/** The page as a picture: ink on white, exactly as drawn on screen. */
private fun renderPage(strokes: List<List<Offset>>, size: IntSize, strokeWidth: Float): Bitmap {
    val bitmap = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.WHITE)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Palette.Ink.toArgb()
        style = Paint.Style.STROKE
        this.strokeWidth = strokeWidth
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.Ink.toArgb() }
    for (points in strokes) {
        if (points.size == 1) {
            canvas.drawCircle(points.first().x, points.first().y, strokeWidth / 2f, dot)
        } else if (points.size > 1) {
            val path = android.graphics.Path().apply {
                moveTo(points.first().x, points.first().y)
                points.drop(1).forEach { lineTo(it.x, it.y) }
            }
            canvas.drawPath(path, paint)
        }
    }
    return bitmap
}
