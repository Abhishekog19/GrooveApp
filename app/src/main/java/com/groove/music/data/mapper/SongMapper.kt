package com.groove.music.data.mapper

import com.groove.music.core.database.entity.SongEntity
import com.groove.music.domain.model.Song
import com.groove.music.domain.model.toFormattedDuration

/**
 * Maps between Room entities ↔ domain models.
 * Keeps all Android/Room types out of the domain layer.
 */
object SongMapper {

    fun SongEntity.toDomain(): Song = Song(
        id               = id,
        title            = title,
        artist           = artist,
        album            = album,
        genre            = genre,
        year             = year,
        durationMs       = durationMs,
        durationFormatted = durationMs.toFormattedDuration(),
        sourceType       = sourceType,
        filePath         = filePath,
        folderName       = folderName,
        coverUri         = coverUri,
        isrc             = isrc,
        spotifyId        = spotifyId,
        isFavorite       = isFavorite,
        isDownloaded     = isDownloaded,
        dateAdded        = dateAdded
    )

    fun Song.toEntity(): SongEntity = SongEntity(
        id         = id,
        title      = title,
        artist     = artist,
        album      = album,
        genre      = genre,
        year       = year,
        durationMs = durationMs,
        sourceType = sourceType,
        filePath   = filePath,
        folderName = folderName,
        coverUri   = coverUri,
        isrc       = isrc,
        spotifyId  = spotifyId,
        isFavorite = isFavorite,
        isDownloaded = isDownloaded,
        dateAdded  = dateAdded
    )

    fun List<SongEntity>.toDomainList(): List<Song> = map { it.toDomain() }
}
