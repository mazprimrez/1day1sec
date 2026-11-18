package com.onedayonesecond.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.io.IOException

private val Context.pendingBookRemovalsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "drive_pending_book_removals",
)

/** Told when a book is removed, so its copy in Drive can be removed too. */
fun interface BookRemovalLog {
    suspend fun add(backupKey: String)
}

/**
 * Books removed from the shelf whose files may still be in Drive.
 *
 * Backup never deletes anything on its own. But a book removed on purpose, often one
 * added by mistake, would come back on the next restore if its copy stayed in Drive.
 * So removing a book writes its backup key here, and the next backup removes that
 * book's files from Drive and then clears the entry.
 */
class PendingBookRemovals(context: Context) : BookRemovalLog {

    private val dataStore = context.applicationContext.pendingBookRemovalsDataStore

    suspend fun all(): Set<String> = try {
        dataStore.data.first()[KEYS].orEmpty()
    } catch (e: IOException) {
        emptySet() // unreadable means nothing is cleaned up this time, never a crash
    }

    override suspend fun add(backupKey: String) {
        dataStore.edit { it[KEYS] = it[KEYS].orEmpty() + backupKey }
    }

    /** Only the given books are cleared, so one removed while a backup ran is kept. */
    suspend fun remove(keys: Set<String>) {
        if (keys.isEmpty()) return
        dataStore.edit { it[KEYS] = it[KEYS].orEmpty() - keys }
    }

    private companion object {
        val KEYS = stringSetPreferencesKey("books")
    }
}
