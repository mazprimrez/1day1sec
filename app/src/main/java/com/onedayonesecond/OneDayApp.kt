package com.onedayonesecond

import android.app.Application
import androidx.media3.common.util.UnstableApi
import com.onedayonesecond.work.Reminders

@UnstableApi
class OneDayApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Reminders.createChannel(this)
        Reminders.schedule(this)
        // Recordings and reels from a previous run are scratch data by
        // definition — a finished clip has already been moved into filesDir.
        container.storage.clearCaptures()
        container.storage.clearReels()
    }
}
