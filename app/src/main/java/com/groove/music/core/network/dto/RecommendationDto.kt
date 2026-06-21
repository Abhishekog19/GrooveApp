package com.groove.music.core.network.dto

/**
 * DTOs for GET /api/recommendations
 * Mirrors the response shape consumed by useRecommendations.js on the web.
 */
data class RecommendationsResponse(
    val tracks: List<RecommendedTrack> = emptyList()
)

data class RecommendedTrack(
    val id: Long? = null,
    val title: String = "",
    val artist: String = "",
    val album: String? = null,
    val albumArt: String? = null,
    val durationMs: Long = 0,
    val isrc: String? = null,
    val tidalId: Long? = null
)
