package com.groove.music.domain.usecase

import android.util.Log
import com.groove.music.core.network.dto.RecommendedTrack
import com.groove.music.domain.recommendation.LocalRecommendationEngine
import com.groove.music.domain.recommendation.RecommendationRepository
import javax.inject.Inject

private const val TAG = "GetDiscoveryUseCase"

/**
 * Fetches online "You May Also Like" recommendations for the currently playing song.
 *
 * Strategy:
 *  1. Try [RecommendationRepository.getDiscoveryRecommendations] (cached, 30-min TTL)
 *  2. On any failure (network, timeout, empty result), fall back to
 *     [LocalRecommendationEngine.getRecommendedForYou] converted to [RecommendedTrack]
 *
 * This makes the feature resilient — it never shows nothing.
 */
class GetDiscoveryRecommendationsUseCase @Inject constructor(
    private val onlineRepo: RecommendationRepository,
    private val localEngine: LocalRecommendationEngine
) {
    suspend operator fun invoke(title: String, artist: String, limit: Int = 8): List<RecommendedTrack> {
        return try {
            val result = onlineRepo.getDiscoveryRecommendations(title, artist, limit)
            if (result.isNotEmpty()) {
                result
            } else {
                Log.d(TAG, "Online returned empty — falling back to local")
                localFallback(limit)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Online discovery failed (${e.message}) — falling back to local")
            localFallback(limit)
        }
    }

    private suspend fun localFallback(limit: Int): List<RecommendedTrack> =
        localEngine.getRecommendedForYou(limit).map { song ->
            RecommendedTrack(
                id         = song.id,
                title      = song.title,
                artist     = song.artist,
                album      = song.album,
                albumArt   = song.coverUri,
                durationMs = song.durationMs
            )
        }
}
