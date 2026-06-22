# Groove — Android Music Player

> The Android companion to [Groove Web](https://github.com/Abhishekog19/GrooveWeb). Your local music library, beautifully managed on-device.

Groove for Android is a native music player built in Kotlin. It reads your local music files from phone storage and SAF-accessible folders, manages metadata through a 7-table Room database, and plays audio entirely offline via Media3 ExoPlayer — no streaming, no network dependency after setup.

---

## What It Does

Groove scans your device for music files, extracts and stores their metadata locally, and plays them back with zero network dependency. Every piece of data — audio, artwork, lyrics, play history — lives on your device.

---

## Features

### 🎵 Playback
- **Media3 ExoPlayer** — handles all audio playback; no `MediaPlayer` anywhere in the codebase
- Background playback with full notification controls
- Lock screen media controls via `MediaSession`
- Gapless playback support
- Queue management

### 📁 Library — Two Import Paths

**MediaStore scan** — automatically indexes audio files already on the device via the Android MediaStore API.

**SAF folder scan** — user picks any folder via the Storage Access Framework; Groove recursively walks the `DocumentFile` tree, extracts metadata per file using `MediaMetadataRetriever`, and imports everything. No cloud, no network — pure local extraction.

Supported formats: MP3, FLAC, WAV, M4A, AAC, OGG

### 🗂️ Offline Storage Architecture

Everything Groove needs to play a song is stored locally — restart with airplane mode and it works identically:

| Resource | Source |
|---|---|
| Audio stream | `file://Music/Groove/…` on shared storage |
| Track metadata | `songs` table in Room |
| Album artwork | `artwork_cache` table (compressed JPEG thumbnails) |
| Lyrics | `lyrics_cache` table |
| Playlist membership | `playlist_song_map` join table |
| Play history | `play_history` + `song_stats` tables |

### 🎨 Smart Artwork Handling
- Artwork is extracted from embedded ID3 tags (for scanned files) or fetched during download
- Compressed to two sizes: **128×128px** for list/grid views, **512×512px** for the player screen
- Stored in app cache directory — never re-fetched, never re-processed

### 📋 Playlists
- Create and manage custom playlists
- Auto-generated playlists from folder structure (one playlist per scanned folder)
- Pure join-table architecture — no song data duplicated in playlist records

### 📊 Play History & Stats
- Every play session recorded: completion percentage, total time played, skip status
- Per-song aggregate stats: play count, skip count, total listened time, last played
- Foundation for future smart recommendations

### 🎼 Lyrics
- Supports plain text and synced (LRC-style) lyrics
- Stored in a dedicated `lyrics_cache` table — separate from song metadata
- RTL language support

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| Audio Engine | Media3 ExoPlayer |
| Database | Room (7 tables) |
| Dependency Injection | Hilt |
| Networking | Retrofit + OkHttp |
| Image Loading | Coil |
| File Access | SAF (Storage Access Framework) + DocumentFile |
| Metadata Extraction | `MediaMetadataRetriever` |
| Architecture | MVVM + Repository pattern |
| Async | Kotlin Coroutines + Flow |
| Build | Gradle (Kotlin DSL) |

---

## Database Schema

Groove uses 7 focused Room tables — each owns a single responsibility:

```
┌─────────────────────────────────────────────────────────────┐
│  songs              — core track data + local file URI      │
│  artwork_cache      — compressed thumbnail paths (2 sizes)  │
│  lyrics_cache       — plain + synced lyrics per song        │
│  playlists          — playlist metadata                     │
│  playlist_song_map  — many-to-many join (no duplication)    │
│  play_history       — per-session play records              │
│  song_stats         — aggregate stats per song              │
└─────────────────────────────────────────────────────────────┘
```

**`songs`** — stores URI, title, artist, album, genre, duration, ISRC, source type (`saf` / `mediastore`), download state, and a reference to the artwork cache path.

**`artwork_cache`** — two compressed JPEG paths per song: a 128px thumbnail for list views and a 512px version for the player. Extracted from embedded ID3 tags at scan time, never fetched again.

**`lyrics_cache`** — stores plain or synced lyrics with provider metadata and an offline availability flag. Separated from songs to avoid bloating the main table.

**`playlist_song_map`** — pure join table with `playlistId`, `songId`, and `position`. Playlists never duplicate song data.

**`play_history`** — one row per listening session: `completionPercentage`, `totalPlayedMs`, `wasSkipped`, `lastPositionMs`. Powers future recommendation logic.

**`song_stats`** — aggregated per song: total play count, skip count, total time listened, last played timestamp. Updated atomically after every session.

---

## Architecture

```
┌──────────────────────────────────────────────────────┐
│                  UI Layer (Activities/Fragments)      │
│              ViewModels (Hilt-injected)               │
└──────────────────────────┬───────────────────────────┘
                           │
┌──────────────────────────▼───────────────────────────┐
│                  Repository Layer                     │
│  SongRepository      — local DB only, zero network   │
│  PlayHistoryRepository — records plays + stats       │
│  PlaylistRepository  — playlist + join table ops     │
│  ArtworkCacheHelper  — compress + store thumbnails   │
└──────────────────────────┬───────────────────────────┘
                           │
┌──────────────────────────▼───────────────────────────┐
│               Data Layer (Room + MediaStore)          │
│  GrooveDatabase (7 DAOs)                             │
│  SafFolderScannerImpl (DocumentFile tree walk)       │
│  MediaMetadataRetriever (tag extraction)             │
└──────────────────────────────────────────────────────┘
```

### Key Design Decision — Strict Offline Boundary

Playback is completely isolated from any network layer. The `SongRepository` has zero network dependencies — it reads from Room only. All artwork and lyrics are pre-fetched and cached at import time so playback never waits on a network call.

### Data Flows

**SAF Scan Flow:**
```
User picks folder
  → SafFolderScannerImpl walks DocumentFile tree
  → MediaMetadataRetriever extracts tags per file
  → Write to: songs table + artwork_cache (compressed)
  → Auto-create playlists from folder names via playlist_song_map
```

**Playback Flow:**
```
User taps Play
  → SongRepository.getPlaybackData(songId) — pure DB lookup
  → Load uri from songs, artwork from artwork_cache
  → ExoPlayer: MediaItem.fromUri(song.uri)
  → On session end: PlayHistoryRepository.recordPlay()
     → Insert into play_history
     → Upsert into song_stats (atomic update)
```

---

## File Storage Layout

```
/sdcard/Music/Groove/              ← shared storage, visible in file manager
    Artist - Title.flac
    Artist - Title.mp3
    ...

/data/data/com.groove.music/       ← app private
    databases/
        groove_db                  ← all 7 Room tables
    cache/
        artwork/
            101_list.jpg           ← 128×128 list thumbnail
            101_player.jpg         ← 512×512 player artwork
            102_list.jpg
```

---

## Getting Started

### Prerequisites
- Android Studio Hedgehog or newer
- Android SDK 26+
- A physical or virtual device running Android 8.0+

### Build

```bash
git clone https://github.com/Abhishekog19/GrooveApp.git
cd GrooveApp
```

Open in Android Studio and sync Gradle. No API keys or environment variables required — the app is fully self-contained and local.

```bash
# Or build from command line
./gradlew assembleDebug
```

### Permissions

The app requires the following permissions at runtime:

```xml
<!-- Android 13+ -->
<uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />

<!-- Android 12 and below -->
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
    android:maxSdkVersion="32" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
    android:maxSdkVersion="29" />
```

---

## Project Structure

```
app/src/main/java/com/groove/
├── data/
│   ├── db/
│   │   ├── GrooveDatabase.kt
│   │   ├── entities/          # SongEntity, ArtworkCacheEntity, etc.
│   │   └── dao/               # One DAO per table
│   ├── repository/
│   │   ├── SongRepository.kt
│   │   ├── PlayHistoryRepository.kt
│   │   ├── PlaylistRepository.kt
│   │   └── ArtworkCacheHelper.kt
│   └── scanner/
│       └── SafFolderScannerImpl.kt
├── domain/
│   └── model/                 # Domain models (Song, Playlist, etc.)
├── player/
│   ├── QueueManager.kt
│   └── PlayerViewModel.kt     # Drives Media3 ExoPlayer
└── ui/
    ├── library/
    ├── player/
    ├── playlists/
    └── settings/
```

---

## Relationship to Groove Web

GrooveApp is the Android-native counterpart to [Groove Web](https://github.com/Abhishekog19/GrooveWeb). Both share the same product concept — a privacy-first, local-first music player — but are built independently for their respective platforms:

| | GrooveWeb | GrooveApp |
|---|---|---|
| Platform | Browser / PWA | Android (native) |
| Language | TypeScript / React | Kotlin |
| Audio Engine | Howler.js | Media3 ExoPlayer |
| Local Storage | IndexedDB (Dexie.js) | Room (SQLite) |
| Backend | Node.js / Express | None (fully on-device) |
| File Access | File API / drag-drop | MediaStore + SAF |

---

## Screenshots

*Coming soon.*

---

## Author

**Abhishek** — [@Abhishekog19](https://github.com/Abhishekog19)
