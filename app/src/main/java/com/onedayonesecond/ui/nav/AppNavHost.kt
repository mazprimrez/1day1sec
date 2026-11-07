package com.onedayonesecond.ui.nav

import androidx.compose.runtime.Composable
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.onedayonesecond.ui.backup.BackupScreen
import com.onedayonesecond.ui.camera.CameraScreen
import com.onedayonesecond.ui.compile.CompileScreen
import com.onedayonesecond.ui.gallery.GalleryDayScreen
import com.onedayonesecond.ui.home.HomeScreen
import com.onedayonesecond.ui.trim.TrimScreen
import java.time.LocalDate

@UnstableApi
@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.HOME) {

        composable(Routes.HOME) {
            HomeScreen(
                onRecord = { date -> navController.navigate(Routes.camera(date)) },
                onCompile = { navController.navigate(Routes.COMPILE) },
                onBackup = { navController.navigate(Routes.BACKUP) },
                onPickFromGallery = { date -> navController.navigate(Routes.galleryDay(date)) },
            )
        }

        composable(
            route = Routes.GALLERY_DAY_PATTERN,
            arguments = listOf(navArgument(Routes.ARG_DATE) { type = NavType.LongType }),
        ) { entry ->
            val date = LocalDate.ofEpochDay(entry.arguments!!.getLong(Routes.ARG_DATE))
            GalleryDayScreen(
                // Back from the trim screen lands here again, so a different video
                // from the same day can be tried.
                onPicked = { file -> navController.navigate(Routes.trim(date, file)) },
                onRecordInstead = { navController.navigate(Routes.camera(date)) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.CAMERA_PATTERN,
            arguments = listOf(navArgument(Routes.ARG_DATE) { type = NavType.LongType }),
        ) { entry ->
            val date = LocalDate.ofEpochDay(entry.arguments!!.getLong(Routes.ARG_DATE))
            CameraScreen(
                onCaptured = { file -> navController.navigate(Routes.trim(date, file)) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.TRIM_PATTERN,
            arguments = listOf(
                navArgument(Routes.ARG_DATE) { type = NavType.LongType },
                navArgument(Routes.ARG_PATH) { type = NavType.StringType },
            ),
        ) {
            TrimScreen(
                // Saving returns all the way home rather than to the camera, so
                // the finished day is visible immediately.
                onSaved = { navController.popBackStack(Routes.HOME, inclusive = false) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.COMPILE) {
            CompileScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.BACKUP) {
            BackupScreen(onBack = { navController.popBackStack() })
        }
    }
}
