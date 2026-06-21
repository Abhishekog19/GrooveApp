package com.groove.music.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Records every individual play event.
 *
 * Stored from Day 1 to power future recommendation engine.
 * Each row = one play session (one song start).
 *
 * completionPercentage: 0.0 = skipped immediately, 1.0 = played to end
 * wasSkipped: true if the user skipped before 30% completion
 */
@Entity(
    tableName = "play_history",
    foreignKeys = [
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("songId"), Index("playedAt")]
)
data class PlayHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val songId: Long,
    val playedAt: Long = System.currentTimeMillis(),
    val completionPercentage: Float = 0f,   // 0.0–1.0
    val lastPositionMs: Long = 0L,
    val totalPlayedMs: Long = 0L,
    val wasSkipped: Boolean = false
)
