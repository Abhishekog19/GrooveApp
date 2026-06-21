package com.groove.music.data.recommendation

import android.util.Log
import com.groove.music.core.network.RecommendationsApiService
import com.groove.music.core.network.dto.RecommendedTrack
import com.groove.music.domain.recommendation.RecommendationRepository
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG             = "RecommendationRepoImpl"
private const val CACHE_TTL_MS    = 30L * 60 * 1000   // 30 minutes
private const val TIMEOUT_MS      = 20_000L            // 20-second hard timeout

/**
 * Online discovery implementation.
 *
 * - Hits the existing [RecommendationsApiService] → GET /api/recommendations
 * - Caches results in-memory for 30 minutes using (title+artist) as key
 * - Enforces a 20-second timeout; returns empty list on timeout so caller can fall back
 */
@Singleton
class RecommendationRepositoryImpl @Inject constructor(
    private val apiService: RecommendationsApiService
) : RecommendationRepository {

    private data class CacheEntry(
        val tracks: List<RecommendedTrack>,
        val cachedAt: Long
    )

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    override suspend fun getDiscoveryRecommendations(
        title: String,
        artist: String,
        limit: Int
    ): List<RecommendedTrack> {
        val cacheKey = "${title.lowercase()}|${artist.lowercase()}"

        // Return cached result if still fresh
        cache[cacheKey]?.let { entry ->
            if (System.currentTimeMillis() - entry.cachedAt < CACHE_TTL_MS) {
                Log.d(TAG, "Cache hit for \"$title\" by $artist")
                return entry.tracks
            } else {
                cache.remove(cacheKey)
            }
        }

        // Fetch from backend with a hard timeout
        val result = withTimeoutOrNull(TIMEOUT_MS) {
            try {
                val response = apiService.getRecommendations(
                    title  = title,
                    artist = artist.split(",").first().trim(),   // primary artist only
                    limit  = limit
                )
                response.tracks.also {
                    Log.d(TAG, "Fetched ${it.size} discovery tracks for \"$title\"")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Discovery API error for \"$title\": ${e.message}")
                throw e
            }
        }

        if (result == null) {
            Log.w(TAG, "Discovery request timed out for \"$title\" — returning empty")
            return emptyList()
        }

        // Store in cache
        cache[cacheKey] = CacheEntry(tracks = result, cachedAt = System.currentTimeMillis())
        return result
    }
}
