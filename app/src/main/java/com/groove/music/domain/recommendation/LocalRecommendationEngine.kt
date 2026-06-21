package com.groove.music.domain.recommendation

import android.util.Log
import com.groove.music.core.database.dao.PlayHistoryDao
import com.groove.music.core.database.dao.SongDao
import com.groove.music.core.database.entity.SongStatsEntity
import com.groove.music.data.mapper.SongMapper.toDomain
import com.groove.music.domain.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "LocalRecommendationEngine"

/**
 * Local personalization engine — no network required.
 *
 * Uses Room data (song_stats, play_history, SongEntity) to power
 * the Home Feed sections. Completely offline.
 *
 * Scoring formula for "Recommended For You":
 *   score = playCount * 5 - skipCount * 4 + recentBonus + downloadedBonus
 *   recentBonus: +10 (played < 2 days ago), +5 (< 7 days), 0 (older)
 *   downloadedBonus: +3 (isDownloaded = true)
 *
 * This is separate from the online Discovery layer (RecommendationRepository)
 * which uses the Smusic backend for similar-track discovery.
 */
@Singleton
class LocalRecommendationEngine @Inject constructor(
    private val songDao: SongDao,
    private val playHistoryDao: PlayHistoryDao
) {

    // ── Section: Most Played ──────────────────────────────────────────────────

    /**
     * Top songs by raw playCount, de-weighted by skip ratio.
     * A song with high skips is penalised: effectiveCount = playCount - skipCount * 0.5
     */
    suspend fun getMostPlayed(limit: Int = 10): List<Song> = withContext(Dispatchers.IO) {
        val stats  = playHistoryDao.getTopPlayed(limit * 3)          // over-fetch for de-weighting
        val songIds = stats.map { it.songId }
        if (songIds.isEmpty()) return@withContext emptyList()

        val songs = songDao.getByIds(songIds).associateBy { it.id }
        val statsMap = stats.associateBy { it.songId }

        stats
            .sortedByDescending { stat ->
                val effective = stat.playCount - stat.skipCount * 0.5
                effective
            }
            .take(limit)
            .mapNotNull { songs[it.songId]?.toDomain() }
            .also { Log.d(TAG, "getMostPlayed: ${it.size} songs") }
    }

    // ── Section: Recently Played ──────────────────────────────────────────────

    /** Most recently played songs, ordered by lastPlayedAt DESC. */
    suspend fun getRecentlyPlayed(limit: Int = 10): List<Song> = withContext(Dispatchers.IO) {
        val stats = playHistoryDao.getRecentlyPlayed(limit)
        val songIds = stats.map { it.songId }
        if (songIds.isEmpty()) return@withContext emptyList()

        val songs = songDao.getByIds(songIds).associateBy { it.id }
        // Preserve lastPlayedAt ordering from the DB query
        stats.mapNotNull { songs[it.songId]?.toDomain() }
            .also { Log.d(TAG, "getRecentlyPlayed: ${it.size} songs") }
    }

    // ── Section: Offline Picks ────────────────────────────────────────────────

    /**
     * Downloaded songs ranked by playCount DESC + recency + low skip rate.
     * Only isDownloaded = true songs are included.
     */
    suspend fun getOfflinePicks(limit: Int = 10): List<Song> = withContext(Dispatchers.IO) {
        val downloadedEntities = songDao.getDownloadedSongsOnce()
        if (downloadedEntities.isEmpty()) return@withContext emptyList()

        val ids      = downloadedEntities.map { it.id }
        val statsMap = playHistoryDao.getTopPlayed(ids.size * 2)
            .filter { it.songId in ids }
            .associateBy { it.songId }

        val now = System.currentTimeMillis()
        downloadedEntities
            .sortedByDescending { entity ->
                val stat = statsMap[entity.id]
                computeScore(
                    playCount       = stat?.playCount ?: 0,
                    skipCount       = stat?.skipCount ?: 0,
                    lastPlayedAt    = stat?.lastPlayedAt,
                    isDownloaded    = true,
                    now             = now
                )
            }
            .take(limit)
            .map { it.toDomain() }
            .also { Log.d(TAG, "getOfflinePicks: ${it.size} songs") }
    }

    // ── Section: Forgotten Favorites ─────────────────────────────────────────

    /**
     * Songs with playCount >= 3 that haven't been played in the last 14 days.
     * Good for "rediscovery" prompts.
     */
    suspend fun getForgottenFavorites(limit: Int = 10): List<Song> = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - (14L * 24 * 60 * 60 * 1000)
        val stats  = playHistoryDao.getTopPlayed(200)           // broad sweep

        val forgotten = stats.filter { stat ->
            stat.playCount >= 3 &&
                (stat.lastPlayedAt == null || stat.lastPlayedAt < cutoff)
        }

        val songIds = forgotten.map { it.songId }
        if (songIds.isEmpty()) return@withContext emptyList()

        val songs = songDao.getByIds(songIds).associateBy { it.id }
        forgotten
            .sortedByDescending { it.playCount }
            .take(limit)
            .mapNotNull { songs[it.songId]?.toDomain() }
            .also { Log.d(TAG, "getForgottenFavorites: ${it.size} songs") }
    }

    // ── Section: Recommended For You ─────────────────────────────────────────

    /**
     * All library songs with any play history, ranked by the full scoring formula.
     *
     * score = playCount * 5 - skipCount * 4 + recentBonus + downloadedBonus
     */
    suspend fun getRecommendedForYou(limit: Int = 10): List<Song> = withContext(Dispatchers.IO) {
        val stats = playHistoryDao.getTopPlayed(500)
        if (stats.isEmpty()) return@withContext emptyList()

        val songIds  = stats.map { it.songId }
        val songs    = songDao.getByIds(songIds).associateBy { it.id }
        val statsMap = stats.associateBy { it.songId }
        val now      = System.currentTimeMillis()

        songs.values
            .sortedByDescending { entity ->
                val stat = statsMap[entity.id]
                computeScore(
                    playCount    = stat?.playCount ?: 0,
                    skipCount    = stat?.skipCount ?: 0,
                    lastPlayedAt = stat?.lastPlayedAt,
                    isDownloaded = entity.isDownloaded,
                    now          = now
                )
            }
            .take(limit)
            .map { it.toDomain() }
            .also { Log.d(TAG, "getRecommendedForYou: ${it.size} songs") }
    }

    // ── Section: Habit Mix ────────────────────────────────────────────────────

    /**
     * v1 approach: recently played songs (last 7 days) + high play counts.
     * Later versions can group by time-of-day or session patterns.
     */
    suspend fun getHabitMix(limit: Int = 10): List<Song> = withContext(Dispatchers.IO) {
        val sevenDaysAgo = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000)
        val recentStats  = playHistoryDao.getRecentlyPlayed(50)
            .filter { it.lastPlayedAt != null && it.lastPlayedAt >= sevenDaysAgo }

        val topStats     = playHistoryDao.getTopPlayed(50)

        // Merge: recent plays first, fill with top plays, deduplicate
        val merged = (recentStats + topStats)
            .distinctBy { it.songId }
            .take(limit * 2)

        val songIds = merged.map { it.songId }
        if (songIds.isEmpty()) return@withContext emptyList()

        val songs = songDao.getByIds(songIds).associateBy { it.id }
        merged
            .take(limit)
            .mapNotNull { songs[it.songId]?.toDomain() }
            .also { Log.d(TAG, "getHabitMix: ${it.size} songs") }
    }

    // ── Scoring formula ───────────────────────────────────────────────────────

    private fun computeScore(
        playCount: Int,
        skipCount: Int,
        lastPlayedAt: Long?,
        isDownloaded: Boolean,
        now: Long
    ): Double {
        val recentBonus = when {
            lastPlayedAt == null              -> 0
            now - lastPlayedAt < TWO_DAYS_MS  -> 10
            now - lastPlayedAt < SEVEN_DAYS_MS -> 5
            else                              -> 0
        }
        val downloadedBonus = if (isDownloaded) 3 else 0
        return playCount * 5.0 - skipCount * 4.0 + recentBonus + downloadedBonus
    }

    companion object {
        private const val TWO_DAYS_MS   = 2L  * 24 * 60 * 60 * 1000
        private const val SEVEN_DAYS_MS = 7L  * 24 * 60 * 60 * 1000
    }
}
