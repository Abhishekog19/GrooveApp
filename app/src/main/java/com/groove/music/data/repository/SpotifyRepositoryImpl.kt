package com.groove.music.data.repository

import com.groove.music.core.network.SpotifyApiService
import com.groove.music.core.network.dto.SpotifyPlaylistRequest
import com.groove.music.domain.model.ImportPlaylist
import com.groove.music.domain.model.Track
import com.groove.music.domain.repository.SpotifyRepository
import javax.inject.Inject

class SpotifyRepositoryImpl @Inject constructor(
    private val spotifyApiService: SpotifyApiService
) : SpotifyRepository {
    override suspend fun fetchPlaylist(url: String): Result<ImportPlaylist> {
        return try {
            val response = spotifyApiService.fetchPlaylist(SpotifyPlaylistRequest(url))
            val tracks = response.tracks.map {
                Track(
                    spotifyId = it.spotifyId,
                    spotifyUrl = it.spotifyUrl,
                    title = it.title,
                    artist = it.artist,
                    album = it.album,
                    albumArt = it.albumArt,
                    isrc = it.isrc,
                    durationMs = it.durationMs
                )
            }
            Result.success(ImportPlaylist(response.playlistName, tracks))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
