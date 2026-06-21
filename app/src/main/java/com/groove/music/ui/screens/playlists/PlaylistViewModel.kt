package com.groove.music.ui.screens.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.groove.music.data.repository.PlaylistRepository
import com.groove.music.domain.model.Playlist
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlaylistUiState(
    val playlists: List<Playlist> = emptyList()
)

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlaylistUiState())
    val uiState: StateFlow<PlaylistUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            playlistRepository.allPlaylists.collect { playlists ->
                _uiState.update { it.copy(playlists = playlists) }
            }
        }
    }

    fun createPlaylist(name: String, emoji: String) {
        viewModelScope.launch {
            playlistRepository.create(name = name, emoji = emoji)
        }
    }

    fun renamePlaylist(id: Long, newName: String) {
        viewModelScope.launch { playlistRepository.rename(id, newName) }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch { playlistRepository.delete(id) }
    }

    fun addSong(playlistId: Long, songId: Long) {
        viewModelScope.launch { playlistRepository.addSong(playlistId, songId) }
    }

    fun removeSong(playlistId: Long, songId: Long) {
        viewModelScope.launch { playlistRepository.removeSong(playlistId, songId) }
    }
}
