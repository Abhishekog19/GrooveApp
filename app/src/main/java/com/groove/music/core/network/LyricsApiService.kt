package com.groove.music.core.network

import com.groove.music.core.network.dto.LyricsResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit interface for GET /api/lyrics
 *
 * Backend fetches lyrics from multiple sources (Genius, MusicBrainz, etc.)
 * and returns plain-text lyrics for the currently playing song.
 * Mirrors useLyrics.js on the web.
 */
interface LyricsApiService {

    /**
     * Fetch lyrics for a track.
     *
     * @param title  — track title
     * @param artist — artist name
     */
    @GET("/api/lyrics")
    suspend fun getLyrics(
        @Query("title")  title: String,
        @Query("artist") artist: String
    ): LyricsResponse
}
