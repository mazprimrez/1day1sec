package com.onedayonesecond

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.media3.common.util.UnstableApi
import com.onedayonesecond.ui.nav.AppNavHost
import com.onedayonesecond.ui.theme.OneDayTheme

@UnstableApi
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // The app is light only for now, so the system bars always get dark icons,
        // whatever the phone's own dark-mode setting is.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            OneDayTheme {
                AppNavHost()
            }
        }
    }
}
