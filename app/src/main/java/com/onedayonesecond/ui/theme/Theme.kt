package com.onedayonesecond.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.onedayonesecond.R
import com.onedayonesecond.data.Mood

/**
 * The whole palette. White paper, near-black ink, and one pink: there is one rose
 * button per screen, and nothing else competes with it.
 */
object Palette {
    /** Every background. */
    val Paper = Color(0xFFFFFFFF)

    /** Text and icons. */
    val Ink = Color(0xFF2E2329)

    /** Dates, secondary text, inactive tabs. */
    val Muted = Color(0xFF6E6168)

    /** The one main action per screen, today's ring, mood faces. */
    val Rose = Color(0xFFAE4A6B)

    /** Clip and photo placeholders, selected chips. */
    val Blush = Color(0xFFFBCCDD)

    /** 1 dp dividers and outlines. */
    val Hairline = Color(0xFFF1E6EA)

    /** Only for mood visuals: the darker the colour, the harder the day. */
    fun mood(mood: Mood): Color = when (mood) {
        Mood.GREAT -> Color(0xFFFCE4EC)
        Mood.GOOD -> Color(0xFFF7BDD0)
        Mood.NEUTRAL -> Color(0xFFE58AAB)
        Mood.BAD -> Color(0xFFB8527A)
        Mood.ROUGH -> Color(0xFF6E2445)
    }
}

// Instrument Sans ships as one variable font file; each weight is the same file with
// a different setting on its weight axis.
@OptIn(ExperimentalTextApi::class)
private fun sans(weight: FontWeight) = Font(
    resId = R.font.instrument_sans,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val InstrumentSans = FontFamily(
    sans(FontWeight.Normal),
    sans(FontWeight.Medium),
    sans(FontWeight.SemiBold),
)

/** Only for the quote of the week, month or year. */
val InstrumentSerifItalic = FontFamily(
    Font(R.font.instrument_serif_italic, FontWeight.Normal, FontStyle.Italic),
)

/** The type scale: four sizes, plus the tab label and the day page's big date. */
object AppType {
    val Title = TextStyle(
        fontFamily = InstrumentSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.8).sp,
    )
    val Heading = TextStyle(
        fontFamily = InstrumentSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 24.sp,
    )
    val Body = TextStyle(
        fontFamily = InstrumentSans,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    )
    val BodyStrong = Body.copy(fontWeight = FontWeight.SemiBold)
    val Meta = TextStyle(
        fontFamily = InstrumentSans,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    )
    val Tab = TextStyle(
        fontFamily = InstrumentSans,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 16.sp,
    )

    /** The day number on the day page: the one exception to the four sizes. */
    val BigDate = TextStyle(
        fontFamily = InstrumentSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 76.sp,
        lineHeight = 76.sp,
        letterSpacing = (-3).sp,
    )

    /** The year on the Recaps tab. */
    val BigYear = BigDate.copy(fontSize = 84.sp, lineHeight = 84.sp)

    val Quote = TextStyle(
        fontFamily = InstrumentSerifItalic,
        fontStyle = FontStyle.Italic,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 34.sp,
    )
}

// Material's fifteen text styles are folded onto the four sizes, so its own
// components (dialogs, text fields, menus) cannot introduce a fifth.
private val AppTypography = Typography(
    displayLarge = AppType.Title,
    displayMedium = AppType.Title,
    displaySmall = AppType.Title,
    headlineLarge = AppType.Title,
    headlineMedium = AppType.Title,
    headlineSmall = AppType.Title,
    titleLarge = AppType.Heading,
    titleMedium = AppType.Heading,
    titleSmall = AppType.Heading,
    bodyLarge = AppType.Body,
    bodyMedium = AppType.Body,
    bodySmall = AppType.Meta,
    labelLarge = AppType.BodyStrong,
    labelMedium = AppType.Meta,
    labelSmall = AppType.Meta,
)

// Light only for now. Every surface role is paper so that nothing reads as a card.
private val AppColors = lightColorScheme(
    primary = Palette.Rose,
    onPrimary = Palette.Paper,
    primaryContainer = Palette.Blush,
    onPrimaryContainer = Palette.Ink,
    secondary = Palette.Rose,
    onSecondary = Palette.Paper,
    secondaryContainer = Palette.Blush,
    onSecondaryContainer = Palette.Ink,
    tertiary = Palette.Rose,
    onTertiary = Palette.Paper,
    tertiaryContainer = Palette.Blush,
    onTertiaryContainer = Palette.Ink,
    background = Palette.Paper,
    onBackground = Palette.Ink,
    surface = Palette.Paper,
    onSurface = Palette.Ink,
    surfaceVariant = Palette.Paper,
    onSurfaceVariant = Palette.Muted,
    surfaceTint = Palette.Paper,
    surfaceBright = Palette.Paper,
    surfaceDim = Palette.Paper,
    surfaceContainer = Palette.Paper,
    surfaceContainerHigh = Palette.Paper,
    surfaceContainerHighest = Palette.Paper,
    surfaceContainerLow = Palette.Paper,
    surfaceContainerLowest = Palette.Paper,
    inverseSurface = Palette.Ink,
    inverseOnSurface = Palette.Paper,
    inversePrimary = Palette.Blush,
    outline = Palette.Muted,
    outlineVariant = Palette.Hairline,
    // The darkest mood colour doubles as the colour of things going wrong.
    error = Color(0xFF6E2445),
    onError = Palette.Paper,
    errorContainer = Palette.Blush,
    onErrorContainer = Palette.Ink,
    scrim = Palette.Ink,
)

@Composable
fun OneDayTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColors,
        typography = AppTypography,
        content = content,
    )
}
