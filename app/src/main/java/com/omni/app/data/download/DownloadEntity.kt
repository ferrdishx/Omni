package com.omni.app.data.download

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val id: String,
    val title: String,
    val filePath: String,
    val thumbnail: String?,
    val type: String,
    val quality: String,
    val format: String,
    val size: String,
    val timestamp: Long = System.currentTimeMillis()
)
