package com.onedayonesecond.data

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

/** Remembers the chosen date caption style, so it does not have to be set every time. */
class DateStyleStore(context: Context) {

    private val dataStore = context.applicationContext.dateStyleDataStore

    suspend fun load(): DateLabelStyle = try {
        val saved = dataStore.data.first()
        DateLabelStyle(
            font = enumOrDefault(saved[FONT], DateFont.CLASSIC),
            align = enumOrDefault(saved[ALIGN], DateAlign.LEFT),
            edge = enumOrDefault(saved[EDGE], DateEdge.BOTTOM),
        )
    } catch (e: IOException) {
        DateLabelStyle() // an unreadable settings file just means "defaults"
    }

    suspend fun save(style: DateLabelStyle) {
        dataStore.edit {
            it[FONT] = style.font.name
            it[ALIGN] = style.align.name
            it[EDGE] = style.edge.name
        }
    }

    private companion object {
        val FONT = stringPreferencesKey("font")
        val ALIGN = stringPreferencesKey("align")
        val EDGE = stringPreferencesKey("edge")
    }
}
