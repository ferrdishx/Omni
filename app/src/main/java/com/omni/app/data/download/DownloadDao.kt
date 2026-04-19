package com.omni.app.data.download

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloaded_media ORDER BY timestamp DESC")
    fun getAllMedia(): Flow<List<DownloadedMedia>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(media: DownloadedMedia)

    @Delete
    suspend fun delete(media: DownloadedMedia)

    @Query("DELETE FROM downloaded_media WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM downloaded_media WHERE id = :id")
    suspend fun getMediaById(id: String): DownloadedMedia?
}
