package com.groove.music.data.repository

import com.groove.music.core.database.dao.SongDao
import com.groove.music.core.database.entity.SongEntity
import com.groove.music.data.mapper.SongMapper.toDomain
import com.groove.music.data.mapper.SongMapper.toDomainList
import com.groove.music.domain.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository implementation — the single data access point for songs.
 * Mirrors the pattern of fetchSongs / addSong / removeSong in useLibraryStore.
 */
@Singleton
class SongRepository @Inject constructor(
    private val songDao: SongDao
) {
    // ── Reactive streams ──────────────────────────────────────────────────────
    val allSongs: Flow<List<Song>> = songDao.observeAll().map { it.toDomainList() }
    val genres: Flow<List<String>> = songDao.observeGenres()
    val songCount: Flow<Int> = songDao.observeCount()
    val favorites: Flow<List<Song>> = songDao.observeFavorites().map { it.toDomainList() }

    fun search(query: String, genre: String): Flow<List<Song>> =
        songDao.search(query, genre).map { it.toDomainList() }

    // ── One-shot operations (mirrors Zustand action functions) ─────────────────
    suspend fun getAll(): List<Song> = songDao.getAll().toDomainList()

    suspend fun getByIds(ids: List<Long>): List<Song> =
        songDao.getByIds(ids).toDomainList()

    suspend fun getById(id: Long): Song? = songDao.getById(id)?.toDomain()

    /** Insert single song; returns generated ID, or -1 if duplicate (IGNORE conflict). */
    suspend fun insert(song: SongEntity): Long = songDao.insert(song)

    /** Batch insert — returns list of generated IDs (-1 for duplicates). */
    suspend fun insertAll(songs: List<SongEntity>): List<Long> = songDao.insertAll(songs)

    suspend fun update(song: SongEntity) = songDao.update(song)

    /** Mirrors toggleLike in usePlayerStore */
    suspend fun setFavorite(id: Long, isFavorite: Boolean) =
        songDao.setFavorite(id, isFavorite)

    /** Mirrors removeSong in useLibraryStore */
    suspend fun deleteById(id: Long) = songDao.deleteById(id)

    suspend fun deleteByFilePath(filePath: String) = songDao.deleteByFilePath(filePath)

    // ── ISRC / dedup matching (mirrors deduplicator.js) ──────────────────────
    suspend fun findByIsrc(isrc: String): Song? = songDao.findByIsrc(isrc)?.toDomain()

    suspend fun findByTitleAndArtist(title: String, artist: String): Song? =
        songDao.findByTitleAndArtist(title, artist)?.toDomain()

    suspend fun findByFilePath(filePath: String): Song? =
        songDao.findByFilePath(filePath)?.toDomain()

    // ── Folder helpers (mirrors folderName grouping in syncFolderSongs) ───────
    suspend fun getByFolder(folderName: String): List<Song> =
        songDao.getByFolder(folderName).toDomainList()

    suspend fun getDistinctFolderNames(): List<String> =
        songDao.getDistinctFolderNames()

    // ── Download helpers ──────────────────────────────────────────────────────

    /** Reactive stream of downloaded songs (newest first). */
    val downloadedSongs: Flow<List<Song>> =
        songDao.observeDownloadedSongs().map { it.toDomainList() }

    /** One-shot list of all downloaded songs — used for stale-file validation. */
    suspend fun getDownloadedSongsOnce(): List<Song> =
        songDao.getDownloadedSongsOnce().toDomainList()

    /**
     * Targeted update of download state fields without touching other columns.
     * Pass [filePath] = null to clear the local path (e.g. on remove-download).
     */
    suspend fun updateDownloadState(
        songId: Long,
        isDownloaded: Boolean,
        downloadedAt: Long?,
        filePath: String?
    ) = songDao.updateDownloadState(songId, isDownloaded, downloadedAt, filePath)

    /** Entity-level access needed by DownloadWorker / RemoveDownload logic. */
    suspend fun getSongEntityById(id: Long) = songDao.getSongEntityById(id)
}

