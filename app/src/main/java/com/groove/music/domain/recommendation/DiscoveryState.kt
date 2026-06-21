package com.groove.music.domain.recommendation

/**
 * Represents the state of the online discovery / "You May Also Like" feature.
 *
 * Separate from [com.groove.music.domain.model.HomeFeedState] — this is
 * player-level state (changes when the current song changes).
 */
sealed class DiscoveryState {
    /** No song playing — no recommendations requested yet. */
    object Idle : DiscoveryState()

    /** Fetching recommendations from the Smusic backend. */
    object Loading : DiscoveryState()

    /** Recommendations ready. */
    data class Ready(
        val tracks: List<com.groove.music.core.network.dto.RecommendedTrack>
    ) : DiscoveryState()

    /** Network unavailable or backend offline — fall back to local recs. */
    object Unavailable : DiscoveryState()

    /** Backend returned an error or timed out. */
    data class Error(val message: String) : DiscoveryState()
}
