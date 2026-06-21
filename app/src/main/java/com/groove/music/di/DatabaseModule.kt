package com.groove.music.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.groove.music.core.database.GrooveDatabase
import com.groove.music.core.database.dao.ArtworkCacheDao
import com.groove.music.core.database.dao.DownloadQueueDao
import com.groove.music.core.database.dao.LyricsCacheDao
import com.groove.music.core.database.dao.PlayHistoryDao
import com.groove.music.core.database.dao.PlaylistDao
import com.groove.music.core.database.dao.SongDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Migration 2 → 3:
 *   - songs: adds isDownloaded, downloadedAt, artworkCachePath columns
 *   - Creates lyrics_cache, artwork_cache, play_history, song_stats tables
 */
private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Add new columns to songs table
        db.execSQL("ALTER TABLE songs ADD COLUMN isDownloaded INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE songs ADD COLUMN downloadedAt INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE songs ADD COLUMN artworkCachePath TEXT DEFAULT NULL")

        // lyrics_cache
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `lyrics_cache` (
                `songId` INTEGER NOT NULL,
                `lyricsType` TEXT NOT NULL,
                `lyricsText` TEXT,
                `syncedLinesJson` TEXT,
                `provider` TEXT,
                `isRtl` INTEGER NOT NULL DEFAULT 0,
                `isOfflineAvailable` INTEGER NOT NULL DEFAULT 0,
                `cachedAt` INTEGER NOT NULL,
                PRIMARY KEY(`songId`),
                FOREIGN KEY(`songId`) REFERENCES `songs`(`id`) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_lyrics_cache_songId` ON `lyrics_cache` (`songId`)")

        // artwork_cache
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `artwork_cache` (
                `songId` INTEGER NOT NULL,
                `listThumbnailPath` TEXT,
                `playerThumbnailPath` TEXT,
                `cachedAt` INTEGER NOT NULL,
                PRIMARY KEY(`songId`),
                FOREIGN KEY(`songId`) REFERENCES `songs`(`id`) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_artwork_cache_songId` ON `artwork_cache` (`songId`)")

        // play_history
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `play_history` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `songId` INTEGER NOT NULL,
                `playedAt` INTEGER NOT NULL,
                `completionPercentage` REAL NOT NULL DEFAULT 0,
                `lastPositionMs` INTEGER NOT NULL DEFAULT 0,
                `totalPlayedMs` INTEGER NOT NULL DEFAULT 0,
                `wasSkipped` INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY(`songId`) REFERENCES `songs`(`id`) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_play_history_songId` ON `play_history` (`songId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_play_history_playedAt` ON `play_history` (`playedAt`)")

        // song_stats
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `song_stats` (
                `songId` INTEGER NOT NULL,
                `playCount` INTEGER NOT NULL DEFAULT 0,
                `skipCount` INTEGER NOT NULL DEFAULT 0,
                `totalPlayedMs` INTEGER NOT NULL DEFAULT 0,
                `lastPlayedAt` INTEGER DEFAULT NULL,
                PRIMARY KEY(`songId`),
                FOREIGN KEY(`songId`) REFERENCES `songs`(`id`) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_song_stats_songId` ON `song_stats` (`songId`)")
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideGrooveDatabase(@ApplicationContext context: Context): GrooveDatabase =
        Room.databaseBuilder(
            context,
            GrooveDatabase::class.java,
            GrooveDatabase.DATABASE_NAME
        )
        .addMigrations(MIGRATION_2_3)
        .build()

    @Provides fun provideSongDao(db: GrooveDatabase): SongDao = db.songDao()

    @Provides fun providePlaylistDao(db: GrooveDatabase): PlaylistDao = db.playlistDao()

    @Provides fun provideDownloadQueueDao(db: GrooveDatabase): DownloadQueueDao = db.downloadQueueDao()

    @Provides fun provideLyricsCacheDao(db: GrooveDatabase): LyricsCacheDao = db.lyricsCacheDao()

    @Provides fun provideArtworkCacheDao(db: GrooveDatabase): ArtworkCacheDao = db.artworkCacheDao()

    @Provides fun providePlayHistoryDao(db: GrooveDatabase): PlayHistoryDao = db.playHistoryDao()
}
