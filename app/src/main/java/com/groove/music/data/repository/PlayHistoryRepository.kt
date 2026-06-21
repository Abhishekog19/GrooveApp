package com.groove.music.data.repository

import android.util.Log
import com.groove.music.core.database.dao.PlayHistoryDao
import com.groove.music.core.database.entity.PlayHistoryEntity
import com.groove.music.core.database.entity.SongStatsEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "PlayHistoryRepository"

/**
 * Records play events and maintains aggregated song statistics.
 *
 * Called by PlayerViewModel on every track end or skip.
 * Both play_history and song_stats are written locally — no network involved.
 *
 * These stats will power:
 *   - "Recently Played" list
 *   - "Most Played" list
 *   - Future local recommendation engine
 */
@Singleton
class PlayHistoryRepository @Inject constructor(
    private val playHistoryDao: PlayHistoryDao
) {
    /**
     * Record a completed or skipped play session.
     *
     * @param songId           Room DB id of the song
     * @param totalPlayedMs    How many ms the song was actually playing
     * @param durationMs       Total song duration in ms
     * @param wasSkipped       True if the user skipped before the song finished
     */
    suspend fun recordPlay(
        songId: Long,
        totalPlayedMs: Long,
        durationMs: Long,
        wasSkipped: Boolean = false
    ) = withContext(Dispatchers.IO) {
        val completion = if (durationMs > 0) totalPlayedMs.toFloat() / durationMs else 0f

        // Insert raw play event
        playHistoryDao.insertPlay(
            PlayHistoryEntity(
                songId               = songId,
                completionPercentage = completion,
                totalPlayedMs        = totalPlayedMs,
                wasSkipped           = wasSkipped
            )
        )

        // Update aggregate stats (upsert)
        val existing = playHistoryDao.getStats(songId) ?: SongStatsEntity(songId = songId)
        playHistoryDao.upsertStats(
            existing.copy(
                playCount      = existing.playCount + 1,
                skipCount      = existing.skipCount + (if (wasSkipped) 1 else 0),
                totalPlayedMs  = existing.totalPlayedMs + totalPlayedMs,
                lastPlayedAt   = System.currentTimeMillis()
            )
        )

        Log.d(TAG, "Recorded play: songId=$songId completion=${"%.0f".format(completion * 100)}% skipped=$wasSkipped")
    }

    /** Returns top N most-played songs by play count */
    suspend fun getTopPlayed(limit: Int = 20) = playHistoryDao.getTopPlayed(limit)

    /** Returns top N most recently played songs */
    suspend fun getRecentlyPlayed(limit: Int = 20) = playHistoryDao.getRecentlyPlayed(limit)

    /** Prune history older than [days] days to prevent unbounded DB growth */
    suspend fun pruneOldHistory(days: Int = 90) {
        val cutoff = System.currentTimeMillis() - (days.toLong() * 24 * 60 * 60 * 1000)
        playHistoryDao.pruneOlderThan(cutoff)
    }
}
