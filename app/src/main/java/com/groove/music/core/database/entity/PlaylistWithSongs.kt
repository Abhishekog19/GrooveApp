package com.groove.music.core.database.entity

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

/**
 * Convenience data class for Room queries that return a playlist
 * with all its songs pre-joined — mirrors the pattern in fetchPlaylists()
 * where the web app loads playlists then resolves songIds.
 */
data class PlaylistWithSongs(
    @Embedded val playlist: PlaylistEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = PlaylistSongCrossRef::class,
            parentColumn = "playlistId",
            entityColumn = "songId"
        )
    )
    val songs: List<SongEntity>
)
