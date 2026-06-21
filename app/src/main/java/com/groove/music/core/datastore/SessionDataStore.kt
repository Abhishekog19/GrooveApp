package com.groove.music.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.sessionDataStore: DataStore<Preferences>
        by preferencesDataStore(name = "groove_session")

/**
 * Persistent session storage using DataStore<Preferences>.
 *
 * Mirrors localStorage session in the web app (restoreSession / saveSession).
 * Keys mirror the session object shape used in store.js restoreSession().
 */
@Singleton
class SessionDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val CURRENT_SONG_ID = longPreferencesKey("current_song_id")
        val QUEUE_IDS       = stringPreferencesKey("queue_ids")       // comma-separated longs
        val CURRENT_TIME_MS = longPreferencesKey("current_time_ms")
        val VOLUME          = floatPreferencesKey("volume")
        val REPEAT          = stringPreferencesKey("repeat")           // "NONE"|"ONE"|"ALL"
        val SHUFFLE         = booleanPreferencesKey("shuffle")
    }

    // ── Save (called on every player state change) ────────────────────────────
    suspend fun saveSession(
        songId: Long,
        queueIds: List<Long>,
        currentTimeMs: Long,
        volume: Float,
        repeat: String,
        shuffle: Boolean
    ) {
        context.sessionDataStore.edit { prefs ->
            prefs[Keys.CURRENT_SONG_ID] = songId
            prefs[Keys.QUEUE_IDS]       = queueIds.joinToString(",")
            prefs[Keys.CURRENT_TIME_MS] = currentTimeMs
            prefs[Keys.VOLUME]          = volume
            prefs[Keys.REPEAT]          = repeat
            prefs[Keys.SHUFFLE]         = shuffle
        }
    }

    // ── Load (called once on cold start) ─────────────────────────────────────
    val sessionFlow: Flow<SavedSession?> = context.sessionDataStore.data.map { prefs ->
        val songId = prefs[Keys.CURRENT_SONG_ID] ?: return@map null
        val queueStr = prefs[Keys.QUEUE_IDS] ?: ""
        val queueIds = if (queueStr.isBlank()) emptyList()
                       else queueStr.split(",").mapNotNull { it.toLongOrNull() }

        SavedSession(
            songId      = songId,
            queueIds    = queueIds,
            currentTimeMs = prefs[Keys.CURRENT_TIME_MS] ?: 0L,
            volume      = prefs[Keys.VOLUME] ?: 0.7f,
            repeat      = prefs[Keys.REPEAT] ?: "NONE",
            shuffle     = prefs[Keys.SHUFFLE] ?: false
        )
    }

    suspend fun clearSession() {
        context.sessionDataStore.edit { it.clear() }
    }
}

data class SavedSession(
    val songId: Long,
    val queueIds: List<Long>,
    val currentTimeMs: Long,
    val volume: Float,
    val repeat: String,
    val shuffle: Boolean
)
