package com.groove.music.ui.screens.imports

import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.groove.music.core.database.dao.DownloadQueueDao
import com.groove.music.core.network.SonglinkApiService
import com.groove.music.core.network.dto.ZipDownloadRequest
import com.groove.music.core.network.dto.ZipTrackRequest
import com.groove.music.data.repository.PlaylistRepository
import com.groove.music.data.repository.SongRepository
import com.groove.music.domain.model.Song
import com.groove.music.domain.model.Track
import com.groove.music.domain.repository.DownloadRepository
import com.groove.music.domain.usecase.ImportProgress
import com.groove.music.domain.usecase.ImportSpotifyPlaylistUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

private const val TAG = "ImportViewModel"

/**
 * Per-track UI state — combines Spotify track metadata with
 * local library match result and download queue status.
 */
data class TrackUiState(
    val track: Track,
    val localSong: Song? = null,          // non-null if already in library
    val downloadStatus: String? = null,   // queued / resolving / downloading / done / failed / null
    val downloadProgress: Int = 0
) {
    val isInLibrary: Boolean get() = localSong != null
    val canPlay: Boolean get() = localSong != null
    val canDownload: Boolean get() = localSong == null && downloadStatus == null
    val isDownloading: Boolean get() = downloadStatus == "queued" ||
            downloadStatus == "resolving" || downloadStatus == "downloading"
}

sealed class ImportScreenState {
    object Idle : ImportScreenState()
    data class Fetching(val message: String) : ImportScreenState()
    data class Loaded(val playlistName: String, val tracks: List<TrackUiState>) : ImportScreenState()
    data class Error(val message: String) : ImportScreenState()
}

sealed class ZipDownloadState {
    object Idle : ZipDownloadState()
    data class Downloading(val message: String) : ZipDownloadState()
    data class Done(val filePath: String) : ZipDownloadState()
    data class Error(val message: String) : ZipDownloadState()
}

/**
 * One-shot event for showing a Snackbar / Toast when a download finishes.
 */
data class DownloadEvent(
    val trackTitle: String,
    val artist: String,
    val success: Boolean,
    val filePath: String? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class ImportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val importUseCase: ImportSpotifyPlaylistUseCase,
    private val downloadRepository: DownloadRepository,
    private val songRepository: SongRepository,
    private val playlistRepository: PlaylistRepository,
    private val downloadQueueDao: DownloadQueueDao,
    private val songlinkApi: SonglinkApiService
) : ViewModel() {

    private val _state = MutableStateFlow<ImportScreenState>(ImportScreenState.Idle)
    val state: StateFlow<ImportScreenState> = _state.asStateFlow()

    private val _zipState = MutableStateFlow<ZipDownloadState>(ZipDownloadState.Idle)
    val zipState: StateFlow<ZipDownloadState> = _zipState.asStateFlow()

    /** One-shot event stream — UI collects this to show Snackbar notifications */
    private val _downloadEvents = MutableSharedFlow<DownloadEvent>(extraBufferCapacity = 8)
    val downloadEvents: SharedFlow<DownloadEvent> = _downloadEvents.asSharedFlow()

    /** Download save directory (exposed so UI can show it) */
    val downloadDir: String get() = File(context.filesDir, "Music").absolutePath

    private var currentPlaylistId: Long? = null
    private var currentPlaylistName: String = "Groove Playlist"
    private var rawTracks: List<Track> = emptyList()

    // ── CACHED status/progress flows ──────────────────────────────────────────
    // These maps ensure the same StateFlow instance is reused across
    // recompositions. The old approach created a NEW StateFlow on every call
    // to getDownloadStatusFlow() (via .stateIn()), causing the UI to flicker
    // endlessly between the initial value (null/0) and the real DB value.
    private val statusFlows   = ConcurrentHashMap<String, StateFlow<String?>>()
    private val progressFlows = ConcurrentHashMap<String, StateFlow<Int>>()

    init {
        // On launch: cancel any workers stuck from a previous session.
        // WorkManager persists "queued"/"running" state across app restarts —
        // without this, old stuck workers block new downloads.
        resetStuckDownloads()
        // Start observing all download statuses for completion events
        observeDownloadCompletions()
    }

    /**
     * Cancel all WorkManager workers and reset "stuck" DB entries to null (not downloaded).
     * This runs on every ViewModel creation (app launch) to prevent the persistent
     * "Queued..." state seen after app restart.
     */
    private fun resetStuckDownloads() {
        viewModelScope.launch {
            try {
                // Cancel all enqueued/running WorkManager work
                WorkManager.getInstance(context).cancelAllWork().result.get()
                Log.d(TAG, "Cancelled all pending WorkManager jobs")
            } catch (e: Exception) {
                Log.w(TAG, "WorkManager cancel failed: ${e.message}")
            }
            try {
                // Reset DB status for any items that were mid-download
                downloadQueueDao.resetStuckDownloads()
                Log.d(TAG, "Reset stuck download queue entries")
            } catch (e: Exception) {
                Log.w(TAG, "DB reset failed: ${e.message}")
            }
        }
    }

    /**
     * Observe the download queue table for status changes.
     * When a track transitions to "done" or "failed", emit a DownloadEvent
     * so the UI can show a notification Snackbar.
     */
    private fun observeDownloadCompletions() {
        viewModelScope.launch {
            downloadQueueDao.getAllFlow().collect { entries ->
                for (entry in entries) {
                    val prevStatus = statusFlows[entry.trackId]?.value
                    // Only fire event on transition TO done/failed
                    if (entry.status == "done" && prevStatus != null && prevStatus != "done") {
                        // Find the file path from the song entity
                        val song = songRepository.findByIsrc(entry.isrc ?: "")
                            ?: songRepository.findByTitleAndArtist(entry.title, entry.artist)
                        _downloadEvents.emit(
                            DownloadEvent(
                                trackTitle = entry.title,
                                artist = entry.artist,
                                success = true,
                                filePath = song?.filePath
                            )
                        )
                    } else if (entry.status == "failed" && prevStatus != null && prevStatus != "failed") {
                        _downloadEvents.emit(
                            DownloadEvent(
                                trackTitle = entry.title,
                                artist = entry.artist,
                                success = false,
                                errorMessage = "Download failed — tap to retry"
                            )
                        )
                    }
                }
            }
        }
    }

    fun importPlaylist(url: String) {
        viewModelScope.launch {
            importUseCase.invoke(url).collect { progress ->
                when (progress) {
                    is ImportProgress.Fetching -> _state.value = ImportScreenState.Fetching(progress.message)
                    is ImportProgress.Error    -> _state.value = ImportScreenState.Error(progress.message)
                    is ImportProgress.Success  -> {
                        rawTracks = progress.playlist.tracks
                        currentPlaylistName = progress.playlist.name
                        currentPlaylistId = playlistRepository.create(
                            name = progress.playlist.name,
                            emoji = "🎵",
                            isAutoGenerated = false
                        )
                        val trackUiStates = rawTracks.map { track ->
                            val localSong = matchTrackToLibrary(track)
                            TrackUiState(track = track, localSong = localSong)
                        }
                        _state.value = ImportScreenState.Loaded(
                            playlistName = progress.playlist.name,
                            tracks = trackUiStates
                        )
                    }
                    ImportProgress.Idle -> {}
                }
            }
        }
    }

    /** Try ISRC first, then title+artist fuzzy match */
    private suspend fun matchTrackToLibrary(track: Track): Song? {
        if (!track.isrc.isNullOrBlank()) {
            val byIsrc = songRepository.findByIsrc(track.isrc)
            if (byIsrc != null) return byIsrc
        }
        return songRepository.findByTitleAndArtist(track.title, track.artist)
    }

    /** Single-track download — goes through the batch manager too */
    fun downloadTrack(track: Track) {
        viewModelScope.launch {
            downloadRepository.enqueueDownload(track, currentPlaylistId)
            updateTrackDownloadStatus(track.spotifyId, "queued")
        }
    }

    // ── Batch download manager ───────────────────────────────────────────────
    // Only BATCH_SIZE workers run concurrently. The rest stay "queued" in the
    // DB and get started as slots free up.

    companion object {
        private const val BATCH_SIZE = 5
        private const val POLL_INTERVAL_MS = 2000L
    }

    /** Tracks waiting to have their workers started */
    private val pendingQueue = java.util.concurrent.ConcurrentLinkedQueue<Track>()
    private val batchRunning = MutableStateFlow(false)

    fun downloadAllMissing() {
        val loaded = _state.value as? ImportScreenState.Loaded ?: return
        val missing = loaded.tracks.filter {
            !it.isInLibrary && !it.isDownloading && it.downloadStatus != "done"
        }
        if (missing.isEmpty()) return

        viewModelScope.launch {
            // Step 1: Insert ALL into DB as "queued" so the UI shows them immediately
            missing.forEach { ts ->
                downloadRepository.queueOnly(ts.track, currentPlaylistId)
                updateTrackDownloadStatus(ts.track.spotifyId, "queued")
            }

            // Step 2: Add all to the pending queue
            missing.forEach { ts -> pendingQueue.add(ts.track) }

            // Step 3: Start the batch processor (if not already running)
            if (!batchRunning.value) {
                startBatchProcessor()
            }
        }
    }

    /**
     * Polls the download queue and keeps exactly BATCH_SIZE workers active.
     * When a worker finishes (done/failed), it starts the next queued track.
     */
    private fun startBatchProcessor() {
        if (batchRunning.value) return
        batchRunning.value = true

        viewModelScope.launch {
            Log.d(TAG, "Batch processor started — ${pendingQueue.size} tracks pending")

            while (pendingQueue.isNotEmpty()) {
                // Count currently active workers (resolving or downloading)
                val activeCount = downloadQueueDao.getActiveDownloads().size

                // Fill open slots
                val slotsAvailable = (BATCH_SIZE - activeCount).coerceAtLeast(0)
                if (slotsAvailable > 0) {
                    repeat(slotsAvailable) {
                        val track = pendingQueue.poll() ?: return@repeat
                        Log.d(TAG, "Starting worker for: ${track.title} (${pendingQueue.size} pending)")
                        downloadRepository.startWorker(track, currentPlaylistId)
                    }
                }

                // Wait before polling again
                kotlinx.coroutines.delay(POLL_INTERVAL_MS)
            }

            Log.d(TAG, "Batch processor done — all tracks processed")
            batchRunning.value = false
        }
    }

    /**
     * Download all missing tracks as a ZIP file via the server.
     * The server resolves each track on TIDAL, downloads audio, and returns a ZIP stream.
     * We save it to the Downloads folder.
     */
    fun downloadAllAsZip() {
        val loaded = _state.value as? ImportScreenState.Loaded ?: return
        val missing = loaded.tracks.filter { !it.isInLibrary && it.downloadStatus != "done" }
        if (missing.isEmpty()) {
            _zipState.value = ZipDownloadState.Error("All tracks are already in your library!")
            return
        }

        _zipState.value = ZipDownloadState.Downloading("Preparing ZIP of ${missing.size} tracks…")

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val request = ZipDownloadRequest(
                    tracks = missing.map { ts ->
                        ZipTrackRequest(
                            title  = ts.track.title,
                            artist = ts.track.artist,
                            isrc   = ts.track.isrc
                        )
                    },
                    playlistName = currentPlaylistName,
                    quality = "LOSSLESS"
                )

                Log.d(TAG, "Requesting ZIP for ${missing.size} tracks from server")
                val body = songlinkApi.downloadAsZip(request)

                // Save ZIP to internal Downloads dir
                val dlDir = File(context.filesDir, "Downloads")
                if (!dlDir.exists()) dlDir.mkdirs()
                val safePlaylistName = currentPlaylistName.replace(Regex("[/\\\\:*?\"<>|]"), "_").trim()
                val zipFile = File(dlDir, "$safePlaylistName.zip")

                body.byteStream().use { input ->
                    zipFile.outputStream().use { output ->
                        input.copyTo(output, bufferSize = 65_536)
                    }
                }

                Log.d(TAG, "ZIP saved: ${zipFile.absolutePath} (${zipFile.length() / 1024}KB)")
                withContext(Dispatchers.Main) {
                    _zipState.value = ZipDownloadState.Done(zipFile.absolutePath)
                }
            } catch (e: Exception) {
                Log.e(TAG, "ZIP download failed: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    _zipState.value = ZipDownloadState.Error(e.message ?: "ZIP download failed")
                }
            }
        }
    }

    fun dismissZipState() {
        _zipState.value = ZipDownloadState.Idle
    }

    /**
     * Get a CACHED StateFlow for download status. The same instance is reused
     * across recompositions — this prevents the flickering caused by creating
     * a new flow (and re-emitting the initial null value) every frame.
     */
    fun getDownloadStatusFlow(spotifyId: String): StateFlow<String?> =
        statusFlows.getOrPut(spotifyId) {
            downloadRepository.getDownloadStatus(spotifyId)
                .distinctUntilChanged()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
        }

    fun getDownloadProgressFlow(spotifyId: String): StateFlow<Int> =
        progressFlows.getOrPut(spotifyId) {
            downloadRepository.getDownloadProgress(spotifyId)
                .distinctUntilChanged()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
        }

    private fun updateTrackDownloadStatus(spotifyId: String, status: String) {
        val current = _state.value as? ImportScreenState.Loaded ?: return
        val updated = current.tracks.map { ts ->
            if (ts.track.spotifyId == spotifyId) ts.copy(downloadStatus = status) else ts
        }
        _state.value = current.copy(tracks = updated)
    }
}
