package com.onedayonesecond.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.io.IOException
import java.time.LocalDate

private val Context.pendingRemovalsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "drive_pending_removals",
)

/**
 * Days whose copy in Drive is out of date because the clip was moved to another day.
 *
 * Backup never deletes anything on its own — a clip deleted on the phone stays in
 * Drive. But a moved clip is different: leaving its old copy behind would bring the
 * clip back on the wrong day after a restore. So a move writes the old day here, and
 * the next backup removes that day's Drive copy and then clears the entry.
 */
class PendingDriveRemovals(context: Context) {

    private val dataStore = context.applicationContext.pendingRemovalsDataStore

    suspend fun all(): Set<LocalDate> = try {
        dataStore.data.first()[DAYS].orEmpty()
            .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
            .toSet()
    } catch (e: IOException) {
        emptySet() // unreadable means nothing is cleaned up this time, never a crash
    }

    suspend fun add(date: LocalDate) {
        dataStore.edit { it[DAYS] = it[DAYS].orEmpty() + date.toString() }
    }

    /** Only the given days are cleared, so a day added while a backup ran is kept. */
    suspend fun remove(dates: Set<LocalDate>) {
        if (dates.isEmpty()) return
        val names = dates.mapTo(HashSet()) { it.toString() }
        dataStore.edit { it[DAYS] = it[DAYS].orEmpty() - names }
    }

    private companion object {
        val DAYS = stringSetPreferencesKey("days")
    }
}
