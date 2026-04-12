package com.omni.app.data.download

import java.util.UUID

enum class DownloadStatus {
    QUEUED,
    FETCHING_INFO,
    DOWNLOADING,
    CONVERTING,
    EMBEDDING,
    COMPLETED,
    FAILED,
    CANCELLED
}

enum class DownloadType { VIDEO, AUDIO }

data class AvailableFormat(
    val formatId: String,
    val height: Int?,
    val fps: Int?,
    val ext: String,
    val filesize: Long?,
    val label: String
)

data class DownloadItem(
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    val title: String = "Fetching...",
    val thumbnailUrl: String? = null,
    val type: DownloadType,
    val quality: String,
    val selectedFormatId: String? = null,
    val format: String,
    val prefer60fps: Boolean = true,
    val embedThumbnail: Boolean = true,
    val outputPath: String? = null,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val progress: Float = 0f,
    val speed: String = "",
    val eta: String = "",
    val errorMessage: String? = null,
    val fileSize: String = "",
    val batchId: String? = null,
    val addedAt: Long = System.currentTimeMillis()
)
