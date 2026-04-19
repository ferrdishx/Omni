package com.omni.app.data.favorites

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {

    // ── Read ─────────────────────────────────────────────────────────────────

    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun getAll(): Flow<List<FavoriteItem>>

    @Query("""
        SELECT * FROM favorites
        WHERE (title LIKE '%' || :query || '%' OR author LIKE '%' || :query || '%')
        ORDER BY addedAt DESC
    """)
    fun search(query: String): Flow<List<FavoriteItem>>

    @Query("""
        SELECT * FROM favorites
        WHERE (title LIKE '%' || :query || '%' OR author LIKE '%' || :query || '%')
          AND (:audioOnly = 0 OR isAudio = 1)
          AND (:videoOnly = 0 OR isAudio = 0)
        ORDER BY
            CASE WHEN :sortBy = 'title' AND :ascending = 1  THEN title  END ASC,
            CASE WHEN :sortBy = 'title' AND :ascending = 0  THEN title  END DESC,
            CASE WHEN :sortBy = 'author'                    THEN author END ASC,
            CASE WHEN :sortBy = 'size'   AND :ascending = 1 THEN fileSize END ASC,
            CASE WHEN :sortBy = 'size'   AND :ascending = 0 THEN fileSize END DESC,
            addedAt DESC
    """)
    fun getFiltered(
        query: String = "",
        sortBy: String = "date",
        ascending: Boolean = false,
        audioOnly: Boolean = false,
        videoOnly: Boolean = false
    ): Flow<List<FavoriteItem>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE id = :id)")
    fun isFavorite(id: String): Flow<Boolean>

    @Query("SELECT COUNT(*) FROM favorites")
    fun count(): Flow<Int>

    // ── Write ────────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: FavoriteItem)

    @Delete
    suspend fun delete(item: FavoriteItem)

    @Query("DELETE FROM favorites WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM favorites")
    suspend fun deleteAll()
}
