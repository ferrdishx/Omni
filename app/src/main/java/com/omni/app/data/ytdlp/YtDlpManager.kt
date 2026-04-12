package com.omni.app.data.ytdlp

import android.content.Context
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoInfo as YtVideoInfo
import com.omni.app.data.download.AvailableFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

object YtDlpManager {

    private const val TAG = "YtDlpManager"
    var isReady = false
        private set

    suspend fun initialize(context: Context, onProgress: (Float) -> Unit = {}): Boolean = withContext(Dispatchers.IO) {
        try {
            FFmpeg.getInstance().init(context)
            YoutubeDL.getInstance().init(context)
            onProgress(100f)
            isReady = true
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize yt-dlp", e)
            false
        }
    }

    data class VideoInfo(
        val title: String?,
        val thumbnailUrl: String?,
        val duration: Long?,
        val viewCount: Long?,
        val uploader: String?,
        val availableFormats: List<AvailableFormat> = emptyList()
    )

    data class SearchResult(
        val id: String,
        val url: String,
        val title: String,
        val thumbnailUrl: String?,
        val duration: Int,
        val uploader: String?,
        val viewCountText: String? = null
    )

    suspend fun searchVideos(query: String, count: Int = 20): List<SearchResult> = withContext(Dispatchers.IO) {
        try {
            val request = YoutubeDLRequest("ytsearch$count:$query")
            request.addOption("--dump-single-json")
            request.addOption("--flat-playlist")
            request.addOption("--no-check-certificate")
            
            val response = YoutubeDL.getInstance().execute(request)
            val json = response.out
            if (json.isBlank()) return@withContext emptyList()

            val root = JSONObject(json)
            val entriesArray = root.optJSONArray("entries") ?: return@withContext emptyList()

            val results = mutableListOf<SearchResult>()
            for (i in 0 until entriesArray.length()) {
                val entry = entriesArray.optJSONObject(i) ?: continue
                val id = entry.optString("id")
                if (id.isEmpty()) continue

                val url = "https://www.youtube.com/watch?v=$id"

                var thumbUrl = if (entry.has("thumbnail")) entry.optString("thumbnail") else null
                if (thumbUrl.isNullOrEmpty() && entry.has("thumbnails")) {
                    val thumbs = entry.optJSONArray("thumbnails")
                    if (thumbs != null && thumbs.length() > 0) {
                        thumbUrl = thumbs.optJSONObject(thumbs.length() - 1)?.optString("url")
                    }
                }
                if (thumbUrl.isNullOrEmpty()) {
                    thumbUrl = "https://i.ytimg.com/vi/$id/mqdefault.jpg"
                }

                results.add(
                    SearchResult(
                        id = id,
                        url = url,
                        title = entry.optString("title", "Unknown"),
                        thumbnailUrl = thumbUrl,
                        duration = entry.optInt("duration", 0),
                        uploader = entry.optString("uploader", "Unknown"),
                        viewCountText = entry.optString("view_count", "")
                    )
                )
            }
            results
        } catch (e: Exception) {
            Log.e(TAG, "Search failed", e)
            emptyList()
        }
    }

    suspend fun fetchVideoInfoWithFormats(url: String, context: Context): VideoInfo? = withContext(Dispatchers.IO) {
        try {
            val request = YoutubeDLRequest(url)
            request.addOption("--no-playlist")
            request.addOption("--no-check-certificate")
            
            val info: YtVideoInfo = YoutubeDL.getInstance().getInfo(request)

            val formats = info.formats?.filter { (it.height ?: 0) > 0 }
                ?.map { f ->
                    val h = f.height ?: 0
                    val fps = f.fps
                    val label = "${h}p" + (if (fps >= 49.0) "60" else "")
                    AvailableFormat(
                        formatId = f.formatId ?: "",
                        height = h,
                        fps = fps.toInt(),
                        ext = f.ext ?: "",
                        filesize = f.fileSize,
                        label = label
                    )
                }
                ?.sortedWith(compareByDescending<AvailableFormat> { it.height }
                    .thenByDescending { it.fps }
                    .thenByDescending { it.filesize ?: 0L })
                ?.distinctBy { it.label }
                ?.sortedByDescending { it.height } ?: emptyList()

            VideoInfo(
                title = info.title,
                thumbnailUrl = info.thumbnail,
                duration = info.duration.toLong(),
                viewCount = info.viewCount?.toLongOrNull() ?: 0L,
                uploader = info.uploader,
                availableFormats = formats
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch video info", e)
            null
        }
    }

    data class PlaylistInfo(
        val title: String,
        val author: String?,
        val videoCount: Int,
        val entries: List<PlaylistItem>
    )

    data class PlaylistItem(
        val url: String,
        val title: String,
        val duration: Int,
        val uploader: String?,
        val thumbnailUrl: String?
    )

    suspend fun fetchPlaylistInfo(url: String, context: Context): PlaylistInfo? = withContext(Dispatchers.IO) {
        try {
            val request = YoutubeDLRequest(url)
            request.addOption("--flat-playlist")
            request.addOption("--dump-single-json")
            request.addOption("--no-check-certificate")

            val response = YoutubeDL.getInstance().execute(request)
            val json = response.out
            if (json.isBlank()) return@withContext null
            val root = JSONObject(json)
            val title = root.optString("title", "Unknown Playlist")
            val uploader = if (root.has("uploader")) root.getString("uploader") else null
            val entriesArray = root.optJSONArray("entries")

            val playlistItems = mutableListOf<PlaylistItem>()
            if (entriesArray != null) {
                for (i in 0 until entriesArray.length()) {
                    val entry = entriesArray.optJSONObject(i) ?: continue
                    val id = entry.optString("id")
                    val entryUrl = entry.optString("url")

                    val fullUrl = when {
                        entryUrl.startsWith("http") -> entryUrl
                        id.isNotEmpty() -> "https://www.youtube.com/watch?v=$id"
                        else -> continue
                    }

                    var thumbUrl = if (entry.has("thumbnail")) entry.optString("thumbnail") else null
                    if (thumbUrl.isNullOrEmpty() && entry.has("thumbnails")) {
                        val thumbs = entry.optJSONArray("thumbnails")
                        if (thumbs != null && thumbs.length() > 0) {
                            thumbUrl = thumbs.optJSONObject(thumbs.length() - 1)?.optString("url")
                        }
                    }

                    if (thumbUrl.isNullOrEmpty() && id.isNotEmpty()) {
                        thumbUrl = "https://i.ytimg.com/vi/$id/mqdefault.jpg"
                    }

                    playlistItems.add(
                        PlaylistItem(
                            url = fullUrl,
                            title = entry.optString("title", "Unknown Video"),
                            duration = entry.optInt("duration", 0),
                            uploader = if (entry.has("uploader")) entry.getString("uploader") else uploader,
                            thumbnailUrl = thumbUrl
                        )
                    )
                }
            }

            PlaylistInfo(
                title = title,
                author = uploader,
                videoCount = playlistItems.size,
                entries = playlistItems
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch playlist info", e)
            null
        }
    }

    suspend fun downloadVideo(
        url: String,
        outputDir: File,
        maxHeight: Int?,
        selectedFormatId: String? = null,
        fps60: Boolean = true,
        outputFormat: String = "mp4",
        onTitle: (String) -> Unit = {},
        onProgress: (Float, String, String) -> Unit = { _, _, _ -> },
        onSuccess: (File) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            outputDir.mkdirs()
            val request = YoutubeDLRequest(url)

            val fmt = when {
                selectedFormatId != null -> "$selectedFormatId+bestaudio/best"
                maxHeight != null -> {
                    val fpsConstraint = if (fps60) "[fps<=60]" else "[fps<=30]"
                    "bestvideo[height<=$maxHeight]$fpsConstraint+bestaudio/best"
                }
                else -> if (fps60) "bestvideo+bestaudio/best" else "bestvideo[fps<=30]+bestaudio/best"
            }

            request.addOption("-f", fmt)
            request.addOption("--merge-output-format", outputFormat)
            request.addOption("--no-playlist")
            request.addOption("--concurrent-fragments", "5")
            request.addOption("--buffer-size", "16K")
            request.addOption("--no-check-certificate")
            request.addOption("-o", "${outputDir.absolutePath}/%(title)s.%(ext)s")

            var downloadedFile: File? = null

            val response = YoutubeDL.getInstance().execute(request) { progress, etaInSeconds, line ->
                onProgress(progress, parseSpeed(line), formatEta(etaInSeconds))
                if (line.contains("[download] Destination:")) {
                    val path = line.substringAfter("Destination: ").trim()
                    downloadedFile = File(path)
                    onTitle(downloadedFile?.nameWithoutExtension ?: "")
                } else if (line.contains("[ffmpeg] Merging formats into \"")) {
                    downloadedFile = File(line.substringAfter("Merging formats into \"").substringBefore("\""))
                } else if (line.contains("has already been downloaded")) {
                    val path = line.substringAfter("[download] ").substringBefore(" has already been downloaded").trim()
                    downloadedFile = File(path)
                }
            }

            val finalFile = downloadedFile ?: if (!response.out.contains("\n") && response.out.contains("/")) File(response.out) else null
            if (finalFile != null) {
                onSuccess(finalFile)
                Result.success(finalFile)
            } else {
                Result.failure(Exception("Could not determine downloaded file path"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadAudio(
        context: Context,
        url: String,
        outputDir: File,
        audioFormat: String = "mp3",
        audioBitrate: String = "0",
        embedThumbnail: Boolean = true,
        onTitle: (String) -> Unit = {},
        onProgress: (Float, String, String) -> Unit = { _, _, _ -> },
        onSuccess: (File) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            outputDir.mkdirs()
            val request = YoutubeDLRequest(url)
            request.addOption("-f", "bestaudio/best")
            request.addOption("--extract-audio")
            request.addOption("--audio-format", audioFormat)
            request.addOption("--audio-quality", audioBitrate)
            if (embedThumbnail) request.addOption("--embed-thumbnail")
            request.addOption("-o", "${outputDir.absolutePath}/%(title)s.%(ext)s")

            var downloadedFile: File? = null
            val response = YoutubeDL.getInstance().execute(request) { progress, etaInSeconds, line ->
                onProgress(progress, parseSpeed(line), formatEta(etaInSeconds))
                if (line.contains("[download] Destination:")) {
                    downloadedFile = File(line.substringAfter("Destination: ").trim())
                } else if (line.contains("[ffmpeg] Post-process file")) {
                    downloadedFile = File(line.substringAfter("[ffmpeg] Post-process file ").trim())
                }
            }

            val finalFile = downloadedFile ?: if (!response.out.contains("\n") && response.out.contains("/")) File(response.out) else null
            if (finalFile != null) {
                onSuccess(finalFile)
                Result.success(finalFile)
            } else {
                Result.failure(Exception("Could not determine downloaded audio file path"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseSpeed(line: String): String = if (line.contains("at")) line.substringAfter("at").trim().split(" ").firstOrNull() ?: "0KiB/s" else "0KiB/s"
    private fun formatEta(seconds: Long): String = if (seconds <= 0) "00:00" else String.format("%02d:%02d", seconds / 60, seconds % 60)
}
