package com.groove.music.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "download_queue")
data class DownloadQueueEntity(
    @PrimaryKey val trackId: String,   // spotifyId
    val title: String,
    val artist: String,
    val albumArt: String?,
    val spotifyUrl: String,
    val isrc: String?,
    val status: String,   // "queued"|"resolving"|"downloading"|"done"|"failed"
    val progress: Int = 0,
    val workerId: String? = null
)
