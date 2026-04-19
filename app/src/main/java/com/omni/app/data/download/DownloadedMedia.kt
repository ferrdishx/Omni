package com.omni.app.data.download

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloaded_media")
data class DownloadedMedia(
    @PrimaryKey val id: String,
    val title: String,
    val filePath: String,
    val thumbnailUrl: String?,
    val isAudio: Boolean,
    val author: String = "Unknown",
    val format: String = "Unknown",
    val timestamp: Long = System.currentTimeMillis()
)
