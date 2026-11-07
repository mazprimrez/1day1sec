package com.onedayonesecond

import android.content.Context
import androidx.media3.common.util.UnstableApi
import com.onedayonesecond.backup.DriveAuth
import com.onedayonesecond.backup.DriveBackup
import com.onedayonesecond.data.AppDatabase
import com.onedayonesecond.data.ClipRepository
import com.onedayonesecond.data.ClipStorage
import com.onedayonesecond.data.DateStyleStore
import com.onedayonesecond.data.PendingDriveRemovals
import com.onedayonesecond.media.ClipThumbnails
import com.onedayonesecond.media.GalleryImporter
import com.onedayonesecond.media.GalleryScanner
import com.onedayonesecond.media.GalleryThumbnails
import com.onedayonesecond.media.MediaStoreExporter
import com.onedayonesecond.media.VideoCompiler
import com.onedayonesecond.media.VideoTrimmer

/**
 * Hand-rolled dependency container. The graph is small enough that Hilt would
 * add more ceremony than it removes.
 */
@UnstableApi
class AppContainer(context: Context) {

    private val appContext = context.applicationContext
    private val database by lazy { AppDatabase.build(appContext) }

    val storage by lazy { ClipStorage(appContext) }
    val repository by lazy { ClipRepository(database.clipDao(), storage) }
    val trimmer by lazy { VideoTrimmer(appContext) }
    val compiler by lazy { VideoCompiler(appContext) }
    val exporter by lazy { MediaStoreExporter(appContext) }
    val dateStyleStore by lazy { DateStyleStore(appContext) }
    val importer by lazy { GalleryImporter(appContext, storage) }
    val galleryScanner by lazy { GalleryScanner(appContext) }
    val thumbnails by lazy { GalleryThumbnails(appContext) }
    val clipThumbnails by lazy { ClipThumbnails() }
    val driveAuth by lazy { DriveAuth(appContext) }
    val pendingDriveRemovals by lazy { PendingDriveRemovals(appContext) }
    val driveBackup by lazy { DriveBackup(repository, pendingDriveRemovals) }
}
