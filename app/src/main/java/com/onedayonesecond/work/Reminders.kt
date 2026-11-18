package com.onedayonesecond.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.content.getSystemService
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.onedayonesecond.R
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

object Reminders {

    const val CHANNEL_ID = "daily_reminder"
    const val NOTIFICATION_ID = 1001
    private const val WORK_NAME = "daily_reminder_check"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.reminder_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.reminder_channel_description)
        }
        context.getSystemService<NotificationManager>()?.createNotificationChannel(channel)
    }

    /**
     * Checks once a day at [time], and reminds if that day has no second yet.
     *
     * @param replace true when the time has just been changed: the check already
     *   waiting is dropped for one at the new time. False on app start, so that
     *   opening the app does not reset the daily cadence.
     */
    fun schedule(context: Context, time: LocalTime, replace: Boolean = false) {
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(Duration.ofDays(1))
            .setInitialDelay(delayUntil(time, LocalDateTime.now()))
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            if (replace) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    /** How long from [now] until the clock next reads [time]: later today, or tomorrow. */
    fun delayUntil(time: LocalTime, now: LocalDateTime): Duration {
        var next = now.toLocalDate().atTime(time)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next)
    }
}
