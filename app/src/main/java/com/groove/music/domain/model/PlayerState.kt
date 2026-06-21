package com.groove.music.domain.model

/**
 * Mirrors the full state of usePlayerStore in the Smusic web app:
 * currentSong, queue, isPlaying, volume, repeat, shuffle, currentTime, duration.
 *
 * This is the single source of truth consumed by all UI composables.
 */
data class PlayerState(
    val currentSong: Song? = null,
    val queue: List<Song> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val volume: Float = 0.7f,
    val repeat: RepeatMode = RepeatMode.NONE,
    val shuffle: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L
)

enum class RepeatMode {
    NONE,   // mirrors 'none'
    ONE,    // mirrors 'one'
    ALL     // mirrors 'all'
}
