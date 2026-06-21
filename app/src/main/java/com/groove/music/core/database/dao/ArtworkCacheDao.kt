package com.groove.music.core.database.dao

import androidx.room.*
import com.groove.music.core.database.entity.ArtworkCacheEntity

@Dao
interface ArtworkCacheDao {

    @Query("SELECT * FROM artwork_cache WHERE songId = :songId LIMIT 1")
    suspend fun find(songId: Long): ArtworkCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ArtworkCacheEntity)

    @Query("DELETE FROM artwork_cache WHERE songId = :songId")
    suspend fun delete(songId: Long)

    @Query("SELECT COUNT(*) FROM artwork_cache")
    suspend fun count(): Int

    /** Returns list thumbnail path for a song — used in RecyclerView / LazyColumn */
    @Query("SELECT listThumbnailPath FROM artwork_cache WHERE songId = :songId LIMIT 1")
    suspend fun getListThumbnailPath(songId: Long): String?

    /** Returns player thumbnail path — used in NowPlayingScreen */
    @Query("SELECT playerThumbnailPath FROM artwork_cache WHERE songId = :songId LIMIT 1")
    suspend fun getPlayerThumbnailPath(songId: Long): String?
}
