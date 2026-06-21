package com.groove.music.domain.recommendation

import com.groove.music.core.network.dto.RecommendedTrack

/**
 * Repository interface for online "You May Also Like" track discovery.
 *
 * Implementations must cache results for 30 minutes (same key = title+artist).
 * Callers should handle network failures and fall back to LocalRecommendationEngine.
 */
interface RecommendationRepository {
    /**
     * Returns similar tracks for the given song.
     *
     * @param title  Track title (normalized server-side)
     * @param artist Primary artist name
     * @param limit  Max number of similar tracks to return (default 8)
     * @throws Exception if the backend is unreachable or returns an error
     */
    suspend fun getDiscoveryRecommendations(
        title: String,
        artist: String,
        limit: Int = 8
    ): List<RecommendedTrack>
}
