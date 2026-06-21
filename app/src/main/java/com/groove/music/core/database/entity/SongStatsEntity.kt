package com.groove.music.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Aggregated playback statistics per song.
 *
 * Updated every time a play event is recorded in play_history.
 * One row per song (upserted). Provides fast lookups for:
 *   - Most played songs
 *   - Recently played
 *   - Skip rate (used by recommendation engine)
 */
@Entity(
    tableName = "song_stats",
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
data class SongStatsEntity(
    @PrimaryKey
    val songId: Long,

    val playCount: Int = 0,
    val skipCount: Int = 0,
    val totalPlayedMs: Long = 0L,
    val lastPlayedAt: Long? = null
)
