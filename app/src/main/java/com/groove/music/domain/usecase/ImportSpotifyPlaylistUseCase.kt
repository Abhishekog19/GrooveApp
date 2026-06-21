package com.groove.music.domain.usecase

import com.groove.music.domain.model.ImportPlaylist
import com.groove.music.domain.repository.SpotifyRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

sealed class ImportProgress {
    object Idle : ImportProgress()
    data class Fetching(val message: String) : ImportProgress()
    data class Success(val playlist: ImportPlaylist) : ImportProgress()
    data class Error(val message: String) : ImportProgress()
}

class ImportSpotifyPlaylistUseCase @Inject constructor(
    private val spotifyRepository: SpotifyRepository
) {
    fun invoke(url: String): Flow<ImportProgress> = flow {
        emit(ImportProgress.Fetching("Fetching playlist from Spotify..."))
        
        val result = spotifyRepository.fetchPlaylist(url)
        if (result.isSuccess) {
            val playlist = result.getOrThrow()
            emit(ImportProgress.Success(playlist))
        } else {
            val ex = result.exceptionOrNull()
            emit(ImportProgress.Error(ex?.message ?: "Unknown error occurred"))
        }
    }
}
