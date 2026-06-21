package com.groove.music.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Caches lyrics for a song after they are fetched from the Smusic backend.
 *
 * Separated from SongEntity so the main songs table stays lean.
 * Offline-first: once cached, lyrics load without any network call.
 *
 * lyricsType values:
 *   "plain"       — raw text lyrics
 *   "synced"      — timestamped lines (syncedLinesJson filled)
 *   "unavailable" — fetched but none found (prevents repeated API calls)
 */
@Entity(
    tableName = "lyrics_cache",
    foreignKeys = [
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("songId")]
)
data class LyricsCacheEntity(
    @PrimaryKey
    val songId: Long,

    val lyricsType: String,             // "plain" | "synced" | "unavailable"
    val lyricsText: String?,
    val syncedLinesJson: String?,       // JSON array: [{timeMs: 0, text: "..."}, ...]
    val provider: String?,              // "smusic-backend" | "embedded"
    val isRtl: Boolean = false,
    val isOfflineAvailable: Boolean = false,
    val cachedAt: Long = System.currentTimeMillis()
)
