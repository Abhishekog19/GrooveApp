package com.groove.music.ui.screens.player

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.groove.music.core.datastore.SavedSession
import com.groove.music.core.datastore.SessionDataStore
import com.groove.music.core.network.dto.RecommendedTrack
import com.groove.music.data.repository.PlayHistoryRepository
import com.groove.music.data.repository.SongRepository
import com.groove.music.domain.model.PlayerState
import com.groove.music.domain.model.RepeatMode
import com.groove.music.domain.model.Song
import com.groove.music.domain.model.toFormattedDuration
import com.groove.music.domain.recommendation.DiscoveryState
import com.groove.music.domain.usecase.GetDiscoveryRecommendationsUseCase
import com.groove.music.player.QueueManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Player ViewModel — the Android equivalent of usePlayerStore in store.js.
 *
 * Manages:
 *   - All playback state (currentSong, queue, isPlaying, position, repeat, shuffle)
 *   - MediaController bridge via QueueManager
 *   - Session save / restore (mirrors restoreSession + saveSession in web)
 *   - Queue operations (mirrors enqueueNext, addToQueue, removeFromQueue)
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val queueManager: QueueManager,
    private val songRepository: SongRepository,
    private val sessionDataStore: SessionDataStore,
    private val playHistoryRepository: PlayHistoryRepository,
    private val getDiscoveryRecommendations: GetDiscoveryRecommendationsUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    // ── Discovery ("You May Also Like") state ─────────────────────────────────
    private val _discoveryState = MutableStateFlow<DiscoveryState>(DiscoveryState.Idle)
    val discoveryState: StateFlow<DiscoveryState> = _discoveryState.asStateFlow()
    private var discoveryJob: Job? = null

    // ── Play-tracking state ───────────────────────────────────────────────────
    // Tracks the PREVIOUS song so we can record its session when a new one starts.
    private var trackingStartMs: Long = 0L      // wall-clock ms when current song started
    private var trackedSong: Song? = null        // song being currently tracked

    init {
        connectToService()
        restoreSession()
        startPositionPolling()
    }

    // ── Service connection ────────────────────────────────────────────────────

    private fun connectToService() {
        queueManager.connect { controller ->
            controller.addListener(object : Player.Listener {

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _state.update { it.copy(isPlaying = isPlaying) }
                    saveSessionIfPlaying()
                }

                override fun onMediaItemTransition(
                    mediaItem: androidx.media3.common.MediaItem?,
                    reason: Int
                ) {
                    val controller = queueManager.controller ?: return
                    val queue = (0 until controller.mediaItemCount)
                        .mapNotNull { i ->
                            val item = controller.getMediaItemAt(i)
                            _state.value.queue.find { it.id.toString() == item.mediaId }
                        }
                    val currentId   = mediaItem?.mediaId?.toLongOrNull()
                    val currentSong = queue.find { it.id == currentId }

                    // ── Record play for the OUTGOING song ─────────────────────
                    // A song is considered "skipped" if it was played < 30%.
                    val prevSong = trackedSong
                    val startMs  = trackingStartMs
                    if (prevSong != null && prevSong.id > 0L) {
                        val playedMs  = System.currentTimeMillis() - startMs
                        val duration  = prevSong.durationMs.coerceAtLeast(1L)
                        val skipped   = playedMs.toFloat() / duration < 0.30f
                        viewModelScope.launch {
                            playHistoryRepository.recordPlay(
                                songId       = prevSong.id,
                                totalPlayedMs = playedMs,
                                durationMs    = duration,
                                wasSkipped    = skipped
                            )
                        }
                    }

                    // ── Start tracking the INCOMING song ──────────────────────
                    trackedSong    = currentSong
                    trackingStartMs = System.currentTimeMillis()

                    _state.update { it.copy(
                        currentSong  = currentSong,
                        currentIndex = queue.indexOfFirst { it.id == currentId },
                        queue        = queue,
                        durationMs   = controller.duration.coerceAtLeast(0L)
                    ) }
                    saveSessionIfPlaying()

                    // ── Fetch discovery recommendations for new song ───────────
                    if (currentSong != null) fetchDiscovery(currentSong)
                }

                override fun onRepeatModeChanged(repeatMode: Int) {
                    _state.update { it.copy(
                        repeat = when (repeatMode) {
                            Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                            Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                            else                   -> RepeatMode.NONE
                        }
                    ) }
                }

                override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                    _state.update { it.copy(shuffle = shuffleModeEnabled) }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        _state.update { it.copy(
                            durationMs = queueManager.controller?.duration?.coerceAtLeast(0) ?: 0L
                        ) }
                    }
                }
            })
        }
    }

    // ── Position polling (ExoPlayer doesn't push position events at 50ms) ────

    private fun startPositionPolling() {
        viewModelScope.launch {
            while (isActive) {
                val ctrl = queueManager.controller
                if (ctrl != null && ctrl.isPlaying) {
                    _state.update { it.copy(currentPositionMs = ctrl.currentPosition) }
                }
                delay(200L) // poll every 200ms for a smooth seek bar
            }
        }
    }

    // ── Session restore — mirrors restoreSession(session, songs) in store.js ─

    private fun restoreSession() {
        viewModelScope.launch {
            val session = sessionDataStore.sessionFlow.firstOrNull { it != null } ?: return@launch
            restoreFromSession(session)
        }
    }

    private suspend fun restoreFromSession(session: SavedSession) {
        val songs = songRepository.getByIds(session.queueIds)
        if (songs.isEmpty()) return

        val currentSong = songs.find { it.id == session.songId } ?: return
        val queue = session.queueIds.mapNotNull { id -> songs.find { it.id == id } }

        // Set up state without auto-playing (user presses Play)
        _state.update { it.copy(
            currentSong = currentSong,
            queue       = queue,
            currentIndex = queue.indexOfFirst { it.id == currentSong.id },
            isPlaying   = false,
            volume      = session.volume,
            repeat      = RepeatMode.valueOf(session.repeat),
            shuffle     = session.shuffle
        ) }

        // Load into ExoPlayer at the right position (but don't play)
        queueManager.controller?.let { ctrl ->
            val mediaItems: List<androidx.media3.common.MediaItem> =
                queue.map { song: Song ->
                    androidx.media3.common.MediaItem.Builder()
                        .setMediaId(song.id.toString())
                        .setUri(song.filePath ?: "")
                        .build()
                }

            val startIndex = queue.indexOfFirst { it.id == currentSong.id }.coerceAtLeast(0)
            ctrl.setMediaItems(mediaItems, startIndex, session.currentTimeMs)
            ctrl.prepare()
        }
    }

    // ── Public actions (mirrors each Zustand action in usePlayerStore) ────────

    /** Mirrors playSong(song) / playWithQueue(song, queue) */
    fun playSong(song: Song, queue: List<Song> = emptyList()) {
        // When no queue is provided, play this specific song solo (not from stale session queue).
        // Only reuse the existing queue if the song is actually IN that queue.
        val existingQueue = _state.value.queue
        val playQueue = when {
            queue.isNotEmpty()                          -> queue
            existingQueue.any { it.id == song.id }      -> existingQueue
            else                                        -> listOf(song)
        }
        _state.update { it.copy(
            currentSong  = song,
            queue        = playQueue,
            currentIndex = playQueue.indexOfFirst { it.id == song.id }.coerceAtLeast(0),
            isPlaying    = true
        ) }
        // Start tracking this song's play session
        trackedSong     = song
        trackingStartMs = System.currentTimeMillis()
        queueManager.play(song, playQueue)
    }

    /**
     * Play a TIDAL stream URL directly (for search results not yet in library).
     * Creates a temporary Song-like MediaItem and feeds it to ExoPlayer.
     */
    fun playStreamUrl(
        streamUrl: String,
        title: String,
        artist: String,
        album: String?,
        albumArt: String?,
        durationMs: Long
    ) {
        // Create a temporary Song for state display
        val tempSong = Song(
            id = -System.currentTimeMillis(),  // negative ID to distinguish from real library songs
            title = title,
            artist = artist,
            album = album ?: "",
            genre = null,
            year = null,
            durationMs = durationMs,
            durationFormatted = durationMs.toFormattedDuration(),
            sourceType = "stream",
            filePath = streamUrl,
            folderName = null,
            coverUri = albumArt,
            isrc = null,
            spotifyId = null,
            isFavorite = false,
            dateAdded = System.currentTimeMillis()
        )

        _state.update { it.copy(
            currentSong  = tempSong,
            queue        = listOf(tempSong),
            currentIndex = 0,
            isPlaying    = true
        ) }

        // Build MediaItem with stream URL
        val ctrl = queueManager.controller
        if (ctrl != null) {
            val mediaItem = MediaItem.Builder()
                .setMediaId(tempSong.id.toString())
                .setUri(Uri.parse(streamUrl))
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(title)
                        .setArtist(artist)
                        .setAlbumTitle(album)
                        .setArtworkUri(albumArt?.let { Uri.parse(it) })
                        .build()
                )
                .build()
            ctrl.setMediaItems(listOf(mediaItem), 0, 0L)
            ctrl.prepare()
            ctrl.play()
            Log.d("PlayerViewModel", "Streaming: \"$title\" from ${streamUrl.take(60)}…")
        } else {
            // Buffer it — QueueManager will replay when controller connects
            queueManager.play(tempSong, listOf(tempSong))
        }
    }

    /** Mirrors togglePlay() */
    fun togglePlay() {
        val ctrl = queueManager.controller ?: return
        if (ctrl.isPlaying) ctrl.pause() else ctrl.play()
    }

    /** Mirrors nextSong() */
    fun nextSong() = queueManager.controller?.seekToNextMediaItem()

    /** Mirrors previousSong() — if > 3s in, restart track */
    fun previousSong() {
        val ctrl = queueManager.controller ?: return
        if (ctrl.currentPosition > 3000L) {
            ctrl.seekTo(0L)
        } else {
            ctrl.seekToPreviousMediaItem()
        }
    }

    fun seekTo(positionMs: Long) = queueManager.seekTo(positionMs)

    fun setVolume(volume: Float) {
        queueManager.setVolume(volume)
        _state.update { it.copy(volume = volume) }
    }

    /** Mirrors toggleRepeat() — cycles NONE → ONE → ALL */
    fun toggleRepeat() {
        val next = when (_state.value.repeat) {
            RepeatMode.NONE -> RepeatMode.ONE
            RepeatMode.ONE  -> RepeatMode.ALL
            RepeatMode.ALL  -> RepeatMode.NONE
        }
        _state.update { it.copy(repeat = next) }
        queueManager.setRepeatMode(next)
    }

    /** Mirrors toggleShuffle() */
    fun toggleShuffle() {
        val newShuffle = !_state.value.shuffle
        _state.update { it.copy(shuffle = newShuffle) }
        queueManager.setShuffleEnabled(newShuffle)
    }

    /** Mirrors addToQueue(song) */
    fun addToQueue(song: Song) {
        if (_state.value.queue.any { it.id == song.id }) return
        _state.update { it.copy(queue = it.queue + song) }
        queueManager.addToQueue(song)
    }

    /** Mirrors enqueueNext(song) */
    fun enqueueNext(song: Song) {
        val state = _state.value
        if (state.queue.any { it.id == song.id }) return
        val insertAt = (state.currentIndex + 1).coerceAtMost(state.queue.size)
        val newQueue = state.queue.toMutableList().apply { add(insertAt, song) }
        _state.update { it.copy(queue = newQueue) }
        queueManager.enqueueNext(song)
    }

    /** Mirrors removeFromQueue(songId) */
    fun removeFromQueue(song: Song) {
        _state.update { it.copy(queue = it.queue.filter { q -> q.id != song.id }) }
        queueManager.removeFromQueue(song)
    }

    /** Mirrors toggleLike — delegates to Room */
    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            songRepository.setFavorite(song.id, !song.isFavorite)
        }
    }

    // ── Discovery recommendations ──────────────────────────────────────────────

    private fun fetchDiscovery(song: Song) {
        discoveryJob?.cancel()
        discoveryJob = viewModelScope.launch {
            _discoveryState.value = DiscoveryState.Loading
            try {
                val tracks = getDiscoveryRecommendations(song.title, song.artist)
                _discoveryState.value = if (tracks.isEmpty()) DiscoveryState.Unavailable
                                        else DiscoveryState.Ready(tracks)
            } catch (e: Exception) {
                Log.e("PlayerViewModel", "Discovery failed: ${e.message}")
                _discoveryState.value = DiscoveryState.Error(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Play a discovered track from the "You May Also Like" list.
     * If the track is in the local library, it plays normally.
     * Otherwise, the discovery track is not playable from here (requires import/download).
     * For now, we enqueue it to the queue so the UI can reflect what's next.
     */
    fun playDiscoveryTrack(track: RecommendedTrack) {
        viewModelScope.launch {
            // Try to find this track in the local library first (by ISRC or title+artist)
            val localSong = when {
                !track.isrc.isNullOrBlank() -> songRepository.findByIsrc(track.isrc!!)
                else -> songRepository.findByTitleAndArtist(track.title, track.artist)
            }
            if (localSong != null) {
                playSong(localSong)
            } else {
                Log.d("PlayerViewModel", "Discovery track not in library: ${track.title}")
                // Not in library — UI should prompt user to import or download
            }
        }
    }

    // ── Session saving ─────────────────────────────────────────────────────────

    private fun saveSessionIfPlaying() {
        val s = _state.value
        val song = s.currentSong ?: return
        viewModelScope.launch {
            sessionDataStore.saveSession(
                songId        = song.id,
                queueIds      = s.queue.map { it.id },
                currentTimeMs = queueManager.controller?.currentPosition ?: 0L,
                volume        = s.volume,
                repeat        = s.repeat.name,
                shuffle       = s.shuffle
            )
        }
    }

    override fun onCleared() {
        // Record play session for whatever was playing when the ViewModel dies
        val song    = trackedSong
        val startMs = trackingStartMs
        if (song != null && song.id > 0L) {
            val playedMs = System.currentTimeMillis() - startMs
            val duration = song.durationMs.coerceAtLeast(1L)
            viewModelScope.launch {
                playHistoryRepository.recordPlay(
                    songId        = song.id,
                    totalPlayedMs = playedMs,
                    durationMs    = duration,
                    wasSkipped    = playedMs.toFloat() / duration < 0.30f
                )
            }
        }
        queueManager.release()
        super.onCleared()
    }
}

// Helper to convert domain Song → MediaItem in this ViewModel
