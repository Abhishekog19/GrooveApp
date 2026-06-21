package com.groove.music.core.network

import com.groove.music.core.network.dto.TidalResolveResponse
import com.groove.music.core.network.dto.TidalSearchResponse
import com.groove.music.core.network.dto.ZipDownloadRequest
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Streaming
import retrofit2.http.Url

interface SonglinkApiService {

    /**
     * Search TIDAL for tracks by query string.
     * Calls GET /api/tidal-download/search?q=...&limit=...
     */
    @GET("/api/tidal-download/search")
    suspend fun searchTracks(
        @Query("q") query: String,
        @Query("limit") limit: Int = 20
    ): TidalSearchResponse
    /**
     * Resolves a Spotify track to a TIDAL direct stream URL via your server.
     * Calls GET /api/tidal-download/resolve?title=...&artist=...&isrc=...&quality=LOSSLESS
     */
    @GET("/api/tidal-download/resolve")
    suspend fun resolve(
        @Query("title") title: String,
        @Query("artist") artist: String,
        @Query("isrc") isrc: String? = null,
        @Query("quality") quality: String = "LOSSLESS"
    ): TidalResolveResponse

    /**
     * Proxied audio stream — server fetches the TIDAL CDN bytes and pipes them back.
     * Calls GET /api/tidal-download/stream?url=<encoded stream URL>
     */
    @Streaming
    @GET("/api/tidal-download/stream")
    suspend fun streamDownloadViaProxy(
        @Query("url") streamUrl: String
    ): ResponseBody

    /**
     * Bulk ZIP download — server downloads all tracks and returns a ZIP file.
     * Calls POST /api/tidal-download/zip
     */
    @Streaming
    @POST("/api/tidal-download/zip")
    suspend fun downloadAsZip(
        @Body request: ZipDownloadRequest
    ): ResponseBody

    /** Direct stream (for non-proxied URLs if needed) */
    @Streaming
    @GET
    suspend fun streamDownload(@Url url: String): ResponseBody
}
