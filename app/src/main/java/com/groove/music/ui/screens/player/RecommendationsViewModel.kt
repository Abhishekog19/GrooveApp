package com.groove.music.ui.screens.player

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.groove.music.BuildConfig
import com.groove.music.core.network.RecommendationsApiService
import com.groove.music.core.network.SonglinkApiService
import com.groove.music.core.network.dto.RecommendedTrack
import com.groove.music.domain.model.Song
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

private const val TAG            = "RecommendationsVM"
private const val CACHE_TTL_MS   = 30 * 60 * 1000L   // 30 min — mirrors useRecommendations.js
private const val TIMEOUT_MS     = 20_000L            // 20 s timeout — Railway cold-start
private const val DEFAULT_LIMIT  = 8

/** Mirrors the status string in useRecommendations.js */
sealed class RecommendationsState {
    object Idle        : RecommendationsState()
    object Loading     : RecommendationsState()
    data class Ready(val tracks: List<RecommendedTrack>) : RecommendationsState()
    object Unavailable : RecommendationsState()
}

/**
 * Mirrors useRecommendations.js exactly:
 *  - Same state machine: Idle → Loading → Ready | Unavailable
 *  - 30-minute in-memory cache keyed on "title|||artist" (lowercase, trimmed)
 *  - 20-second timeout per request
 *  - Artist normalization: comma-split → take first segment (handles "Artist A, Artist B")
 *  - Cancels the previous in-flight request when the song changes
 *  - playTrack(): resolves a recommended track to a stream URL and plays it
 *
 * Inject into NowPlayingScreen via hiltViewModel().
 */
@HiltViewModel
class RecommendationsViewModel @Inject constructor(
    private val api: RecommendationsApiService,
    private val songlinkApi: SonglinkApiService
) : ViewModel() {

    private val _state = MutableStateFlow<RecommendationsState>(RecommendationsState.Idle)
    val state: StateFlow<RecommendationsState> = _state.asStateFlow()

    /** ID (title|||artist key) of the track being streamed — drives spinner in UI */
    private val _playingKey = MutableStateFlow<String?>(null)
    val playingKey: StateFlow<String?> = _playingKey.asStateFlow()

    /** One-shot error events for Snackbar */
    private val _playError = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val playError: SharedFlow<String> = _playError.asSharedFlow()

    /** In-memory cache: cacheKey → Pair(tracks, fetchedAtMs) */
    private val memCache = ConcurrentHashMap<String, Pair<List<RecommendedTrack>, Long>>()

    private var lastKey: String? = null
    private var currentFetchJob: Job? = null

    // ── Fetch recommendations ─────────────────────────────────────────────────

    /**
     * Called whenever the current song changes.
     * Mirrors the useEffect in useRecommendations.js.
     */
    fun onSongChanged(song: Song?, limit: Int = DEFAULT_LIMIT) {
        val title  = song?.title?.trim()
        val artist = normalizeArtist(song?.artist)

        if (title.isNullOrBlank() || artist.isBlank()) {
            _state.value = RecommendationsState.Idle
            lastKey = null
            return
        }

        val key = "${title.lowercase()}|||${artist.lowercase()}"
        if (key == lastKey) return
        lastKey = key

        currentFetchJob?.cancel()

        // Instant cache hit
        val cached = memCache[key]
        if (cached != null && System.currentTimeMillis() - cached.second < CACHE_TTL_MS) {
            Log.d(TAG, "Cache hit: \"$key\" (${cached.first.size} tracks)")
            _state.value = if (cached.first.isNotEmpty())
                RecommendationsState.Ready(cached.first)
            else
                RecommendationsState.Unavailable
            return
        }

        _state.value = RecommendationsState.Loading

        currentFetchJob = viewModelScope.launch {
            try {
                Log.d(TAG, "Fetching recommendations for: \"$title\" by \"$artist\"")
                val result = withTimeoutOrNull(TIMEOUT_MS) {
                    api.getRecommendations(title = title, artist = artist, limit = limit)
                }

                if (result == null) {
                    Log.w(TAG, "Timed out for: \"$title\"")
                    _state.value = RecommendationsState.Unavailable
                    return@launch
                }

                val tracks = result.tracks.take(limit)
                memCache[key] = Pair(tracks, System.currentTimeMillis())
                Log.d(TAG, "Got ${tracks.size} recommendations for: \"$title\"")
                _state.value = if (tracks.isNotEmpty())
                    RecommendationsState.Ready(tracks)
                else
                    RecommendationsState.Unavailable

            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) return@launch
                Log.w(TAG, "Fetch failed for \"$title\": ${e.message}")
                _state.value = RecommendationsState.Unavailable
            }
        }
    }

    /** Force a re-fetch (user taps "Retry"). */
    fun retry(song: Song?, limit: Int = DEFAULT_LIMIT) {
        lastKey?.let { memCache.remove(it) }
        lastKey = null
        onSongChanged(song, limit)
    }

    // ── Play a recommended track ──────────────────────────────────────────────

    /**
     * Resolves a recommended track to a stream URL and plays it immediately.
     * Mirrors SearchViewModel.playTrackFromSearch — same retry logic (3 attempts,
     * server rotates mirrors on each call), same proxy stream URL construction.
     *
     * @param track         — the recommended track to play
     * @param playerViewModel — to call playStreamUrl() on
     * @param onReady       — called on the Main thread once playback starts
     */
    fun playTrack(
        track: RecommendedTrack,
        playerViewModel: PlayerViewModel,
        onReady: () -> Unit = {}
    ) {
        val trackKey = "${track.title}|||${track.artist}"

        viewModelScope.launch {
            _playingKey.value = trackKey
            val maxAttempts = 3
            var lastException: Exception? = null

            repeat(maxAttempts) { attempt ->
                if (lastException != null && attempt > 0) {
                    kotlinx.coroutines.delay(1500L * attempt)
                }
                try {
                    Log.d(TAG, "Resolving stream (attempt ${attempt + 1}/$maxAttempts): \"${track.title}\"")

                    val resolved = songlinkApi.resolve(
                        title   = track.title,
                        artist  = track.artist,
                        isrc    = track.isrc,
                        quality = "LOSSLESS"
                    )

                    val serverBase = BuildConfig.SERVER_BASE_URL
                        .trimEnd('/')
                    val encodedUrl = URLEncoder.encode(resolved.streamUrl, "UTF-8")
                    val proxyUrl   = "$serverBase/api/tidal-download/stream?url=$encodedUrl"

                    Log.d(TAG, "Streaming via proxy: ${proxyUrl.take(80)}…")

                    playerViewModel.playStreamUrl(
                        streamUrl  = proxyUrl,
                        title      = resolved.title.ifBlank { track.title },
                        artist     = resolved.artist.ifBlank { track.artist },
                        album      = resolved.album.ifBlank { track.album ?: "" },
                        albumArt   = track.albumArt,
                        durationMs = resolved.durationMs.takeIf { it > 0 } ?: track.durationMs
                    )

                    onReady()
                    lastException = null
                    _playingKey.value = null
                    return@launch  // success

                } catch (e: Exception) {
                    lastException = e
                    Log.w(TAG, "Stream attempt ${attempt + 1} failed: ${e.message}")
                }
            }

            // All retries exhausted
            _playingKey.value = null
            val e = lastException ?: return@launch
            val friendlyMsg = when (e) {
                is SocketTimeoutException -> "Connection timed out — try again."
                else -> {
                    val msg = e.message ?: ""
                    when {
                        msg.contains("502") || msg.contains("Bad Gateway") ->
                            "Stream unavailable. Try again in a moment."
                        msg.contains("404") -> "Track not found on TIDAL."
                        else -> "Playback failed: ${msg.take(80)}"
                    }
                }
            }
            Log.e(TAG, "Play failed after $maxAttempts attempts: ${e.message}", e)
            _playError.emit(friendlyMsg)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Takes first comma-separated artist segment — mirrors useRecommendations.js */
    private fun normalizeArtist(raw: String?): String =
        raw?.split(",")?.firstOrNull()?.trim() ?: ""
}
