package com.groove.music.core.database.dao

import androidx.room.*
import com.groove.music.core.database.entity.PlayHistoryEntity
import com.groove.music.core.database.entity.SongStatsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlayHistoryDao {

    // ── play_history ─────────────────────────────────────────────────────────

    @Insert
    suspend fun insertPlay(event: PlayHistoryEntity)

    @Query("SELECT * FROM play_history WHERE songId = :songId ORDER BY playedAt DESC")
    fun observeForSong(songId: Long): Flow<List<PlayHistoryEntity>>

    @Query("SELECT * FROM play_history ORDER BY playedAt DESC LIMIT :limit")
    suspend fun getRecentPlays(limit: Int = 50): List<PlayHistoryEntity>

    @Query("DELETE FROM play_history WHERE playedAt < :cutoffMs")
    suspend fun pruneOlderThan(cutoffMs: Long)

    // ── song_stats ────────────────────────────────────────────────────────────

    @Query("SELECT * FROM song_stats WHERE songId = :songId LIMIT 1")
    suspend fun getStats(songId: Long): SongStatsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStats(stats: SongStatsEntity)

    @Query("SELECT * FROM song_stats ORDER BY playCount DESC LIMIT :limit")
    suspend fun getTopPlayed(limit: Int = 20): List<SongStatsEntity>

    @Query("SELECT * FROM song_stats ORDER BY lastPlayedAt DESC LIMIT :limit")
    suspend fun getRecentlyPlayed(limit: Int = 20): List<SongStatsEntity>
}
