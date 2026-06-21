package com.groove.music.ui.screens.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.groove.music.data.repository.PlaylistRepository
import com.groove.music.data.repository.SongRepository
import com.groove.music.data.scanner.MediaStoreScannerImpl
import com.groove.music.data.scanner.SafFolderScannerImpl
import com.groove.music.domain.model.Song
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryUiState(
    val songs: List<Song> = emptyList(),
    val genres: List<String> = emptyList(),
    val searchQuery: String = "",
    val selectedGenre: String = "all",
    val isScanning: Boolean = false,
    val scanProgress: Int = 0,
    val scanMessage: String = "",
    val toast: ToastMessage? = null
)

data class ToastMessage(val message: String, val type: ToastType = ToastType.SUCCESS)

enum class ToastType { SUCCESS, ERROR, INFO }

/**
 * Library ViewModel — Android equivalent of useLibraryStore.
 *
 * Manages:
 *  - Reactive song list (filtered by search + genre — mirrors filterGenre / searchQuery)
 *  - MediaStore auto-scan on first launch
 *  - SAF folder scan on user action
 *  - Re-sync (mirrors syncFolderSongs)
 *  - Song deletion (mirrors removeSong)
 *  - Genre list for filter chips
 */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val songRepository: SongRepository,
    private val playlistRepository: PlaylistRepository,
    private val mediaStoreScanner: MediaStoreScannerImpl,
    private val safScanner: SafFolderScannerImpl
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    // Reactive filtered song list
    val filteredSongs: Flow<List<Song>> = _uiState
        .flatMapLatest { state ->
            songRepository.search(state.searchQuery, state.selectedGenre)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Observe genres for filter chips
        viewModelScope.launch {
            songRepository.genres.collect { genres ->
                _uiState.update { it.copy(genres = genres) }
            }
        }
    }

    // ── Search / Filter — mirrors setSearchQuery / setFilterGenre in web ──────

    fun setSearchQuery(query: String) = _uiState.update { it.copy(searchQuery = query) }

    fun setSelectedGenre(genre: String) = _uiState.update { it.copy(selectedGenre = genre) }

    // ── MediaStore scan (auto on first launch, manual on "Re-scan") ───────────

    fun scanMediaStore() {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, scanProgress = 0, scanMessage = "Scanning device music…") }
            try {
                val songs = mediaStoreScanner.scan { count ->
                    _uiState.update { it.copy(scanProgress = count) }
                }
                val inserted = songRepository.insertAll(songs)
                val addedCount = inserted.count { it != -1L }

                // Auto-create folder playlists (mirrors syncFolderSongs logic)
                songs.groupBy { it.folderName ?: "Music" }.forEach { (folder, folderSongs) ->
                    val ids = folderSongs.mapNotNull { s ->
                        songRepository.findByFilePath(s.filePath ?: return@mapNotNull null)?.id
                            ?: inserted.getOrNull(songs.indexOf(s))?.takeIf { it != -1L }
                    }
                    if (ids.isNotEmpty()) playlistRepository.upsertFolderPlaylist(folder, ids)
                }

                // Cache artwork for newly inserted songs in the background
                // (does not block UI — toast fires immediately below)
                if (addedCount > 0) {
                    val artworkMap = songs.zip(inserted)
                        .filter { (_, id) -> id != -1L }
                        .mapNotNull { (song, id) ->
                            val path = song.filePath ?: return@mapNotNull null
                            path to id
                        }
                        .toMap()
                    launch { mediaStoreScanner.cacheArtworkForInserted(artworkMap) }
                }

                val msg = if (addedCount == 0) "Library is up to date ✓"
                          else "+$addedCount songs added"
                showToast(msg, ToastType.SUCCESS)
            } catch (e: Exception) {
                showToast("Scan failed: ${e.message}", ToastType.ERROR)
            } finally {
                _uiState.update { it.copy(isScanning = false, scanProgress = 0) }
            }
        }
    }

    // ── SAF folder scan (mirrors scanMusicFolder + upsert auto-playlists) ────

    fun scanSafFolder(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, scanProgress = 0, scanMessage = "Scanning folder…") }
            try {
                val songs = safScanner.scan(uri) { count, file ->
                    _uiState.update { it.copy(scanProgress = count, scanMessage = "Scanned: $file") }
                }

                val inserted = songRepository.insertAll(songs)
                val addedCount = inserted.count { it != -1L }

                songs.groupBy { it.folderName ?: "Scanned Music" }.forEach { (folder, folderSongs) ->
                    val ids = folderSongs.mapNotNull { s ->
                        songRepository.findByFilePath(s.filePath ?: return@mapNotNull null)?.id
                            ?: inserted.getOrNull(songs.indexOf(s))?.takeIf { it != -1L }
                    }
                    if (ids.isNotEmpty()) playlistRepository.upsertFolderPlaylist(folder, ids)
                }

                // Cache artwork for newly inserted songs in the background
                if (addedCount > 0) {
                    val artworkMap = songs.zip(inserted)
                        .filter { (_, id) -> id != -1L }
                        .mapNotNull { (song, id) ->
                            val path = song.filePath ?: return@mapNotNull null
                            path to id
                        }
                        .toMap()
                    launch { safScanner.cacheArtworkForInserted(artworkMap) }
                }

                val msg = if (addedCount == 0) "No new songs found"
                          else "+$addedCount songs added from folder"
                showToast(msg, ToastType.SUCCESS)
            } catch (e: Exception) {
                showToast("Scan failed: ${e.message}", ToastType.ERROR)
            } finally {
                _uiState.update { it.copy(isScanning = false, scanProgress = 0) }
            }
        }
    }

    // ── Delete song — mirrors removeSong in useLibraryStore ──────────────────

    fun deleteSong(song: Song) {
        viewModelScope.launch {
            try {
                playlistRepository.removeSongFromAll(song.id)
                songRepository.deleteById(song.id)
            } catch (e: Exception) {
                showToast("Failed to delete song", ToastType.ERROR)
            }
        }
    }

    // ── Toast helper ──────────────────────────────────────────────────────────

    private fun showToast(message: String, type: ToastType = ToastType.SUCCESS) {
        _uiState.update { it.copy(toast = ToastMessage(message, type)) }
        viewModelScope.launch {
            kotlinx.coroutines.delay(3500)
            _uiState.update { it.copy(toast = null) }
        }
    }

    fun clearToast() = _uiState.update { it.copy(toast = null) }
}
