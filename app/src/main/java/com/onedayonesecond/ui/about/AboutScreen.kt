package com.onedayonesecond.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import com.onedayonesecond.ui.theme.AppType
import com.onedayonesecond.ui.theme.Palette
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.onedayonesecond.BuildConfig
import com.onedayonesecond.ui.theme.BackBar
import com.onedayonesecond.ui.theme.BodyText
import com.onedayonesecond.ui.theme.Hairline
import com.onedayonesecond.ui.theme.HeadingText
import com.onedayonesecond.ui.theme.MetaText
import com.onedayonesecond.ui.theme.ScreenPadding

/**
 * What the app is, and where the things it did not make itself come from. Open
 * Library asks to be credited where its data is used.
 */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        BackBar(onBack, backLabel = "Back to Reel", title = "About Kilasan")
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BodyText("A diary of one second a day, what you wrote on it, and what you thought while reading.")
            Text("VERSION ${BuildConfig.VERSION_NAME}", style = AppType.FrameNumber, color = Palette.Muted)

            Hairline(Modifier.padding(top = 8.dp))
            HeadingText("Books", Modifier.padding(top = 8.dp))
            BodyText("Book data from Open Library.")
            MetaText(
                "Searching for a book sends what you typed, or the ISBN you scanned, to Open Library, which " +
                    "is also where covers come from. Barcodes are read on your phone; no picture is sent anywhere.",
            )

            Hairline(Modifier.padding(top = 8.dp))
            HeadingText("Your words", Modifier.padding(top = 8.dp))
            MetaText(
                "Your diary and your thoughts on books are kept on this phone, and in your own Google Drive if " +
                    "you back up. Voice notes, letters and book summaries are written with an AI service that " +
                    "only answers your account.",
                Modifier.padding(bottom = 24.dp),
            )
        }
    }
}
