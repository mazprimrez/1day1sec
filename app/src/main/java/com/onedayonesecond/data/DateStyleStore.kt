package com.onedayonesecond.data

import androidx.datastore.preferences.core.booleanPreferencesKey
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.onedayonesecond.media.DateAlign
import com.onedayonesecond.media.DateEdge
import com.onedayonesecond.media.DateFont
import com.onedayonesecond.media.DateLabelStyle
import com.onedayonesecond.media.enumOrDefault
import kotlinx.coroutines.flow.first
import java.io.IOException

private val Context.dateStyleDataStore: DataStore<Preferences> by preferencesDataStore(name = "date_style")

/** What is printed on each second of a reel, in which style, and the range a reel starts with. */
data class ReelSettings(
    val style: DateLabelStyle = DateLabelStyle(),
    val showDates: Boolean = true,
    val showCaptions: Boolean = true,
    val showPlaces: Boolean = true,
    /** The name of the range "Make my reel" starts with; null until one is chosen in Settings. */
    val startRange: String? = null,
)

/** Remembers the reel's settings, so they do not have to be set every time. */
class DateStyleStore(context: Context) {

    private val dataStore = context.applicationContext.dateStyleDataStore

    suspend fun load(): ReelSettings = try {
        val saved = dataStore.data.first()
        ReelSettings(
            style = DateLabelStyle(
                font = enumOrDefault(saved[FONT], DateFont.CLASSIC),
                align = enumOrDefault(saved[ALIGN], DateAlign.LEFT),
                edge = enumOrDefault(saved[EDGE], DateEdge.BOTTOM),
            ),
            showDates = saved[SHOW_DATES] ?: true,
            showCaptions = saved[SHOW_CAPTIONS] ?: true,
            showPlaces = saved[SHOW_PLACES] ?: true,
            startRange = saved[START_RANGE],
        )
    } catch (e: IOException) {
        ReelSettings() // an unreadable settings file just means "defaults"
    }

    suspend fun save(style: DateLabelStyle) {
        dataStore.edit {
            it[FONT] = style.font.name
            it[ALIGN] = style.align.name
            it[EDGE] = style.edge.name
        }
    }

    suspend fun saveShown(dates: Boolean, captions: Boolean, places: Boolean) {
        dataStore.edit {
            it[SHOW_DATES] = dates
            it[SHOW_CAPTIONS] = captions
            it[SHOW_PLACES] = places
        }
    }

    suspend fun saveStartRange(name: String) {
        dataStore.edit { it[START_RANGE] = name }
    }

    private companion object {
        val FONT = stringPreferencesKey("font")
        val ALIGN = stringPreferencesKey("align")
        val EDGE = stringPreferencesKey("edge")
        val SHOW_DATES = booleanPreferencesKey("show_dates")
        val SHOW_CAPTIONS = booleanPreferencesKey("show_captions")
        val SHOW_PLACES = booleanPreferencesKey("show_places")
        val START_RANGE = stringPreferencesKey("start_range")
    }
}
