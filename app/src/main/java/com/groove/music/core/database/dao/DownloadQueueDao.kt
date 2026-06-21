package com.groove.music.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.groove.music.core.database.entity.DownloadQueueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadQueueDao {
    @Query("SELECT * FROM download_queue")
    fun getAllFlow(): Flow<List<DownloadQueueEntity>>

    @Query("SELECT * FROM download_queue WHERE trackId = :trackId")
    fun getByIdFlow(trackId: String): Flow<DownloadQueueEntity?>

    @Query("SELECT status FROM download_queue WHERE trackId = :trackId")
    suspend fun getStatus(trackId: String): String?

    /** Returns downloads with actively running workers (NOT "queued" — those are waiting for batch) */
    @Query("SELECT * FROM download_queue WHERE status IN ('resolving', 'downloading')")
    suspend fun getActiveDownloads(): List<DownloadQueueEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(entity: DownloadQueueEntity)

    @Query("UPDATE download_queue SET status = :status WHERE trackId = :trackId")
    suspend fun updateStatus(trackId: String, status: String)

    @Query("UPDATE download_queue SET progress = :progress WHERE trackId = :trackId")
    suspend fun updateProgress(trackId: String, progress: Int)

    @Query("UPDATE download_queue SET workerId = :workerId WHERE trackId = :trackId")
    suspend fun updateWorkerId(trackId: String, workerId: String?)

    @Query("DELETE FROM download_queue WHERE status = 'done'")
    suspend fun clearCompleted()

    @Query("DELETE FROM download_queue WHERE trackId = :trackId")
    suspend fun deleteById(trackId: String)

    /** Reset any entries stuck mid-download from a previous session. */
    @Query("DELETE FROM download_queue WHERE status NOT IN ('done', 'failed')")
    suspend fun resetStuckDownloads()
}
