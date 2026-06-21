package com.groove.music.core.network

import com.groove.music.core.network.dto.RecommendationsResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit interface for GET /api/recommendations
 *
 * Backend fires all TIDAL proxy mirrors in parallel (Promise.any race) and returns
 * "You May Also Like" tracks for the currently playing song.
 * Mirrors the fetch in useRecommendations.js on the web.
 */
interface RecommendationsApiService {

    /**
     * Fetch similar tracks for a given song.
     *
     * @param title   — track title (will be normalized server-side)
     * @param artist  — primary artist name (first artist only, comma-split on client)
     * @param limit   — max results to return (default 8)
     */
    @GET("/api/recommendations")
    suspend fun getRecommendations(
        @Query("title")  title: String,
        @Query("artist") artist: String,
        @Query("limit")  limit: Int = 8
    ): RecommendationsResponse
}
