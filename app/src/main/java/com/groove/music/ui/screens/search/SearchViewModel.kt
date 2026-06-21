package com.groove.music.ui.screens.search

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.groove.music.core.database.dao.DownloadQueueDao
import com.groove.music.core.database.entity.DownloadQueueEntity
import com.groove.music.core.network.SonglinkApiService
import com.groove.music.core.network.dto.TidalSearchTrack
import com.groove.music.data.repository.SongRepository
import com.groove.music.data.worker.DownloadWorker
import com.groove.music.domain.model.Song
import com.groove.music.domain.model.toFormattedDuration
import com.groove.music.ui.screens.player.PlayerViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import java.util.Locale
import java.net.SocketTimeoutException

private const val TAG = "SearchViewModel"

/** Mirrors the memCache in useSearch.js / useRecommendations.js */
private data class CachedResults(
    val tracks: List<SearchTrackUi>,
    val fetchedAtMs: Long
)
private const val SEARCH_CACHE_TTL_MS = 30 * 60 * 1000L // 30 minutes

data class SearchTrackUi(
    val track: TidalSearchTrack,
    val durationFormatted: String,
    val localSong: Song? = null,
    val downloadStatus: String? = null  // queued / resolving / downloading / done / failed / null
) {
    val isInLibrary: Boolean get() = localSong != null
    val isDownloading: Boolean get() = downloadStatus == "queued" ||
            downloadStatus == "resolving" || downloadStatus == "downloading"
}

sealed class SearchUiState {
    object Idle : SearchUiState()
    object Loading : SearchUiState()
    data class Results(val tracks: List<SearchTrackUi>) : SearchUiState()
    data class Error(val message: String) : SearchUiState()
}

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val songlinkApi: SonglinkApiService,
    private val songRepository: SongRepository,
    private val downloadQueueDao: DownloadQueueDao
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _state = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    /** ID of the track currently being resolved for streaming playback */
    private val _streamingTrackId = MutableStateFlow<Long?>(null)
    val streamingTrackId: StateFlow<Long?> = _streamingTrackId.asStateFlow()

    /** One-shot events for showing error Snackbar in the UI */
    private val _streamError = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val streamError: SharedFlow<String> = _streamError.asSharedFlow()

    // ── 30-min search result cache (mirrors memCache in useSearch.js) ────────
    private val searchCache = ConcurrentHashMap<String, CachedResults>()

    // Cached download status flows (same pattern as ImportViewModel)
    private val statusFlows = ConcurrentHashMap<String, StateFlow<String?>>() 

    init {
        // Auto-search with debounce: fire search 500ms after the user stops typing
        viewModelScope.launch {
            _query
                .debounce(500)
                .filter { it.trim().length >= 2 }
                .distinctUntilChanged()
                .collect { q ->
                    performSearch(q.trim())
                }
        }
    }

    fun updateQuery(newQuery: String) {
        _query.value = newQuery
        if (newQuery.isBlank()) {
            _state.value = SearchUiState.Idle
        }
    }

    fun searchNow() {
        val q = _query.value.trim()
        if (q.length >= 2) {
            viewModelScope.launch { performSearch(q) }
        }
    }

    private suspend fun performSearch(query: String) {
        // ── Cache check (30-min TTL) ──────────────────────────────────────────
        val cacheKey = query.trim().lowercase(Locale.getDefault())
        val cached = searchCache[cacheKey]
        if (cached != null && System.currentTimeMillis() - cached.fetchedAtMs < SEARCH_CACHE_TTL_MS) {
            Log.d(TAG, "Cache hit for: \"$query\" (${cached.tracks.size} results)")
            _state.value = SearchUiState.Results(cached.tracks)
            return
        }

        _state.value = SearchUiState.Loading
        val maxAttempts = 4
        var lastError: Exception? = null

        repeat(maxAttempts) { attempt ->
            try {
                Log.d(TAG, "Searching (attempt ${attempt + 1}/$maxAttempts): \"$query\"")
                val response = songlinkApi.searchTracks(query, limit = 20)

                val tracks = response.results.map { track ->
                    val localSong = songRepository.findByTitleAndArtist(track.title, track.artist)
                    val dbStatus  = downloadQueueDao.getStatus(track.id.toString())
                    SearchTrackUi(
                        track             = track,
                        durationFormatted = track.durationMs.toFormattedDuration(),
                        localSong         = localSong,
                        downloadStatus    = dbStatus
                    )
                }

                // Store in cache
                searchCache[cacheKey] = CachedResults(tracks, System.currentTimeMillis())
                _state.value = SearchUiState.Results(tracks)
                Log.d(TAG, "Found ${tracks.size} results for \"$query\" on attempt ${attempt + 1}")
                return  // ← success, exit the repeat block
            } catch (e: Exception) {
                lastError = e
                Log.w(TAG, "Search attempt ${attempt + 1} failed: ${e.message}")

                if (attempt < maxAttempts - 1) {
                    // Brief pause before trying the next mirror — server-side
                    // rotates mirrors on each call, so a retry usually hits a different one
                    kotlinx.coroutines.delay(1500L * (attempt + 1))
                }
            }
        }

        // All attempts exhausted
        val msg = lastError?.message ?: "Search failed"
        val friendly = when {
            msg.contains("502") || msg.contains("Bad Gateway") ->
                "TIDAL mirrors are busy. Please retry in a moment."
            msg.contains("timeout", ignoreCase = true) ->
                "Search timed out. Check your connection."
            else -> "Search failed: ${msg.take(60)}"
        }
        Log.e(TAG, "Search failed after $maxAttempts attempts: $msg", lastError)
        _state.value = SearchUiState.Error(friendly)
    }

    // ── Instant playback from search results ─────────────────────────────────

    /**
     * Play a search result immediately.
     * Retries the resolve call up to 3 times — server rotates mirrors on each call,
     * so a retry usually hits a different (working) mirror.
     */
    fun playTrackFromSearch(
        track: TidalSearchTrack,
        playerViewModel: PlayerViewModel,
        onReady: () -> Unit
    ) {
        viewModelScope.launch {
            _streamingTrackId.value = track.id
            val maxAttempts = 3
            var lastException: Exception? = null

            repeat(maxAttempts) { attempt ->
                if (lastException != null && attempt > 0) {
                    // Already failed once — short pause so server picks a different mirror
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

                    val serverBaseUrl = com.groove.music.BuildConfig.SERVER_BASE_URL
                    val proxyStreamUrl = "${serverBaseUrl}/api/tidal-download/stream?url=${
                        java.net.URLEncoder.encode(resolved.streamUrl, "UTF-8")
                    }"

                    Log.d(TAG, "Streaming via proxy (attempt ${attempt + 1}): ${proxyStreamUrl.take(80)}…")

                    playerViewModel.playStreamUrl(
                        streamUrl  = proxyStreamUrl,
                        title      = resolved.title.ifBlank { track.title },
                        artist     = resolved.artist.ifBlank { track.artist },
                        album      = resolved.album,
                        albumArt   = track.albumArt,
                        durationMs = resolved.durationMs
                    )

                    onReady()
                    lastException = null
                    return@launch  // ← success
                } catch (e: Exception) {
                    lastException = e
                    Log.w(TAG, "Stream attempt ${attempt + 1} failed: ${e.message}")
                }
            }

            // All retries exhausted — show user-friendly error
            val e = lastException ?: return@launch
            Log.e(TAG, "Stream resolve failed after $maxAttempts attempts: ${e.message}", e)
            val friendlyMsg = when (e) {
                is SocketTimeoutException -> "Connection timed out. The server may be busy — try again."
                else -> {
                    val msg = e.message ?: ""
                    when {
                        msg.contains("502") || msg.contains("Bad Gateway") ->
                            "Stream unavailable. Try again in a moment."
                        msg.contains("404") -> "Track not found on TIDAL."
                        msg.contains("No stream URL") || msg.contains("all qualities") ->
                            "Could not get audio URL — try a different quality or track."
                        msg.contains("All TIDAL proxy") ->
                            "All TIDAL mirrors are down. Please try again later."
                        else -> "Playback failed: ${msg.take(80)}"
                    }
                }
            }
            _streamError.emit(friendlyMsg)
            _streamingTrackId.value = null
        }
    }

    // ── Download ─────────────────────────────────────────────────────────────

    /**
     * Download a track from search results.
     * Inserts into the download queue DB and starts a WorkManager worker.
     */
    fun downloadTrack(track: TidalSearchTrack) {
        viewModelScope.launch {
            try {
                // Insert into download queue
                val entity = DownloadQueueEntity(
                    trackId = track.id.toString(),
                    title = track.title,
                    artist = track.artist,
                    albumArt = track.albumArt,
                    spotifyUrl = "",  // no Spotify URL for direct TIDAL search results
                    isrc = track.isrc,
                    status = "queued"
                )
                downloadQueueDao.insertOrUpdate(entity)

                // Update UI state
                updateTrackStatus(track.id, "queued")

                // Start WorkManager
                val inputData = Data.Builder()
                    .putString("trackId", track.id.toString())
                    .putString("title", track.title)
                    .putString("artist", track.artist)
                    .putString("isrc", track.isrc)
                    .build()

                val request = OneTimeWorkRequestBuilder<DownloadWorker>()
                    .setInputData(inputData)
                    .build()

                WorkManager.getInstance(context).enqueueUniqueWork(
                    track.id.toString(),
                    ExistingWorkPolicy.KEEP,
                    request
                )

                Log.d(TAG, "Enqueued download: \"${track.title}\" by ${track.artist}")
            } catch (e: Exception) {
                Log.e(TAG, "Download enqueue failed: ${e.message}", e)
                updateTrackStatus(track.id, "failed")
            }
        }
    }

    /**
     * Get a CACHED download status flow for a track.
     */
    fun getDownloadStatusFlow(trackId: String): StateFlow<String?> =
        statusFlows.getOrPut(trackId) {
            downloadQueueDao.getByIdFlow(trackId)
                .map { it?.status }
                .distinctUntilChanged()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
        }

    private fun updateTrackStatus(tidalId: Long, status: String) {
        val current = _state.value as? SearchUiState.Results ?: return
        val updated = current.tracks.map { sui ->
            if (sui.track.id == tidalId) sui.copy(downloadStatus = status) else sui
        }
        _state.value = current.copy(tracks = updated)
    }
}
