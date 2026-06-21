package com.groove.music.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Mirrors the IndexedDB `songs` table (indexedDB.js v3).
 *
 * sourceType values (mirrors web sourceType field):
 *   "mediastore"  — scanned from Android MediaStore (device music)
 *   "saf"         — scanned from user-picked SAF folder
 *   "upload"      — manually opened single file
 *   "downloaded"  — fetched via Songlink/TIDAL (Phase 2)
 */
@Entity(
    tableName = "songs",
    indices = [
        Index(value = ["filePath"], unique = true),
        Index(value = ["title"]),
        Index(value = ["artist"]),
        Index(value = ["genre"])
    ]
)
data class SongEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // ── Core metadata (mirrors folderScanner.js song object) ──
    val title: String,
    val artist: String,
    val album: String,
    val genre: String? = null,
    val year: Int? = null,
    val durationMs: Long = 0,           // stored as millis internally; formatted for UI

    // ── Source tracking ──────────────────────────────────────
    val sourceType: String,             // "mediastore" | "saf" | "upload" | "downloaded"
    val filePath: String? = null,       // content:// URI string OR absolute file path
    val folderName: String? = null,     // e.g. "Downloads", "Rock", mirrors folderName in web

    // ── Art ──────────────────────────────────────────────────
    val coverUri: String? = null,       // embedded art content URI, remote URL, or null

    // ── Spotify / Download integration (Phase 2 ready) ───────
    val isrc: String? = null,           // International Standard Recording Code
    val spotifyId: String? = null,

    // ── Download state ───────────────────────────────────────
    val isDownloaded: Boolean = false,      // true once file is saved to Music/Groove/
    val downloadedAt: Long? = null,         // epoch ms of download completion

    // ── Artwork cache (compressed thumbnails) ─────────────────
    // Path stored here for fast access; full record in artwork_cache table
    val artworkCachePath: String? = null,   // 128x128 list thumbnail path

    // ── User state ───────────────────────────────────────────
    val isFavorite: Boolean = false,    // mirrors likedSongs Set in Zustand

    val dateAdded: Long = System.currentTimeMillis()
)
