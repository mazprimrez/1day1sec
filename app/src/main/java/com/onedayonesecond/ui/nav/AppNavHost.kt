package com.onedayonesecond.ui.nav

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.onedayonesecond.OneDayApp
import com.onedayonesecond.ui.camera.CameraScreen
import com.onedayonesecond.ui.day.DayScreen
import com.onedayonesecond.ui.day.PenScreen
import com.onedayonesecond.ui.day.VoiceScreen
import com.onedayonesecond.ui.day.WriteScreen
import com.onedayonesecond.ui.gallery.GalleryDayScreen
import com.onedayonesecond.ui.home.HomeScreen
import com.onedayonesecond.ui.home.RecentlyDeletedScreen
import com.onedayonesecond.ui.recaps.PixelsScreen
import com.onedayonesecond.ui.recaps.RecapScreen
import com.onedayonesecond.ui.recaps.RecapsScreen
import com.onedayonesecond.ui.reel.ReelScreen
import com.onedayonesecond.ui.theme.AppType
import com.onedayonesecond.ui.theme.Hairline
import com.onedayonesecond.ui.theme.LineIcons
import com.onedayonesecond.ui.theme.MessageBar
import com.onedayonesecond.ui.theme.Palette
import com.onedayonesecond.ui.trim.TrimScreen
import kotlinx.coroutines.launch
import java.time.LocalDate

@UnstableApi
@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val container = (LocalContext.current.applicationContext as OneDayApp).container
    val message by container.messages.current.collectAsStateWithLifecycle()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    Scaffold(
        containerColor = Palette.Paper,
        contentColor = Palette.Ink,
        bottomBar = { if (route in Routes.TABS) TabBar(route, navController) },
        snackbarHost = {
            message?.let { shown ->
                MessageBar(
                    message = shown,
                    onAction = { action ->
                        container.messages.dismiss(shown.id)
                        // On the app's scope, so an Undo still finishes if the screen changes under it.
                        container.appScope.launch {
                            action.run?.invoke()
                            action.route?.let { navController.navigate(it) }
                        }
                    },
                    onDismiss = { container.messages.dismiss(shown.id) },
                )
            }
        },
    ) { padding ->
        // The camera draws under the system bars; every other screen sits between them.
        val inner = if (route == Routes.CAMERA_PATTERN) PaddingValues(0.dp) else padding
        NavHost(
            navController = navController,
            startDestination = Routes.DAYS,
            // Consumed as well as applied, so a screen that pads for the keyboard does
            // not add the system bar's height a second time.
            modifier = Modifier.padding(inner).consumeWindowInsets(inner),
        ) {
            destinations(navController)
        }
    }
}

@UnstableApi
private fun androidx.navigation.NavGraphBuilder.destinations(nav: NavHostController) {
    val dateArg = listOf(navArgument(Routes.ARG_DATE) { type = NavType.LongType })
    fun androidx.navigation.NavBackStackEntry.date(): LocalDate =
        LocalDate.ofEpochDay(arguments!!.getLong(Routes.ARG_DATE))

    /** After something is added to a day: show that day, with the calendar one step behind it. */
    fun showDay(date: LocalDate) {
        nav.popBackStack(Routes.DAYS, inclusive = false)
        nav.navigate(Routes.day(date))
    }

    composable(Routes.DAYS) {
        HomeScreen(
            onOpenDay = { nav.navigate(Routes.day(it)) },
            onRecord = { nav.navigate(Routes.camera(it)) },
            onWrite = { nav.navigate(Routes.write(it)) },
            onVoice = { nav.navigate(Routes.voice(it)) },
            onDraw = { nav.navigate(Routes.pen(it)) },
            onRecentlyDeleted = { nav.navigate(Routes.DELETED) },
        )
    }

    composable(Routes.RECAPS) {
        RecapsScreen(
            onOpenRecap = { nav.navigate(Routes.recap(it)) },
            onOpenPixels = { nav.navigate(Routes.pixels(it)) },
        )
    }

    composable(Routes.REEL) { ReelScreen() }

    composable(Routes.DELETED) {
        RecentlyDeletedScreen(onBack = { nav.popBackStack() })
    }

    composable(Routes.DAY_PATTERN, arguments = dateArg) {
        DayScreen(
            onBack = { nav.popBackStack() },
            onRecord = { nav.navigate(Routes.camera(it)) },
            onPickFromGallery = { nav.navigate(Routes.galleryDay(it)) },
            onWrite = { date, entryId -> nav.navigate(Routes.write(date, entryId)) },
            onVoice = { nav.navigate(Routes.voice(it)) },
            onDraw = { nav.navigate(Routes.pen(it)) },
            onMoved = ::showDay,
        )
    }

    composable(Routes.GALLERY_DAY_PATTERN, arguments = dateArg) { entry ->
        val date = entry.date()
        GalleryDayScreen(
            // Back from the trim screen lands here again, so a different video
            // from the same day can be tried.
            onPicked = { file -> nav.navigate(Routes.trim(date, file)) },
            onRecordInstead = { nav.navigate(Routes.camera(date)) },
            onBack = { nav.popBackStack() },
        )
    }

    composable(Routes.CAMERA_PATTERN, arguments = dateArg) { entry ->
        val date = entry.date()
        CameraScreen(
            date = date,
            onCaptured = { file -> nav.navigate(Routes.trim(date, file)) },
            onBack = { nav.popBackStack() },
        )
    }

    composable(
        route = Routes.TRIM_PATTERN,
        arguments = dateArg + navArgument(Routes.ARG_PATH) { type = NavType.StringType },
    ) { entry ->
        TrimScreen(
            // Saving lands on the day's page rather than back at the camera, so the
            // finished day and the "saved" message are what the user sees next.
            onSaved = { showDay(entry.date()) },
            onBack = { nav.popBackStack() },
        )
    }

    composable(
        route = Routes.WRITE_PATTERN,
        arguments = dateArg + navArgument(Routes.ARG_ENTRY) {
            type = NavType.LongType
            defaultValue = Routes.NO_ENTRY
        },
    ) { entry ->
        WriteScreen(
            date = entry.date(),
            entryId = entry.arguments!!.getLong(Routes.ARG_ENTRY),
            onDone = { showDay(entry.date()) },
            onBack = { nav.popBackStack() },
        )
    }

    composable(Routes.VOICE_PATTERN, arguments = dateArg) { entry ->
        VoiceScreen(date = entry.date(), onDone = { showDay(entry.date()) }, onBack = { nav.popBackStack() })
    }

    composable(Routes.PEN_PATTERN, arguments = dateArg) { entry ->
        PenScreen(date = entry.date(), onDone = { showDay(entry.date()) }, onBack = { nav.popBackStack() })
    }

    composable(
        route = Routes.RECAP_PATTERN,
        arguments = listOf(navArgument(Routes.ARG_KEY) { type = NavType.StringType }),
    ) {
        RecapScreen(
            onBack = { nav.popBackStack() },
            onOpenRecap = { period ->
                // Stepping to the previous or next period replaces this page, so Back
                // returns to the list rather than walking through every period seen.
                nav.popBackStack()
                nav.navigate(Routes.recap(period))
            },
            onOpenDay = { nav.navigate(Routes.day(it)) },
            onOpenPixels = { nav.navigate(Routes.pixels(it)) },
            onWrite = { nav.navigate(Routes.write(it)) },
        )
    }

    composable(
        route = Routes.PIXELS_PATTERN,
        arguments = listOf(navArgument(Routes.ARG_YEAR) { type = NavType.IntType }),
    ) {
        PixelsScreen(onBack = { nav.popBackStack() })
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val Tabs = listOf(
    Tab(Routes.DAYS, "Days", LineIcons.Days),
    Tab(Routes.RECAPS, "Recaps", LineIcons.Recaps),
    Tab(Routes.REEL, "Reel", LineIcons.Reel),
)

@Composable
private fun TabBar(current: String?, nav: NavHostController) {
    Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
        Hairline()
        Row(Modifier.fillMaxWidth().height(64.dp)) {
            Tabs.forEach { tab ->
                val selected = tab.route == current
                val color = if (selected) Palette.Ink else Palette.Muted
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab,
                        ) {
                            if (!selected) {
                                nav.navigate(tab.route) {
                                    // One copy of each tab, and each keeps its own state
                                    // (the month shown, the year shown) while away.
                                    popUpTo(Routes.DAYS) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                        .semantics { this.selected = selected },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(tab.icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
                    Text(tab.label, style = AppType.Tab, color = color, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}
