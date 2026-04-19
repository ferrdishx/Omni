package com.omni.app.data.ytdlp

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoInfo as YtVideoInfo
import com.omni.app.data.download.AvailableFormat
import com.omni.app.data.download.DownloadItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

object YtDlpManager {

    private const val TAG = "YtDlpManager"
    var isReady = false
        private set

    private fun YoutubeDLRequest.addSearchOptions() {
        addOption("--no-check-certificate")
        addOption("--no-warnings")
        addOption("--socket-timeout", "20")
        addOption("--no-cache-dir")
    }

    private fun YoutubeDLRequest.addVideoBypassOptions() {
        addOption("--no-check-certificate")
        addOption("--no-warnings")
        addOption("--socket-timeout", "30")
        addOption("--retries", "10")
        addOption("--no-cache-dir")
        addOption("--force-ipv4")

        addOption("--extractor-args", "youtube:player_client=tv,ios,android_vr;player_skip=web,mweb,android")

        addOption("--user-agent", "Mozilla/5.0 (Chromecast; GoogleTV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/118.0.0.0 Safari/537.36")
    }

    suspend fun initialize(context: Context, onProgress: (Float) -> Unit = {}): Boolean = withContext(Dispatchers.IO) {
        try {

            FFmpeg.getInstance().init(context)

            YoutubeDL.getInstance().init(context)

            try {
                val status = YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)
            } catch (updateError: Exception) {
            }

            onProgress(100f)
            isReady = true
            true
        } catch (e: Exception) {

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
            request.addSearchOptions()
            val response = YoutubeDL.getInstance().execute(request)
            val json = response.out

            if (json.isBlank()) {
                return@withContext emptyList()
            }

            val root = JSONObject(json)
            val entriesArray = root.optJSONArray("entries")

            if (entriesArray == null) {
                return@withContext emptyList()
            }

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

            emptyList()
        }
    }

    suspend fun fetchVideoInfoWithFormats(url: String, context: Context): VideoInfo? = withContext(Dispatchers.IO) {
        try {

            val ffmpegDir = File(File(File(context.noBackupFilesDir, "youtubedl-android"), "packages"), "ffmpeg")
            val ffmpegFile = File(ffmpegDir, "ffmpeg")
            val ffmpegPath = if (ffmpegFile.exists()) ffmpegFile.absolutePath else null

            if (ffmpegPath != null) {
            }

            val request = YoutubeDLRequest(url)
            request.addOption("--no-playlist")
            request.addOption("--all-formats")
            request.addVideoBypassOptions()

            if (ffmpegPath != null) request.addOption("--ffmpeg-location", ffmpegPath)
            val info: YtVideoInfo = YoutubeDL.getInstance().getInfo(request)

            val formats = info.formats?.filter {
                (it.height ?: 0) >= 144 && // Remove resoluções irrelevantes (storyboards/thumbnails)
                it.ext != "mhtml" &&
                it.acodec != "none" || it.vcodec != "none"
            }
                ?.map { f ->
                    val h = f.height ?: 0
                    val fpsValue = f.fps?.toDouble() ?: 0.0
                    val label = "${h}p" + (if (fpsValue >= 49.0) "60" else "")

                    AvailableFormat(
                        formatId = f.formatId ?: "",
                        height = h,
                        fps = fpsValue.toInt(),
                        ext = f.ext ?: "",
                        filesize = f.fileSize,
                        label = label
                    )
                }
                ?.sortedWith(compareByDescending<AvailableFormat> { it.height }
                    .thenByDescending { it.fps }
                    .thenByDescending { it.filesize ?: 0L })
                ?.distinctBy { it.label } // Mantém apenas um de cada (ex: um 1080p, um 1080p60)
                ?.sortedByDescending { it.height } ?: emptyList()
            formats.forEach { fmt ->
            }

            VideoInfo(
                title = info.title,
                thumbnailUrl = info.thumbnail,
                duration = info.duration.toLong(),
                viewCount = info.viewCount?.toLongOrNull() ?: 0L,
                uploader = info.uploader,
                availableFormats = formats
            )
        } catch (e: Exception) {

            if (e.message?.contains("LOGIN_REQUIRED") == true || e.message?.contains("Please sign in") == true) {

            } else if (e.message?.contains("could not find chrome cookies database") == true) {

            } else if (e.message?.contains("HTTP Error 403") == true) {

            } else if (e.message?.contains("HTTP Error 429") == true) {

            }

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

            val ffmpegDir = File(File(File(context.noBackupFilesDir, "youtubedl-android"), "packages"), "ffmpeg")
            val ffmpegFile = File(ffmpegDir, "ffmpeg")
            val ffmpegPath = if (ffmpegFile.exists()) ffmpegFile.absolutePath else null

            val request = YoutubeDLRequest(url)
            request.addOption("--flat-playlist")
            request.addOption("--dump-single-json")
            request.addSearchOptions()
            if (ffmpegPath != null) request.addOption("--ffmpeg-location", ffmpegPath)
            val response = YoutubeDL.getInstance().execute(request)
            val json = response.out

            if (json.isBlank()) {
                return@withContext null
            }

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

            null
        }
    }

    suspend fun downloadVideo(
        item: DownloadItem,
        outputDir: File,
        maxHeight: Int?,
        context: Context,
        onTitle: (String) -> Unit = {},
        onProgress: (Float, String, String) -> Unit = { _, _, _ -> },
        onSuccess: (File) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        try {

            outputDir.mkdirs()
            val request = YoutubeDLRequest(item.url)

            val ffmpegDir = File(File(File(context.noBackupFilesDir, "youtubedl-android"), "packages"), "ffmpeg")
            val ffmpegFile = File(ffmpegDir, "ffmpeg")
            val ffmpegPath = if (ffmpegFile.exists()) ffmpegFile.absolutePath else null

            val fmt = when {
                item.selectedFormatId != null ->
                    "${item.selectedFormatId}+bestaudio/bestvideo+bestaudio/best"
                maxHeight != null -> {
                    if (item.prefer60fps)
                        "bestvideo[height<=$maxHeight][fps<=60]+bestaudio/bestvideo[height<=$maxHeight]+bestaudio/best[height<=$maxHeight]/best"
                    else
                        "bestvideo[height<=$maxHeight]+bestaudio/best[height<=$maxHeight]/best"
                }
                else ->
                    if (item.prefer60fps) "bestvideo[fps<=60]+bestaudio/bestvideo+bestaudio/best"
                    else "bestvideo+bestaudio/best"
            }

            request.addOption("-f", fmt)
            request.addOption("--merge-output-format", item.format.lowercase())
            request.addOption("--no-playlist")
            request.addVideoBypassOptions()
            if (ffmpegPath != null) request.addOption("--ffmpeg-location", ffmpegPath)

            applyAdvancedOptions(request, item, isAudio = false)

            request.addOption("-o", "${outputDir.absolutePath}/${item.outputTemplate}")

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
                } else if (line.contains("ERROR") || line.contains("error")) {
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
        item: DownloadItem,
        outputDir: File,
        onTitle: (String) -> Unit = {},
        onProgress: (Float, String, String) -> Unit = { _, _, _ -> },
        onSuccess: (File) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        try {

            outputDir.mkdirs()
            val request = YoutubeDLRequest(item.url)
            val ffmpegDir = File(File(File(context.noBackupFilesDir, "youtubedl-android"), "packages"), "ffmpeg")
            val ffmpegFile = File(ffmpegDir, "ffmpeg")
            val ffmpegPath = if (ffmpegFile.exists()) ffmpegFile.absolutePath else null

            request.addOption("-f", "bestaudio/best")
            request.addOption("--extract-audio")
            request.addOption("--audio-format", item.format.lowercase().ifBlank { "mp3" })

            val bitrate = item.quality
                .replace("kbps", "").replace("K", "").trim()
                .takeIf { it.all { c -> c.isDigit() } } ?: "0"
            if (bitrate != "0") request.addOption("--audio-quality", bitrate)

            request.addOption("--no-playlist")
            request.addVideoBypassOptions()
            if (ffmpegPath != null) request.addOption("--ffmpeg-location", ffmpegPath)
            if (item.embedThumbnail) {
                request.addOption("--embed-thumbnail")
                request.addOption("--convert-thumbnails", "jpg")
            }

            applyAdvancedOptions(request, item, isAudio = true)

            request.addOption("-o", "${outputDir.absolutePath}/${item.outputTemplate}")

            var downloadedFile: File? = null
            val response = YoutubeDL.getInstance().execute(request) { progress, etaInSeconds, line ->
                onProgress(progress, parseSpeed(line), formatEta(etaInSeconds))

                if (line.contains("[download] Destination:")) {
                    downloadedFile = File(line.substringAfter("Destination: ").trim())
                } else if (line.contains("[ffmpeg] Post-process file")) {
                    downloadedFile = File(line.substringAfter("[ffmpeg] Post-process file ").trim())
                } else if (line.contains("ERROR") || line.contains("error")) {
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

    private fun applyAdvancedOptions(request: YoutubeDLRequest, item: DownloadItem, isAudio: Boolean) {

        if (item.speedLimit.isNotBlank()) {
            request.addOption("--rate-limit", item.speedLimit)
        }

        if (item.proxy.isNotBlank()) {
            request.addOption("--proxy", item.proxy)
        }

        request.addOption("--concurrent-fragments", item.maxFragments)

        if (item.cookieSource != com.omni.app.data.download.CookieSource.NONE) {
            val browser = when (item.cookieSource) {
                com.omni.app.data.download.CookieSource.CHROME -> "chrome"
                com.omni.app.data.download.CookieSource.FIREFOX -> "firefox"
                com.omni.app.data.download.CookieSource.EDGE -> "edge"
                com.omni.app.data.download.CookieSource.BRAVE -> "brave"
                else -> null
            }
            if (browser != null) {
                request.addOption("--cookies-from-browser", browser)
            }
        }

        if (item.embedSubtitles && !isAudio) {
            request.addOption("--write-subs")
            if (item.autoGeneratedSubtitles) {
                request.addOption("--write-auto-subs")
            }
            request.addOption("--sub-langs", item.subtitleLanguage)
            request.addOption("--convert-subs", "srt")

            request.addOption("--embed-subs")
        }

        if (item.embedChapters) {
            request.addOption("--embed-chapters")
        }
        if (item.splitByChapters) {
            request.addOption("--split-chapters")
        }

        if (item.writeMetadata) {
            request.addOption("--add-metadata")
        }

        if (item.sponsorBlockEnabled && !isAudio) {
            val categories = item.sponsorBlockCategories.joinToString(",")
            if (item.sponsorBlockAction == com.omni.app.data.download.SponsorBlockAction.REMOVE) {
                request.addOption("--sponsorblock-remove", categories)
            } else {
                request.addOption("--sponsorblock-mark", categories)
            }
        }

        if (item.startTime.isNotBlank() || item.endTime.isNotBlank()) {
            val start = item.startTime.ifBlank { "00:00:00" }
            val end = item.endTime.ifBlank { "99:59:59" }
            request.addOption("--download-sections", "*$start-$end")
        }

        val filters = mutableListOf<String>()
        if (item.normalizeAudio) {
            filters.add("loudnorm")
        }
        if (item.trimSilence) {
            filters.add("silenceremove=1:0:-50dB")
        }
        if (filters.isNotEmpty()) {
            request.addOption("--postprocessor-args", "ffmpeg:-af ${filters.joinToString(",")}")
        }

        if (item.customArgs.isNotBlank()) {
            item.customArgs.split(" ").filter { it.isNotBlank() }.forEach {
                request.addOption(it)
            }
        }
    }

    private fun parseSpeed(line: String): String = if (line.contains("at")) line.substringAfter("at").trim().split(" ").firstOrNull() ?: "0KiB/s" else "0KiB/s"
    private fun formatEta(seconds: Long): String = if (seconds <= 0) "00:00" else String.format("%02d:%02d", seconds / 60, seconds % 60)
}
