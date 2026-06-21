package com.groove.music.data.download

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.groove.music.core.database.dao.ArtworkCacheDao
import com.groove.music.core.database.entity.ArtworkCacheEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ArtworkCacheHelper"

/**
 * Downloads or extracts album artwork and saves two compressed thumbnails:
 *   - 128x128  → listThumbnailPath  (for LazyColumn / grids)
 *   - 512x512  → playerThumbnailPath (for NowPlayingScreen)
 *
 * Artwork is NEVER stored full-size. Source bytes are immediately discarded
 * after compression. This keeps artwork_cache/ under ~10 KB per song.
 *
 * Usage:
 *   artworkCacheHelper.downloadAndCompress(songId, "https://…/artwork.jpg")
 *   artworkCacheHelper.extractAndCompress(songId, contentUri)  // for SAF/MediaStore
 */
@Singleton
class ArtworkCacheHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val artworkCacheDao: ArtworkCacheDao
) {
    private val cacheDir = File(context.cacheDir, "artwork").also { it.mkdirs() }

    /**
     * For TIDAL-downloaded songs: fetch artwork URL → compress → save.
     * Called only once during DownloadWorker — TIDAL not touched after this.
     */
    suspend fun downloadAndCompress(songId: Long, artUrl: String) = withContext(Dispatchers.IO) {
        try {
            val response = okHttpClient
                .newCall(Request.Builder().url(artUrl).build())
                .execute()
            val bytes = response.use { it.body?.bytes() } ?: run {
                Log.w(TAG, "Empty artwork response for songId=$songId")
                return@withContext
            }
            val original = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext
            compressAndSave(songId, original)
            original.recycle()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download artwork for songId=$songId: ${e.message}")
        }
    }

    /**
     * For SAF / MediaStore scanned songs: extract embedded art from file URI.
     * Uses MediaMetadataRetriever — no network call, no TIDAL involved.
     */
    suspend fun extractAndCompress(songId: Long, fileUri: Uri) = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, fileUri)
            val bytes = retriever.embeddedPicture ?: return@withContext  // no embedded art
            val original = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext
            compressAndSave(songId, original)
            original.recycle()
        } catch (e: Exception) {
            Log.w(TAG, "No embedded art for songId=$songId: ${e.message}")
        } finally {
            retriever.release()
        }
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private suspend fun compressAndSave(songId: Long, original: Bitmap) {
        val listPath   = compress(original, songId, 128,  "list")
        val playerPath = compress(original, songId, 512,  "player")
        artworkCacheDao.upsert(
            ArtworkCacheEntity(
                songId              = songId,
                listThumbnailPath   = listPath,
                playerThumbnailPath = playerPath
            )
        )
        Log.d(TAG, "Artwork cached for songId=$songId → list=$listPath")
    }

    private fun compress(source: Bitmap, songId: Long, sizePx: Int, tag: String): String {
        val scaled = Bitmap.createScaledBitmap(source, sizePx, sizePx, true)
        val file   = File(cacheDir, "${songId}_$tag.jpg")
        FileOutputStream(file).use { scaled.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        scaled.recycle()
        return file.absolutePath
    }

    /** Delete cached thumbnails for a song (called when song is deleted) */
    suspend fun delete(songId: Long) = withContext(Dispatchers.IO) {
        File(cacheDir, "${songId}_list.jpg").delete()
        File(cacheDir, "${songId}_player.jpg").delete()
        artworkCacheDao.delete(songId)
    }
}
