package com.groove.music.core.network

import com.groove.music.core.network.dto.SpotifyPlaylistRequest
import com.groove.music.core.network.dto.SpotifyPlaylistResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface SpotifyApiService {
    @POST("/api/spotify-playlist")
    suspend fun fetchPlaylist(
        @Body request: SpotifyPlaylistRequest
    ): SpotifyPlaylistResponse
}
