package com.groove.music.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Stores paths to compressed artwork thumbnails for a song.
 *
 * Two sizes are stored:
 *   listThumbnailPath   — 128x128 JPEG for song lists and grids (fast loading)
 *   playerThumbnailPath — 512x512 JPEG for the Now Playing screen
 *
 * Artwork is never stored full-size. Source artwork (from TIDAL URL or embedded
 * in audio file) is compressed and saved here; the source is then discarded.
 *
 * For SAF/MediaStore scanned songs: embedded art is extracted via MediaMetadataRetriever.
 * For TIDAL downloads: artwork URL is fetched and compressed during DownloadWorker.
 */
@Entity(
    tableName = "artwork_cache",
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
data class ArtworkCacheEntity(
    @PrimaryKey
    val songId: Long,

    val listThumbnailPath: String?,     // 128x128 JPEG absolute path in app cache
    val playerThumbnailPath: String?,   // 512x512 JPEG absolute path in app cache
    val cachedAt: Long = System.currentTimeMillis()
)
