package com.groove.music.domain.model

data class Track(
    val spotifyId: String,
    val spotifyUrl: String,   // full https://open.spotify.com/track/... URL
    val title: String,
    val artist: String,
    val album: String,
    val albumArt: String?,
    val isrc: String?,
    val durationMs: Long = 0
)

data class ImportPlaylist(
    val name: String,
    val tracks: List<Track>
)
