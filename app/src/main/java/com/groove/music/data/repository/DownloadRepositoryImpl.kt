package com.groove.music.data.repository

import android.content.Context
import android.util.Log
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.groove.music.core.database.dao.DownloadQueueDao
import com.groove.music.core.database.entity.DownloadQueueEntity
import com.groove.music.data.download.DownloadStorageHelper
import com.groove.music.data.worker.DownloadWorker
import com.groove.music.domain.model.Track
import com.groove.music.domain.repository.DownloadRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

private const val TAG = "DownloadRepositoryImpl"

class DownloadRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadQueueDao: DownloadQueueDao,
    private val songRepository: SongRepository
) : DownloadRepository {

    /** Full enqueue: insert into DB + start WorkManager immediately */
    override suspend fun enqueueDownload(track: Track, parentPlaylistId: Long?) {
        insertToQueue(track)
        launchWorker(track, parentPlaylistId)
    }

    /** Insert into DB as "queued" WITHOUT starting a WorkManager job */
    override suspend fun queueOnly(track: Track, parentPlaylistId: Long?) {
        insertToQueue(track)
    }

    /** Start a WorkManager job for a track already in the queue */
    override suspend fun startWorker(track: Track, parentPlaylistId: Long?) {
        launchWorker(track, parentPlaylistId)
    }

    override fun getDownloadProgress(spotifyId: String): Flow<Int> {
        return downloadQueueDao.getByIdFlow(spotifyId).map { it?.progress ?: 0 }
    }

    override fun getDownloadStatus(spotifyId: String): Flow<String?> {
        return downloadQueueDao.getByIdFlow(spotifyId).map { it?.status }
    }

    // ── Remove download ───────────────────────────────────────────────────────

    override suspend fun removeDownload(songId: Long) = withContext(Dispatchers.IO) {
        val song = songRepository.getSongEntityById(songId) ?: run {
            Log.w(TAG, "removeDownload: song $songId not found")
            return@withContext
        }

        // Delete physical file only if it is inside Music/Groove/ (local download)
        val filePath = song.filePath
        if (!filePath.isNullOrBlank() && filePath.startsWith("/")) {
            val file = File(filePath)
            if (file.exists()) {
                val deleted = file.delete()
                Log.d(TAG, "removeDownload: deleted file=$deleted path=$filePath")
            }
        }

        // Clear download state; keep the song row and all other metadata intact
        songRepository.updateDownloadState(
            songId      = songId,
            isDownloaded = false,
            downloadedAt = null,
            filePath    = if (filePath?.startsWith("/") == true) null else filePath
        )
        Log.d(TAG, "removeDownload: cleared download state for songId=$songId")
    }

    // ── Validate stale downloads ──────────────────────────────────────────────

    override suspend fun validateDownloadedFiles(): Unit = withContext(Dispatchers.IO) {        
        val downloadedSongs = songRepository.getDownloadedSongsOnce()
        var staleCount = 0

        for (song in downloadedSongs) {
            val filePath = song.filePath
            val isStale = when {
                filePath.isNullOrBlank()       -> true          // path was never saved
                filePath.startsWith("content://") -> false      // content URI — managed by MediaStore
                else -> !File(filePath).exists()                // local file gone
            }
            if (isStale) {
                songRepository.updateDownloadState(
                    songId       = song.id,
                    isDownloaded = false,
                    downloadedAt = null,
                    filePath     = if (filePath?.startsWith("/") == true) null else filePath
                )
                staleCount++
                Log.d(TAG, "validateDownloads: cleared stale download songId=${song.id} path=$filePath")
            }
        }
        Log.i(TAG, "validateDownloadedFiles: checked ${downloadedSongs.size} songs, cleared $staleCount stale entries")
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private suspend fun insertToQueue(track: Track) {
        val entity = DownloadQueueEntity(
            trackId    = track.spotifyId,
            title      = track.title,
            artist     = track.artist,
            albumArt   = track.albumArt,
            spotifyUrl = track.spotifyUrl,
            isrc       = track.isrc,
            status     = "queued"
        )
        downloadQueueDao.insertOrUpdate(entity)
    }

    private fun launchWorker(track: Track, parentPlaylistId: Long?) {
        val inputData = Data.Builder()
            .putString("trackId", track.spotifyId)
            .putString("title",   track.title)
            .putString("artist",  track.artist)
            .putString("isrc",    track.isrc)
            .putString("albumArt", track.albumArt)
            .apply { if (parentPlaylistId != null) putLong("playlistId", parentPlaylistId) }
            .build()

        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(inputData)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            track.spotifyId,
            ExistingWorkPolicy.KEEP,
            request
        )
    }
}

