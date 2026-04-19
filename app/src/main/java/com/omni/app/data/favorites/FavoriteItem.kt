package com.omni.app.data.favorites

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A favorited media item.
 * Inspired by ytdlnis HistoryItem — keeps url, metadata and type
 * so the user can re-download or play directly from Favorites.
 */
@Entity(tableName = "favorites")
data class FavoriteItem(
    @PrimaryKey
    val id: String,                         // same id as DownloadedMedia
    val url: String,                        // original source URL
    val title: String,
    val author: String = "",
    val duration: String = "",
    val thumbnailUrl: String? = null,
    val isAudio: Boolean,
    val format: String = "",
    val filePath: String,                   // local file path
    val website: String = "",               // "youtube", "instagram", etc.
    @ColumnInfo(defaultValue = "0")
    val fileSize: Long = 0L,
    val addedAt: Long = System.currentTimeMillis()
)
