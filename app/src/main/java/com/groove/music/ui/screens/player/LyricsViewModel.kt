package com.groove.music.ui.screens.player

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.groove.music.core.database.dao.LyricsCacheDao
import com.groove.music.core.database.entity.LyricsCacheEntity
import com.groove.music.core.network.LyricsApiService
import com.groove.music.domain.model.Song
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

private const val LYRICS_TAG          = "LyricsVM"
private const val LYRICS_TIMEOUT_MS   = 15_000L   // 15s network timeout
private const val MEM_CACHE_TTL_MS    = 5 * 60 * 1000L  // 5-min in-memory TTL

/** Mirrors useLyrics.js status strings */
sealed class LyricsState {
    object Idle        : LyricsState()
    object Loading     : LyricsState()
    data class Ready(val lyrics: String, val source: String?) : LyricsState()
    object Unavailable : LyricsState()
}

/**
 * Offline-first lyrics loader:
 *
 *   1. In-memory cache hit (5 min TTL)         → instant
 *   2. Room `lyrics_cache` hit                 → instant, works in airplane mode
 *   3. Network fetch (LyricsApiService)         → persisted to Room for next time
 *   4. Timeout / error / 404 → Unavailable
 *      (marked in Room as "unavailable" so we don't hammer the server again for
 *       the same song in the same session)
 *
 * Song is keyed by "title|||artist" (lowercase). For DB lookup the songId (Room id)
 * is used, so lyrics survive across app restarts without any network call.
 */
@HiltViewModel
class LyricsViewModel @Inject constructor(
    private val api: LyricsApiService,
    private val lyricsCacheDao: LyricsCacheDao
) : ViewModel() {

    private val _state = MutableStateFlow<LyricsState>(LyricsState.Idle)
    val state: StateFlow<LyricsState> = _state.asStateFlow()

    // In-memory short-term cache keyed on "title|||artist" (lowercase)
    // Value: Triple<lyricsText, source, timestampMs>
    private val memCache = ConcurrentHashMap<String, Triple<String, String?, Long>>()

    private var lastKey: String? = null
    private var currentJob: Job? = null

    /**
     * Fetch lyrics for the given song.
     * Call this whenever the current song changes (mirrors useLyrics onSongChanged).
     *
     * @param song  The current song. `song.id > 0` means it's in the Room library.
     */
    fun onSongChanged(song: Song?) {
        val title  = song?.title?.trim()
        val artist = song?.artist?.split(",")?.firstOrNull()?.trim() ?: ""
        val songId = song?.id ?: -1L

        if (title.isNullOrBlank() || artist.isBlank()) {
            _state.value = LyricsState.Idle
            lastKey = null
            return
        }

        val key = "${title.lowercase()}|||${artist.lowercase()}"
        if (key == lastKey) return
        lastKey = key

        currentJob?.cancel()

        // 1. In-memory cache hit
        val cached = memCache[key]
        if (cached != null && System.currentTimeMillis() - cached.third < MEM_CACHE_TTL_MS) {
            Log.d(LYRICS_TAG, "Mem-cache hit: \"$key\"")
            _state.value = if (cached.first.isBlank()) LyricsState.Unavailable
                           else LyricsState.Ready(lyrics = cached.first, source = cached.second)
            return
        }

        _state.value = LyricsState.Loading

        currentJob = viewModelScope.launch {
            // 2. Room DB cache hit (offline-first — works in airplane mode)
            if (songId > 0L) {
                val dbEntry = lyricsCacheDao.find(songId)
                if (dbEntry != null) {
                    when (dbEntry.lyricsType) {
                        "unavailable" -> {
                            Log.d(LYRICS_TAG, "DB cache: unavailable for songId=$songId")
                            memCache[key] = Triple("", null, System.currentTimeMillis())
                            _state.value = LyricsState.Unavailable
                            return@launch
                        }
                        else -> if (!dbEntry.lyricsText.isNullOrBlank()) {
                            Log.d(LYRICS_TAG, "DB cache hit: \"$title\" (${dbEntry.lyricsType})")
                            memCache[key] = Triple(dbEntry.lyricsText, dbEntry.provider, System.currentTimeMillis())
                            _state.value = LyricsState.Ready(
                                lyrics = dbEntry.lyricsText,
                                source = dbEntry.provider
                            )
                            return@launch
                        }
                    }
                }
            }

            // 3. Network fetch
            try {
                Log.d(LYRICS_TAG, "Fetching lyrics: \"$title\" by \"$artist\"")

                val result = withTimeoutOrNull(LYRICS_TIMEOUT_MS) {
                    api.getLyrics(title = title, artist = artist)
                }

                when {
                    result == null -> {
                        Log.w(LYRICS_TAG, "Timeout: \"$title\"")
                        persistToDb(songId, type = "unavailable", text = null, source = null)
                        memCache[key] = Triple("", null, System.currentTimeMillis())
                        _state.value = LyricsState.Unavailable
                    }
                    result.lyrics.isNullOrBlank() -> {
                        Log.d(LYRICS_TAG, "No lyrics: \"$title\"")
                        persistToDb(songId, type = "unavailable", text = null, source = result.source)
                        memCache[key] = Triple("", result.source, System.currentTimeMillis())
                        _state.value = LyricsState.Unavailable
                    }
                    else -> {
                        Log.d(LYRICS_TAG, "Got lyrics: \"$title\" (source: ${result.source})")
                        persistToDb(songId, type = "plain", text = result.lyrics, source = result.source)
                        memCache[key] = Triple(result.lyrics, result.source, System.currentTimeMillis())
                        _state.value = LyricsState.Ready(lyrics = result.lyrics, source = result.source)
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) return@launch
                Log.w(LYRICS_TAG, "Fetch failed for \"$title\": ${e.message}")
                _state.value = LyricsState.Unavailable
            }
        }
    }

    /** Force re-fetch — clears both mem-cache and DB entry for this song */
    fun retry(song: Song?) {
        val key = lastKey ?: return
        memCache.remove(key)
        lastKey = null
        val songId = song?.id ?: -1L
        if (songId > 0L) {
            viewModelScope.launch { lyricsCacheDao.delete(songId) }
        }
        onSongChanged(song)
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Persist lyrics (or "unavailable") to the Room lyrics_cache table.
     * Only stored for library songs (songId > 0). Streaming-only temp songs
     * have negative IDs and are not persisted.
     */
    private suspend fun persistToDb(
        songId: Long,
        type: String,
        text: String?,
        source: String?
    ) {
        if (songId <= 0L) return   // streaming-only temp song, nothing to persist
        try {
            lyricsCacheDao.upsert(
                LyricsCacheEntity(
                    songId              = songId,
                    lyricsType          = type,
                    lyricsText          = text,
                    syncedLinesJson     = null,   // synced lines reserved for future
                    provider            = source,
                    isOfflineAvailable  = !text.isNullOrBlank()
                )
            )
        } catch (e: Exception) {
            Log.w(LYRICS_TAG, "Failed to persist lyrics to DB: ${e.message}")
        }
    }
}
