package com.onedayonesecond

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.media3.common.util.UnstableApi
import com.onedayonesecond.ui.nav.AppNavHost
import com.onedayonesecond.ui.theme.OneDayTheme

@UnstableApi
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            OneDayTheme {
                AppNavHost()
            }
        }
    }
}
