package com.groove.music.core.database.dao

import androidx.room.*
import com.groove.music.core.database.entity.LyricsCacheEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LyricsCacheDao {

    @Query("SELECT * FROM lyrics_cache WHERE songId = :songId LIMIT 1")
    suspend fun find(songId: Long): LyricsCacheEntity?

    @Query("SELECT * FROM lyrics_cache WHERE songId = :songId")
    fun observe(songId: Long): Flow<LyricsCacheEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LyricsCacheEntity)

    @Query("DELETE FROM lyrics_cache WHERE songId = :songId")
    suspend fun delete(songId: Long)

    /** Mark a song as having no lyrics available (prevents repeated backend calls) */
    @Query("""
        INSERT OR REPLACE INTO lyrics_cache (songId, lyricsType, lyricsText, syncedLinesJson, provider, isRtl, isOfflineAvailable, cachedAt)
        VALUES (:songId, 'unavailable', NULL, NULL, NULL, 0, 0, :now)
    """)
    suspend fun markUnavailable(songId: Long, now: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM lyrics_cache WHERE isOfflineAvailable = 1")
    suspend fun countOfflineAvailable(): Int
}
