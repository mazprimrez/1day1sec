package com.onedayonesecond.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
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
 * other words) and saves it to the gallery: the title, the grid, a legend with the
 * number of days per mood, and a small Kilasan mark.
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

    private val sans: Typeface by lazy {
        ResourcesCompat.getFont(context, R.font.instrument_sans) ?: Typeface.DEFAULT
    }

    private fun text(size: Float, weight: Int, color: Int, align: Paint.Align = Paint.Align.LEFT) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = sans
            // One variable font file; the weight is a setting on it.
            fontVariationSettings = "'wght' $weight"
            textSize = size
            this.color = color
            textAlign = align
        }

    private fun draw(year: Int, moods: Map<LocalDate, Mood>, today: LocalDate): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val title = text(76f, 600, INK).apply { letterSpacing = -0.03f }
        canvas.drawText("$year in pixels", MARGIN, 170f, title)

        // The grid: months across, days down.
        val gridTop = 290f
        val labelWidth = 64f
        val step = 42f
        val cell = 34f
        val gridLeft = (WIDTH - (labelWidth + Pixels.MONTHS * step)) / 2f + labelWidth
        val label = text(24f, 400, MUTED, Paint.Align.CENTER)
        val locale = Locale.getDefault()
        for (month in 1..Pixels.MONTHS) {
            canvas.drawText(
                Month.of(month).getDisplayName(TextStyle.NARROW, locale),
                gridLeft + (month - 1) * step + cell / 2,
                gridTop - 18f,
                label,
            )
        }
        val dayLabel = text(24f, 400, MUTED, Paint.Align.RIGHT)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = HAIRLINE
        }
        for (day in 1..Pixels.DAYS) {
            val top = gridTop + (day - 1) * step
            if (day == 1 || day % 5 == 0) canvas.drawText(day.toString(), gridLeft - 16f, top + cell - 9f, dayLabel)
            for (month in 1..Pixels.MONTHS) {
                val left = gridLeft + (month - 1) * step
                val rect = RectF(left, top, left + cell, top + cell)
                val pixel = Pixels.at(year, month, day, moods, today)
                when (pixel.kind) {
                    PixelKind.MOOD -> {
                        fill.color = moodColor(pixel.mood!!)
                        canvas.drawRoundRect(rect, 7f, 7f, fill)
                    }
                    PixelKind.EMPTY -> canvas.drawRoundRect(rect, 7f, 7f, outline)
                    PixelKind.FUTURE -> {
                        fill.color = HAIRLINE
                        canvas.drawCircle(rect.centerX(), rect.centerY(), 4f, fill)
                    }
                    PixelKind.ABSENT -> Unit
                }
            }
        }

        // The legend: best days first, each with its count.
        val counts = JournalMath.moodCounts(moods.filterKeys { it.year == year }.values)
        val legendTop = gridTop + Pixels.DAYS * step + 40f
        val name = text(28f, 400, INK)
        val count = text(28f, 400, MUTED)
        val columnWidth = (WIDTH - 2 * MARGIN) / 3f
        Mood.entries.reversed().forEachIndexed { index, mood ->
            val x = MARGIN + (index % 3) * columnWidth
            val y = legendTop + (index / 3) * 64f
            fill.color = moodColor(mood)
            canvas.drawRoundRect(RectF(x, y, x + 34f, y + 34f), 7f, 7f, fill)
            canvas.drawText(mood.label, x + 50f, y + 27f, name)
            val days = counts[mood] ?: 0
            canvas.drawText("$days", x + 50f + name.measureText(mood.label) + 12f, y + 27f, count)
        }

        canvas.drawText("Kilasan", WIDTH / 2f, HEIGHT - 70f, text(26f, 600, MUTED, Paint.Align.CENTER))
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
        const val MARGIN = 96f
        const val ALBUM = "Kilasan"

        // The app's palette (ui/theme/Theme.kt), as plain colour ints: this class draws
        // with android.graphics, not Compose.
        val INK = Color.parseColor("#2E2329")
        val MUTED = Color.parseColor("#6E6168")
        val HAIRLINE = Color.parseColor("#F1E6EA")

        fun moodColor(mood: Mood): Int = Color.parseColor(
            when (mood) {
                Mood.GREAT -> "#FCE4EC"
                Mood.GOOD -> "#F7BDD0"
                Mood.NEUTRAL -> "#E58AAB"
                Mood.BAD -> "#B8527A"
                Mood.ROUGH -> "#6E2445"
            },
        )
    }
}
