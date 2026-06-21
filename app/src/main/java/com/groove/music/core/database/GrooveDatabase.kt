package com.groove.music.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.groove.music.core.database.dao.ArtworkCacheDao
import com.groove.music.core.database.dao.DownloadQueueDao
import com.groove.music.core.database.dao.LyricsCacheDao
import com.groove.music.core.database.dao.PlayHistoryDao
import com.groove.music.core.database.dao.PlaylistDao
import com.groove.music.core.database.dao.SongDao
import com.groove.music.core.database.entity.ArtworkCacheEntity
import com.groove.music.core.database.entity.DownloadQueueEntity
import com.groove.music.core.database.entity.LyricsCacheEntity
import com.groove.music.core.database.entity.PlayHistoryEntity
import com.groove.music.core.database.entity.PlaylistEntity
import com.groove.music.core.database.entity.PlaylistSongCrossRef
import com.groove.music.core.database.entity.SongEntity
import com.groove.music.core.database.entity.SongStatsEntity

/**
 * Version history:
 *   1 → Initial schema (songs, playlists, playlist_songs, download_queue)
 *   2 → Added spotifyId column to songs; added workerId to download_queue
 *   3 → Added isDownloaded, downloadedAt, artworkCachePath to songs;
 *       Added lyrics_cache, artwork_cache, play_history, song_stats tables
 *
 * Migration 2→3 is in DatabaseModule.kt (addColumn + createTable statements).
 */
@Database(
    entities = [
        SongEntity::class,
        PlaylistEntity::class,
        PlaylistSongCrossRef::class,
        DownloadQueueEntity::class,
        LyricsCacheEntity::class,
        ArtworkCacheEntity::class,
        PlayHistoryEntity::class,
        SongStatsEntity::class,
    ],
    version = 3,
    exportSchema = true
)
abstract class GrooveDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun downloadQueueDao(): DownloadQueueDao
    abstract fun lyricsCacheDao(): LyricsCacheDao
    abstract fun artworkCacheDao(): ArtworkCacheDao
    abstract fun playHistoryDao(): PlayHistoryDao

    companion object {
        const val DATABASE_NAME = "groove_music.db"
    }
}
