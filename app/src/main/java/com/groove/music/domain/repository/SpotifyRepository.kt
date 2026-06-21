package com.groove.music.domain.repository

import com.groove.music.domain.model.ImportPlaylist

interface SpotifyRepository {
    suspend fun fetchPlaylist(url: String): Result<ImportPlaylist>
}
