package com.groove.music.core.network.dto

/**
 * DTO for GET /api/lyrics?title=...&artist=...
 * Mirrors the response consumed by useLyrics.js on the web.
 */
data class LyricsResponse(
    val lyrics: String? = null,
    val source: String? = null,
    val synced: Boolean = false
)
