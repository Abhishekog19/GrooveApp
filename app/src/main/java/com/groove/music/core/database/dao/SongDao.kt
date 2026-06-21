package com.groove.music.core.database.dao

import androidx.room.*
import com.groove.music.core.database.entity.SongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {

    // ── Observe all songs (reactive — mirrors Zustand store auto-update) ──────
    @Query("SELECT * FROM songs ORDER BY title ASC")
    fun observeAll(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs ORDER BY dateAdded DESC")
    fun observeByDateAdded(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs ORDER BY artist ASC, album ASC, title ASC")
    fun observeByArtist(): Flow<List<SongEntity>>

    // ── One-shot queries ──────────────────────────────────────────────────────
    @Query("SELECT * FROM songs")
    suspend fun getAll(): List<SongEntity>

    @Query("SELECT * FROM songs WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): SongEntity?

    @Query("SELECT * FROM songs WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<SongEntity>

    // ── Search (mirrors searchQuery filter in Zustand / Library.jsx) ──────────
    @Query("""
        SELECT * FROM songs
        WHERE (:query = '' OR
               title LIKE '%' || :query || '%' OR
               artist LIKE '%' || :query || '%' OR
               album LIKE '%' || :query || '%')
        AND   (:genre = 'all' OR genre = :genre)
        ORDER BY title ASC
    """)
    fun search(query: String, genre: String = "all"): Flow<List<SongEntity>>

    // ── Genre list for filter chips ───────────────────────────────────────────
    @Query("SELECT DISTINCT genre FROM songs WHERE genre IS NOT NULL ORDER BY genre ASC")
    fun observeGenres(): Flow<List<String>>

    // ── Favorites (mirrors likedSongs Set in usePlayerStore) ─────────────────
    @Query("SELECT * FROM songs WHERE isFavorite = 1 ORDER BY title ASC")
    fun observeFavorites(): Flow<List<SongEntity>>

    // ── Deduplication — mirrors deduplicator.js ───────────────────────────────
    @Query("""
        SELECT * FROM songs
        WHERE title = :title AND artist = :artist
        LIMIT 1
    """)
    suspend fun findByTitleAndArtist(title: String, artist: String): SongEntity?

    @Query("SELECT * FROM songs WHERE isrc = :isrc LIMIT 1")
    suspend fun findByIsrc(isrc: String): SongEntity?

    @Query("SELECT * FROM songs WHERE filePath = :filePath LIMIT 1")
    suspend fun findByFilePath(filePath: String): SongEntity?

    // ── Insert / Update / Delete ──────────────────────────────────────────────
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(song: SongEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(songs: List<SongEntity>): List<Long>

    @Update
    suspend fun update(song: SongEntity)

    @Query("UPDATE songs SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: Long, isFavorite: Boolean)

    @Query("DELETE FROM songs WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM songs WHERE filePath = :filePath")
    suspend fun deleteByFilePath(filePath: String)

    // ── Folder-based queries (mirrors folderName grouping in syncFolderSongs) ─
    @Query("SELECT * FROM songs WHERE folderName = :folderName")
    suspend fun getByFolder(folderName: String): List<SongEntity>

    @Query("SELECT DISTINCT folderName FROM songs WHERE folderName IS NOT NULL")
    suspend fun getDistinctFolderNames(): List<String>

    // ── Source type filter (mirrors sourceType in web) ────────────────────────
    @Query("SELECT * FROM songs WHERE sourceType = :sourceType")
    suspend fun getBySourceType(sourceType: String): List<SongEntity>

    @Query("SELECT COUNT(*) FROM songs")
    fun observeCount(): Flow<Int>

    // ── Downloaded songs ──────────────────────────────────────────────────────

    /** Reactive stream of all downloaded songs, newest first. */
    @Query("SELECT * FROM songs WHERE isDownloaded = 1 ORDER BY downloadedAt DESC")
    fun observeDownloadedSongs(): Flow<List<SongEntity>>

    /** One-shot list of downloaded songs — used for validation on startup. */
    @Query("SELECT * FROM songs WHERE isDownloaded = 1")
    suspend fun getDownloadedSongsOnce(): List<SongEntity>

    /** Entity-level getById — needed for download/remove operations. */
    @Query("SELECT * FROM songs WHERE id = :id LIMIT 1")
    suspend fun getSongEntityById(id: Long): SongEntity?

    /**
     * Targeted update of download state fields only.
     * Avoids overwriting unrelated fields (title, artist, stats, etc.).
     */
    @Query("""
        UPDATE songs
        SET isDownloaded = :isDownloaded,
            downloadedAt = :downloadedAt,
            filePath     = :filePath
        WHERE id = :songId
    """)
    suspend fun updateDownloadState(
        songId: Long,
        isDownloaded: Boolean,
        downloadedAt: Long?,
        filePath: String?
    )
}
