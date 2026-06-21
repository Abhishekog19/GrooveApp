package com.groove.music.data.scanner

import androidx.documentfile.provider.DocumentFile
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.groove.music.core.database.dao.ArtworkCacheDao
import com.groove.music.core.database.entity.SongEntity
import com.groove.music.data.download.ArtworkCacheHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SafFolderScanner"

/**
 * SAF (Storage Access Framework) folder scanner.
 *
 * This is the direct Android equivalent of folderScanner.js — recursively
 * walks a DocumentFile tree and extracts metadata from each audio file.
 *
 * The user picks the folder via Intent.ACTION_OPEN_DOCUMENT_TREE,
 * and the persisted URI is passed here.
 */
@Singleton
class SafFolderScannerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val artworkCacheHelper: ArtworkCacheHelper,
    private val artworkCacheDao: ArtworkCacheDao
) {
    private val AUDIO_EXTENSIONS = setOf(
        "mp3", "flac", "m4a", "aac", "ogg", "wav", "opus", "wma"
    )

    /**
     * Recursively scan a folder URI (SAF) and return SongEntity list.
     *
     * Mirrors scanMusicFolder() in folderScanner.js.
     *
     * @param folderUri  Persisted SAF tree URI
     * @param onProgress Optional progress callback (scannedCount, fileName)
     */
    suspend fun scan(
        folderUri: Uri,
        onProgress: ((count: Int, fileName: String) -> Unit)? = null
    ): List<SongEntity> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<SongEntity>()
        val rootDoc = DocumentFile.fromTreeUri(context, folderUri) ?: return@withContext emptyList()

        scanDirectory(rootDoc, rootDoc.name ?: "Music", songs, 0, onProgress)
        songs
    }

    /**
     * After Room insertion, cache artwork for newly inserted SAF songs.
     * [insertedSongs] maps SAF URI string → Room songId.
     * Songs with embedded art are processed; others are skipped gracefully.
     * Already-cached songs are skipped.
     */
    suspend fun cacheArtworkForInserted(insertedSongs: Map<String, Long>) {
        for ((uriStr, songId) in insertedSongs) {
            try {
                if (artworkCacheDao.find(songId) != null) continue

                val uri = Uri.parse(uriStr)
                artworkCacheHelper.extractAndCompress(songId, uri)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to cache artwork for SAF songId=$songId: ${e.message}")
            }
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private suspend fun scanDirectory(
        dir: DocumentFile,
        parentFolderName: String,
        songs: MutableList<SongEntity>,
        depth: Int,
        onProgress: ((Int, String) -> Unit)?
    ) {
        if (depth > 10) return // Guard against deep symlink loops

        dir.listFiles().forEach { entry ->
            if (entry.isDirectory) {
                scanDirectory(entry, entry.name ?: parentFolderName, songs, depth + 1, onProgress)
            } else if (entry.isFile && isAudioFile(entry.name)) {
                try {
                    val song = extractSong(entry, parentFolderName)
                    if (song != null) {
                        songs.add(song)
                        onProgress?.invoke(songs.size, entry.name ?: "")
                    }
                } catch (e: Exception) {
                    // Skip unreadable files (mirrors folderScanner.js catch)
                }
            }
        }
    }

    private fun isAudioFile(name: String?): Boolean {
        if (name == null) return false
        return AUDIO_EXTENSIONS.any { name.lowercase().endsWith(".$it") }
    }

    /**
     * Extract metadata from a DocumentFile audio entry.
     * Uses MediaMetadataRetriever — the Android equivalent of music-metadata JS lib.
     */
    private fun extractSong(file: DocumentFile, folderName: String): SongEntity? {
        val uri = file.uri
        val retriever = MediaMetadataRetriever()

        return try {
            retriever.setDataSource(context, uri)

            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() }
                ?: file.name?.substringBeforeLast('.') ?: return null

            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?.takeIf { it.isNotBlank() } ?: "Unknown Artist"

            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?.takeIf { it.isNotBlank() } ?: "Unknown Album"

            val genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)

            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L

            val year = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
                ?.toIntOrNull()

            // Embedded art is stored as bytes; we reference the content URI
            // so Coil can load it from the file directly
            val hasEmbeddedArt = retriever.embeddedPicture != null

            SongEntity(
                title      = title,
                artist     = artist,
                album      = album,
                genre      = genre,
                durationMs = durationMs,
                year       = year,
                sourceType = "saf",
                filePath   = uri.toString(),
                folderName = folderName,
                coverUri   = if (hasEmbeddedArt) uri.toString() else null
            )
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    }
}
