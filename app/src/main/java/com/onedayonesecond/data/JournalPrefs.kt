package com.onedayonesecond.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.journalDataStore: DataStore<Preferences> by preferencesDataStore(name = "journal")

/** Why a recap that was due is not there. */
enum class RecapFailureReason { OFFLINE, FAILED, NOT_SET_UP }

data class RecapFailure(val reason: RecapFailureReason, val at: Long)

/** A thought that was being written when "Log a thought" was closed, kept for next time. */
data class ThoughtDraft(
    val page: Int,
    val text: String,
    val showInDiary: Boolean,
    /** A voice note or page photo already attached. Scratch files do not outlive the app, so it may be gone. */
    val mediaPath: String? = null,
    val mediaType: ThoughtType? = null,
)

/** Small things the journal remembers: the name letters are addressed to, and failed recaps. */
class JournalPrefs(context: Context) {

    private val dataStore = context.applicationContext.journalDataStore

    private val safeData: Flow<Preferences> = dataStore.data.catch { e ->
        if (e is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw e
    }

    /** What "Dear …," says. Empty until the user gives a name. */
    val name: Flow<String> = safeData.map { it[NAME].orEmpty() }

    suspend fun setName(name: String) {
        dataStore.edit { it[NAME] = name.trim().take(MAX_NAME_LENGTH) }
    }

    /** True once the app has asked for location, so it asks on first use and never nags. */
    suspend fun locationAsked(): Boolean = safeData.first()[LOCATION_ASKED] ?: false

    suspend fun setLocationAsked() {
        dataStore.edit { it[LOCATION_ASKED] = true }
    }

    /** Recaps that were tried and could not be written, by recap key. */
    val recapFailures: Flow<Map<String, RecapFailure>> = safeData.map { prefs ->
        prefs.asMap().mapNotNull { (key, value) ->
            if (!key.name.startsWith(FAILURE_PREFIX)) return@mapNotNull null
            val parts = (value as? String)?.split('|') ?: return@mapNotNull null
            val reason = RecapFailureReason.entries.firstOrNull { it.name == parts.getOrNull(0) }
            val at = parts.getOrNull(1)?.toLongOrNull()
            if (reason == null || at == null) null else key.name.removePrefix(FAILURE_PREFIX) to RecapFailure(reason, at)
        }.toMap()
    }

    suspend fun setRecapFailure(recapKey: String, failure: RecapFailure?) {
        val key = stringPreferencesKey(FAILURE_PREFIX + recapKey)
        dataStore.edit { if (failure == null) it.remove(key) else it[key] = "${failure.reason.name}|${failure.at}" }
    }

    /** The draft kept for a book, or null. One per book, so switching books does not lose either. */
    suspend fun thoughtDraft(bookId: Long): ThoughtDraft? {
        val stored = safeData.first()[stringPreferencesKey(DRAFT_PREFIX + bookId)] ?: return null
        return try {
            val json = org.json.JSONObject(stored)
            ThoughtDraft(
                page = json.optInt("page", 1),
                text = json.optString("text"),
                showInDiary = json.optBoolean("showInDiary", true),
                mediaPath = json.optString("mediaPath").ifEmpty { null },
                mediaType = ThoughtType.entries.firstOrNull { it.name == json.optString("mediaType") },
            )
        } catch (e: org.json.JSONException) {
            null
        }
    }

    suspend fun setThoughtDraft(bookId: Long, draft: ThoughtDraft?) {
        val key = stringPreferencesKey(DRAFT_PREFIX + bookId)
        dataStore.edit {
            if (draft == null) {
                it.remove(key)
            } else {
                it[key] = org.json.JSONObject()
                    .put("page", draft.page)
                    .put("text", draft.text)
                    .put("showInDiary", draft.showInDiary)
                    .put("mediaPath", draft.mediaPath.orEmpty())
                    .put("mediaType", draft.mediaType?.name.orEmpty())
                    .toString()
            }
        }
    }

    companion object {
        const val MAX_NAME_LENGTH = 40
        private const val DRAFT_PREFIX = "thought_draft_"
        private val NAME = stringPreferencesKey("name")
        private val LOCATION_ASKED = booleanPreferencesKey("location_asked")
        private const val FAILURE_PREFIX = "recap_failure_"
    }
}
