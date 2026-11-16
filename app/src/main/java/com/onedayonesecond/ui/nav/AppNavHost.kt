package com.onedayonesecond.ui.nav

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
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
            // Not over the camera, where it would sit on the record button. A message
            // still waiting is shown again, with its Undo, on the next screen.
            message?.takeIf { route != Routes.CAMERA_PATTERN }?.let { shown ->
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
        NavHost(navController = navController, startDestination = Routes.DAYS) {
            destinations(navController, padding)
        }
    }
}

/**
 * A screen that sits between the system bars (and above the tab bar, when it shows).
 * The padding is applied by each screen rather than by the host, so that while one
 * screen fades into another, each keeps its own: the camera, which draws under the
 * bars, does not drag the screen it is replacing up with it. It is consumed as well
 * as applied, so a screen that pads for the keyboard does not add the system bar's
 * height a second time.
 */
private fun androidx.navigation.NavGraphBuilder.screen(
    route: String,
    padding: PaddingValues,
    arguments: List<androidx.navigation.NamedNavArgument> = emptyList(),
    content: @Composable (androidx.navigation.NavBackStackEntry) -> Unit,
) {
    composable(route, arguments = arguments) { entry ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) { content(entry) }
    }
}

@UnstableApi
private fun androidx.navigation.NavGraphBuilder.destinations(nav: NavHostController, padding: PaddingValues) {
    val dateArg = listOf(navArgument(Routes.ARG_DATE) { type = NavType.LongType })
    fun androidx.navigation.NavBackStackEntry.date(): LocalDate =
        LocalDate.ofEpochDay(arguments!!.getLong(Routes.ARG_DATE))

    /** After something is added to a day: show that day, with the calendar one step behind it. */
    fun showDay(date: LocalDate) {
        nav.popBackStack(Routes.DAYS, inclusive = false)
        nav.navigate(Routes.day(date))
    }

    screen(Routes.DAYS, padding) {
        HomeScreen(
            onOpenDay = { nav.navigate(Routes.day(it)) },
            onRecord = { nav.navigate(Routes.camera(it)) },
            onWrite = { nav.navigate(Routes.write(it)) },
            onVoice = { nav.navigate(Routes.voice(it)) },
            onDraw = { nav.navigate(Routes.pen(it)) },
            onRecentlyDeleted = { nav.navigate(Routes.DELETED) },
        )
    }

    screen(Routes.RECAPS, padding) {
        RecapsScreen(
            onOpenRecap = { nav.navigate(Routes.recap(it)) },
            onOpenPixels = { nav.navigate(Routes.pixels(it)) },
        )
    }

    screen(Routes.REEL, padding) { ReelScreen() }

    screen(Routes.DELETED, padding) {
        RecentlyDeletedScreen(onBack = { nav.popBackStack() })
    }

    screen(Routes.DAY_PATTERN, padding, dateArg) {
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

    screen(Routes.GALLERY_DAY_PATTERN, padding, dateArg) { entry ->
        val date = entry.date()
        GalleryDayScreen(
            // Back from the trim screen lands here again, so a different video
            // from the same day can be tried.
            onPicked = { file -> nav.navigate(Routes.trim(date, file)) },
            onRecordInstead = { nav.navigate(Routes.camera(date)) },
            onBack = { nav.popBackStack() },
        )
    }

    // The camera draws under the system bars and pads its own controls.
    composable(Routes.CAMERA_PATTERN, arguments = dateArg) { entry ->
        val date = entry.date()
        CameraScreen(
            date = date,
            onCaptured = { file -> nav.navigate(Routes.trim(date, file)) },
            onBack = { nav.popBackStack() },
        )
    }

    screen(
        Routes.TRIM_PATTERN,
        padding,
        dateArg + navArgument(Routes.ARG_PATH) { type = NavType.StringType },
    ) { entry ->
        TrimScreen(
            // Saving lands on the day's page rather than back at the camera, so the
            // finished day and the "saved" message are what the user sees next.
            onSaved = { showDay(entry.date()) },
            onBack = { nav.popBackStack() },
        )
    }

    screen(
        Routes.WRITE_PATTERN,
        padding,
        dateArg + navArgument(Routes.ARG_ENTRY) {
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

    screen(Routes.VOICE_PATTERN, padding, dateArg) { entry ->
        VoiceScreen(date = entry.date(), onDone = { showDay(entry.date()) }, onBack = { nav.popBackStack() })
    }

    screen(Routes.PEN_PATTERN, padding, dateArg) { entry ->
        PenScreen(date = entry.date(), onDone = { showDay(entry.date()) }, onBack = { nav.popBackStack() })
    }

    screen(
        Routes.RECAP_PATTERN,
        padding,
        listOf(navArgument(Routes.ARG_KEY) { type = NavType.StringType }),
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

    screen(
        Routes.PIXELS_PATTERN,
        padding,
        listOf(navArgument(Routes.ARG_YEAR) { type = NavType.IntType }),
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
