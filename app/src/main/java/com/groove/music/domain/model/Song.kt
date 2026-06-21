package com.groove.music.domain.model

/**
 * Pure domain model — no Room or Android dependencies.
 * Generated from SongEntity via SongMapper.
 *
 * Mirrors the song object shape used across the Smusic web app
 * (store.js, folderScanner.js, Library.jsx).
 */
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val genre: String?,
    val year: Int?,
    val durationMs: Long,
    val durationFormatted: String,      // e.g. "3:45" — mirrors web duration field
    val sourceType: String,             // "mediastore"|"saf"|"upload"|"downloaded"
    val filePath: String?,              // content:// URI or absolute path
    val folderName: String?,
    val coverUri: String?,
    val isrc: String?,
    val spotifyId: String?,
    val isFavorite: Boolean,
    val isDownloaded: Boolean = false,  // true when local file exists in Music/Groove/
    val dateAdded: Long
)

fun Long.toFormattedDuration(): String {
    val totalSecs = this / 1000
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    return "$mins:${secs.toString().padStart(2, '0')}"
}
