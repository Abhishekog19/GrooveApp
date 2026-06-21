package com.groove.music.domain.repository

import com.groove.music.domain.model.Track
import kotlinx.coroutines.flow.Flow

interface DownloadRepository {
    /** Insert into DB + start WorkManager immediately (for single-track downloads) */
    suspend fun enqueueDownload(track: Track, parentPlaylistId: Long?)

    /** Insert into DB as "queued" WITHOUT starting a worker (for batch downloads) */
    suspend fun queueOnly(track: Track, parentPlaylistId: Long?)

    /** Start the WorkManager job for a track that was previously queued via queueOnly() */
    suspend fun startWorker(track: Track, parentPlaylistId: Long?)

    fun getDownloadProgress(spotifyId: String): Flow<Int>
    fun getDownloadStatus(spotifyId: String): Flow<String?>

    /**
     * Remove a downloaded song:
     *  - Delete the local audio file from Music/Groove/ if it exists.
     *  - Mark [songId] as isDownloaded = false in Room.
     *  - Do NOT delete the song from library, history, stats, or artwork cache.
     */
    suspend fun removeDownload(songId: Long)

    /**
     * Validate all songs marked isDownloaded = true.
     * If the local file no longer exists, clear the download state in Room.
     * Called on app startup and when the Downloads screen opens.
     */
    suspend fun validateDownloadedFiles()
}

