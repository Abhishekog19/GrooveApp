package com.groove.music.data.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.groove.music.core.database.dao.DownloadQueueDao
import com.groove.music.core.database.entity.SongEntity
import com.groove.music.core.network.SonglinkApiService
import com.groove.music.data.download.ArtworkCacheHelper
import com.groove.music.data.download.DownloadStorageHelper
import com.groove.music.data.repository.PlaylistRepository
import com.groove.music.data.repository.SongRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "DownloadWorker"

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val songlinkApi: SonglinkApiService,
    private val songRepo: SongRepository,
    private val playlistRepo: PlaylistRepository,
    private val downloadQueueDao: DownloadQueueDao,
    private val artworkCacheHelper: ArtworkCacheHelper
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val trackId    = inputData.getString("trackId")  ?: return@withContext Result.failure()
        val title      = inputData.getString("title")    ?: return@withContext Result.failure()
        val artist     = inputData.getString("artist")   ?: return@withContext Result.failure()
        val isrc       = inputData.getString("isrc")
        val albumArt   = inputData.getString("albumArt")
        val playlistId = inputData.getLong("playlistId", -1L)

        downloadQueueDao.updateWorkerId(trackId, id.toString())
        Log.d(TAG, "Starting download: \"$title\" by $artist")

        try {
            // ── Step 0: Dedup check — already downloaded to local file? ─────
            val existingByIsrc    = if (!isrc.isNullOrBlank()) songRepo.findByIsrc(isrc) else null
            val existingByTitle   = songRepo.findByTitleAndArtist(title, artist)
            val existing          = existingByIsrc ?: existingByTitle

            if (existing != null && existing.isDownloaded && !existing.filePath.isNullOrBlank()) {
                val existingFile = java.io.File(existing.filePath!!)
                if (existingFile.exists()) {
                    Log.d(TAG, "Dedup hit — file already downloaded at ${existing.filePath}")
                    downloadQueueDao.updateStatus(trackId, "done")
                    if (playlistId != -1L) playlistRepo.addSong(playlistId, existing.id)
                    return@withContext Result.success()
                }
            }

            // ── Step 1: Resolve TIDAL stream URL via Smusic backend ─────────
            downloadQueueDao.updateStatus(trackId, "resolving")
            setProgress(workDataOf("status" to "resolving", "percent" to 0))

            val resolved = songlinkApi.resolve(
                title   = title,
                artist  = artist,
                isrc    = isrc,
                quality = "LOSSLESS"
            )
            Log.d(TAG, "Resolved: \"${resolved.title}\" → ${resolved.streamUrl.take(80)}…")

            // ── Step 2: Determine target file ────────────────────────────────
            downloadQueueDao.updateStatus(trackId, "downloading")
            setProgress(workDataOf("status" to "downloading", "percent" to 0))

            val ext        = resolved.format.ifBlank { "flac" }
            val outputFile = DownloadStorageHelper.getTargetFile(
                artist = resolved.artist.ifBlank { artist },
                title  = resolved.title.ifBlank { title },
                ext    = ext
            )

            // ── File-reuse: target file already on disk — no re-download ─────
            if (outputFile.exists()) {
                Log.d(TAG, "File already exists — reusing: ${outputFile.absolutePath}")
                downloadQueueDao.updateProgress(trackId, 100)
                val reuseId = recordToRoom(resolved, title, artist, isrc, trackId, outputFile.absolutePath)
                if (reuseId != -1L && !albumArt.isNullOrBlank()) {
                    artworkCacheHelper.downloadAndCompress(reuseId, albumArt)
                }
                if (reuseId != -1L && playlistId != -1L) {
                    playlistRepo.addSong(playlistId, reuseId)
                }
                downloadQueueDao.updateStatus(trackId, "done")
                setProgress(workDataOf("status" to "done", "percent" to 100))
                return@withContext Result.success()
            }

            // ── Full download ────────────────────────────────────────────────
            val body       = songlinkApi.streamDownloadViaProxy(resolved.streamUrl)
            val total      = body.contentLength()
            var downloaded = 0L
            var lastPct    = -1

            body.byteStream().use { input ->
                outputFile.outputStream().use { output ->
                    val buffer = ByteArray(32_768)
                    var bytes: Int
                    while (input.read(buffer).also { bytes = it } != -1) {
                        output.write(buffer, 0, bytes)
                        downloaded += bytes
                        val pct = if (total > 0) ((downloaded * 100) / total).toInt() else 0
                        if (pct >= lastPct + 5) {
                            lastPct = pct
                            downloadQueueDao.updateProgress(trackId, pct)
                            setProgress(workDataOf("status" to "downloading", "percent" to pct))
                        }
                    }
                }
            }

            downloadQueueDao.updateProgress(trackId, 100)
            Log.d(TAG, "Downloaded ${downloaded / 1024}KB → ${outputFile.absolutePath}")

            // ── Step 3: Add to Room songs table ─────────────────────────────
            val insertedId = recordToRoom(resolved, title, artist, isrc, trackId, outputFile.absolutePath)
            Log.d(TAG, "Inserted song ID=$insertedId into Room")

            // ── Step 4: Cache artwork ────────────────────────────────────────
            if (insertedId != -1L) {
                if (!albumArt.isNullOrBlank()) {
                    artworkCacheHelper.downloadAndCompress(insertedId, albumArt)
                }
                if (playlistId != -1L) {
                    playlistRepo.addSong(playlistId, insertedId)
                }
            }

            downloadQueueDao.updateStatus(trackId, "done")
            setProgress(workDataOf("status" to "done", "percent" to 100))
            Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "Download failed for \"$title\": ${e.message}", e)
            downloadQueueDao.updateStatus(trackId, "failed")
            if (runAttemptCount < 3) Result.retry()
            else Result.failure(workDataOf("error" to (e.message ?: "Unknown error")))
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private suspend fun recordToRoom(
        resolved: com.groove.music.core.network.dto.TidalResolveResponse,
        title: String,
        artist: String,
        isrc: String?,
        trackId: String,
        filePath: String
    ): Long {
        val newSong = SongEntity(
            title        = resolved.title.ifBlank { title },
            artist       = resolved.artist.ifBlank { artist },
            album        = resolved.album,
            durationMs   = resolved.durationMs,
            filePath     = filePath,
            sourceType   = "downloaded",
            isrc         = isrc,
            spotifyId    = trackId,
            folderName   = "Downloads",
            isDownloaded = true,
            downloadedAt = System.currentTimeMillis()
        )
        return songRepo.insert(newSong)
    }
}
