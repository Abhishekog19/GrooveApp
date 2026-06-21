package com.groove.music.core.network.dto

// ── Spotify Playlist ─────────────────────────────────────────────────────────

data class SpotifyPlaylistRequest(val playlistUrl: String)

data class SpotifyPlaylistResponse(
    val playlistName: String,
    val tracks: List<SpotifyTrackDto>,
    val totalTracks: Int
)

data class SpotifyTrackDto(
    val spotifyId: String,
    val spotifyUrl: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumArt: String?,
    val isrc: String?,
    val durationMs: Long = 0
)

// ── /api/tidal-download/resolve response ─────────────────────────────────────

/**
 * Response from GET /api/tidal-download/resolve
 * Server finds the track on TIDAL and returns a direct CDN stream URL.
 */
data class TidalResolveResponse(
    val streamUrl: String,          // Direct audio CDN URL (FLAC or M4A)
    val tidalTrackId: Long?,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val format: String,             // "flac" or "m4a"
    val quality: String             // "LOSSLESS", "HI_RES_LOSSLESS", "HIGH", etc.
)

// ── /api/tidal-download/zip request ──────────────────────────────────────────

data class ZipDownloadRequest(
    val tracks: List<ZipTrackRequest>,
    val playlistName: String,
    val quality: String = "LOSSLESS"
)

data class ZipTrackRequest(
    val title: String,
    val artist: String,
    val isrc: String?
)

// ── /api/tidal-download/search response ──────────────────────────────────────

data class TidalSearchResponse(
    val results: List<TidalSearchTrack>
)

data class TidalSearchTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String?,
    val albumArt: String?,
    val durationMs: Long = 0,
    val isrc: String? = null
)

