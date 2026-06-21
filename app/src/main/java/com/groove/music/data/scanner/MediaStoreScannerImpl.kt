package com.groove.music.data.scanner

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.groove.music.core.database.dao.ArtworkCacheDao
import com.groove.music.core.database.entity.SongEntity
import com.groove.music.data.download.ArtworkCacheHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "MediaStoreScanner"

/**
 * Scans the device's MediaStore for audio files.
 *
 * This is the Android equivalent of the web app reading files from IndexedDB —
 * no folder picker needed; picks up ALL music already on the device.
 * Runs automatically on first launch (and on re-sync).
 */
@Singleton
class MediaStoreScannerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val artworkCacheHelper: ArtworkCacheHelper,
    private val artworkCacheDao: ArtworkCacheDao
) {
    private val AUDIO_MIME_TYPES = setOf(
        "audio/mpeg",       // MP3
        "audio/flac",       // FLAC
        "audio/mp4",        // M4A / AAC
        "audio/ogg",        // OGG
        "audio/wav",        // WAV
        "audio/x-wav",
        "audio/opus",       // OPUS
        "audio/aac"
    )

    private val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.GENRE,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.DATA,            // absolute file path
        MediaStore.Audio.Media.RELATIVE_PATH,   // folder name
        MediaStore.Audio.Media.YEAR,
        MediaStore.Audio.Media.MIME_TYPE,
        MediaStore.Audio.Media.DATE_ADDED,
        MediaStore.Audio.Media.ALBUM_ID
    )

    /**
     * Scan and return all audio songs found in MediaStore.
     * Songs are returned as [SongEntity] ready for Room insertion.
     *
     * @param onProgress optional progress callback (scannedCount)
     */
    suspend fun scan(onProgress: ((Int) -> Unit)? = null): List<SongEntity> {
        val songs = mutableListOf<SongEntity>()
        var scanned = 0

        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        context.contentResolver.query(uri, projection, null, null, sortOrder)?.use { cursor ->
            val idCol         = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol      = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol     = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol      = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val genreCol      = cursor.getColumnIndex(MediaStore.Audio.Media.GENRE)
            val durationCol   = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val relPathCol    = cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
            val yearCol       = cursor.getColumnIndex(MediaStore.Audio.Media.YEAR)
            val mimeCol       = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val dateAddedCol  = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val albumIdCol    = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

            while (cursor.moveToNext()) {
                val mime = cursor.getString(mimeCol) ?: continue
                if (mime !in AUDIO_MIME_TYPES) continue

                val mediaId    = cursor.getLong(idCol)
                val contentUri = Uri.withAppendedPath(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    mediaId.toString()
                ).toString()

                val relPath   = if (relPathCol >= 0) cursor.getString(relPathCol) else null
                val folderName = relPath
                    ?.trimEnd('/')
                    ?.substringAfterLast('/')
                    ?: "Music"

                val albumId    = cursor.getLong(albumIdCol)
                val albumArtUri = Uri.parse("content://media/external/audio/albumart/$albumId").toString()

                val song = SongEntity(
                    title       = cursor.getString(titleCol)
                        ?.takeIf { it.isNotBlank() } ?: "Unknown Title",
                    artist      = cursor.getString(artistCol)
                        ?.takeIf { it != "<unknown>" } ?: "Unknown Artist",
                    album       = cursor.getString(albumCol)
                        ?.takeIf { it != "<unknown>" } ?: "Unknown Album",
                    genre       = if (genreCol >= 0) cursor.getString(genreCol) else null,
                    durationMs  = cursor.getLong(durationCol),
                    year        = if (yearCol >= 0) cursor.getInt(yearCol).takeIf { it > 0 } else null,
                    sourceType  = "mediastore",
                    filePath    = contentUri,           // content:// URI as the canonical ID
                    folderName  = folderName,
                    coverUri    = albumArtUri,
                    dateAdded   = cursor.getLong(dateAddedCol) * 1000 // seconds → millis
                )

                songs.add(song)
                scanned++
                onProgress?.invoke(scanned)
            }
        }

        return songs
    }

    /**
     * After songs are inserted into Room, extract and compress their embedded artwork.
     * Pass the map of [SongEntity.filePath] → inserted Room ID.
     * Songs that already have an artwork cache entry are skipped.
     *
     * @param insertedSongs Map of content:// URI → Room songId
     */
    suspend fun cacheArtworkForInserted(insertedSongs: Map<String, Long>) {
        for ((contentUriStr, songId) in insertedSongs) {
            try {
                // Skip if artwork already cached (re-scan case)
                if (artworkCacheDao.find(songId) != null) continue

                val contentUri = Uri.parse(contentUriStr)
                artworkCacheHelper.extractAndCompress(songId, contentUri)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to cache artwork for songId=$songId: ${e.message}")
            }
        }
    }

    /**
     * Returns a set of all content: URI paths currently in MediaStore.
     * Used for diff-based re-sync (find added/removed songs).
     */
    suspend fun getAllContentUris(): Set<String> {
        val uris = mutableSetOf<String>()
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.MIME_TYPE)

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection, null, null, null
        )?.use { cursor ->
            val idCol   = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            while (cursor.moveToNext()) {
                val mime = cursor.getString(mimeCol) ?: continue
                if (mime in AUDIO_MIME_TYPES) {
                    val id = cursor.getLong(idCol)
                    uris.add(
                        Uri.withAppendedPath(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id.toString()
                        ).toString()
                    )
                }
            }
        }
        return uris
    }
}
