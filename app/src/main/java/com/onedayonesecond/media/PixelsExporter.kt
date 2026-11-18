package com.onedayonesecond.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import kotlin.math.roundToInt
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.res.ResourcesCompat
import com.onedayonesecond.R
import com.onedayonesecond.data.Mood
import com.onedayonesecond.journal.JournalMath
import com.onedayonesecond.journal.PixelKind
import com.onedayonesecond.journal.Pixels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

/**
 * Draws a year in pixels as a 1080 x 1920 picture (a phone wallpaper or story, in
 * other words) and saves it to the gallery: the year as a title, the grid, a legend
 * of what the colours mean, and a line at the foot with the share of good days and
 * the app's name.
 */
class PixelsExporter(private val context: Context) {

    /** @return the file name it was saved under, in Pictures/Kilasan. */
    suspend fun export(year: Int, moods: Map<LocalDate, Mood>, today: LocalDate): String =
        withContext(Dispatchers.IO) {
            val picture = draw(year, moods, today)
            val name = "kilasan-$year-in-pixels.png"
            try {
                save(picture, name)
            } finally {
                picture.recycle()
            }
            name
        }

    private val flex: Typeface by lazy {
        ResourcesCompat.getFont(context, R.font.roboto_flex) ?: Typeface.DEFAULT
    }

    private fun text(
        size: Float,
        weight: Int,
        color: Int,
        align: Paint.Align = Paint.Align.LEFT,
        slanted: Boolean = false,
    ) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = flex
        // One variable font file; the weight and the slant are settings on it.
        fontVariationSettings = "'wght' $weight, 'slnt' ${if (slanted) -10 else 0}"
        textSize = size
        this.color = color
        textAlign = align
    }

    private fun draw(year: Int, moods: Map<LocalDate, Mood>, today: LocalDate): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        // "2026" in ink, then "in pixels" slanted in rose on the same line.
        val title = text(149f, 600, INK)
        canvas.drawText(year.toString(), MARGIN, TITLE_BASELINE, title)
        canvas.drawText(
            "in pixels",
            MARGIN + title.measureText(year.toString()) + 27f,
            TITLE_BASELINE,
            text(91f, 500, ROSE, slanted = true),
        )

        // The grid: months across, days down, filling the width between the margins.
        val width = WIDTH - 2 * MARGIN
        val cellW = (width - GAP * (Pixels.MONTHS - 1)) / Pixels.MONTHS
        val label = text(24f, 500, MUTED, Paint.Align.CENTER)
        val locale = Locale.getDefault()
        for (month in 1..Pixels.MONTHS) {
            canvas.drawText(
                Month.of(month).getDisplayName(TextStyle.NARROW, locale),
                MARGIN + (month - 1) * (cellW + GAP) + cellW / 2,
                GRID_TOP - 16f,
                label,
            )
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        // A day without a mood is a dashed outline, as it is on the page in the app.
        val dashed = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
            color = EMPTY
            pathEffect = DashPathEffect(floatArrayOf(8f, 5f), 0f)
        }
        for (day in 1..Pixels.DAYS) {
            val top = GRID_TOP + (day - 1) * (CELL_HEIGHT + GAP)
            for (month in 1..Pixels.MONTHS) {
                val left = MARGIN + (month - 1) * (cellW + GAP)
                val rect = RectF(left, top, left + cellW, top + CELL_HEIGHT)
                val pixel = Pixels.at(year, month, day, moods, today)
                when (pixel.kind) {
                    PixelKind.MOOD -> {
                        fill.color = moodColor(pixel.mood!!)
                        canvas.drawRoundRect(rect, RADIUS, RADIUS, fill)
                    }
                    PixelKind.EMPTY -> {
                        rect.inset(1.25f, 1.25f)
                        canvas.drawRoundRect(rect, RADIUS, RADIUS, dashed)
                    }
                    // Days still to come are left as paper.
                    PixelKind.FUTURE, PixelKind.ABSENT -> Unit
                }
            }
        }

        // The legend: best days first, then what a dashed square is. It wraps like a line of words.
        val swatch = 27f
        val name = text(29f, 600, INK)
        var x = MARGIN
        var y = GRID_TOP + Pixels.DAYS * (CELL_HEIGHT + GAP) - GAP + 48f
        val items = Mood.entries.reversed().map { it.label to it } + ("No entry" to null)
        items.forEach { (word, mood) ->
            val itemWidth = swatch + 16f + name.measureText(word)
            if (x + itemWidth > WIDTH - MARGIN) {
                x = MARGIN
                y += swatch + 21f
            }
            val box = RectF(x, y, x + swatch, y + swatch)
            if (mood != null) {
                fill.color = moodColor(mood)
                canvas.drawRoundRect(box, RADIUS, RADIUS, fill)
            } else {
                box.inset(1.25f, 1.25f)
                canvas.drawRoundRect(box, RADIUS, RADIUS, dashed)
            }
            canvas.drawText(word, x + swatch + 16f, y + swatch - 4f, name)
            x += itemWidth + 43f
        }

        // The foot: how much of the year was good, and whose picture this is.
        val counts = JournalMath.moodCounts(moods.filterKeys { it.year == year }.values)
        val marked = counts.values.sum()
        val foot = HEIGHT - MARGIN
        if (marked > 0) {
            val bright = (counts[Mood.GOOD] ?: 0) + (counts[Mood.GREAT] ?: 0)
            canvas.drawText("${(bright * 100f / marked).roundToInt()}% good or great days", MARGIN, foot, text(29f, 500, INK))
        }
        canvas.drawText("Kilasan", WIDTH - MARGIN, foot, text(29f, 500, INK, Paint.Align.RIGHT))
        return bitmap
    }

    private fun save(picture: Bitmap, displayName: String): Uri {
        val resolver = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM")
                // Hides the row from gallery apps until the bytes are written.
                put(MediaStore.Images.Media.IS_PENDING, 1)
            } else {
                @Suppress("DEPRECATION")
                val albumDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    ALBUM,
                ).apply { mkdirs() }
                @Suppress("DEPRECATION")
                put(MediaStore.Images.Media.DATA, File(albumDir, displayName).absolutePath)
            }
        }
        val uri = resolver.insert(collection, values) ?: throw IOException("The gallery rejected the new picture")
        try {
            resolver.openOutputStream(uri)?.use { picture.compress(Bitmap.CompressFormat.PNG, 100, it) }
                ?: throw IOException("Could not open $uri for writing")
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        }
        return uri
    }

    private companion object {
        const val WIDTH = 1080
        const val HEIGHT = 1920
        const val ALBUM = "Kilasan"

        // The picture is the design's 405 x 720 board at eight thirds of its size.
        const val MARGIN = 80f
        const val TITLE_BASELINE = 236f
        const val GRID_TOP = 350f
        const val CELL_HEIGHT = 34.67f
        const val GAP = 8f
        const val RADIUS = 8f

        // The app's palette (ui/theme/Theme.kt), as plain colour ints: this class draws
        // with android.graphics, not Compose.
        val INK = Color.parseColor("#2E2329")
        val MUTED = Color.parseColor("#6E6168")
        val ROSE = Color.parseColor("#AE4A6B")
        val EMPTY = Color.parseColor("#D9A7BB")

        fun moodColor(mood: Mood): Int = Color.parseColor(
            when (mood) {
                Mood.GREAT -> "#F9D3E0"
                Mood.GOOD -> "#F7BDD0"
                Mood.NEUTRAL -> "#E58AAB"
                Mood.BAD -> "#B8527A"
                Mood.ROUGH -> "#6E2445"
            },
        )
    }
}
