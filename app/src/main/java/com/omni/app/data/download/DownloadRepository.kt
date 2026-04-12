package com.omni.app.data.download

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.util.Log
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.omni.app.data.prefs.UserPreferences
import com.omni.app.data.ytdlp.YtDlpManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.io.FileOutputStream

class DownloadRepository(private val context: Context) {

    private val database = AppDatabase.getDatabase(context)
    private val downloadDao = database.downloadDao()
    private val notificationHelper = DownloadNotificationHelper(context)
    private val userPreferences = UserPreferences(context)

    private val _downloads = MutableStateFlow<List<DownloadItem>>(emptyList())
    val downloads: StateFlow<List<DownloadItem>> = _downloads.asStateFlow()

    // Flow for completed media from Room
    val completedMedia = downloadDao.getAllMedia()

    suspend fun insertMedia(media: DownloadedMedia) {
        downloadDao.insert(media)
        MediaScannerConnection.scanFile(context, arrayOf(media.filePath), null, null)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = mutableMapOf<String, Job>()

    private val _ytdlpProgress = MutableStateFlow<Float?>(null)
    val ytdlpSetupProgress: StateFlow<Float?> = _ytdlpProgress.asStateFlow()

    fun ensureYtDlp() {
        if (YtDlpManager.isReady) return
        scope.launch {
            YtDlpManager.initialize(context) { _ytdlpProgress.value = it }
            _ytdlpProgress.value = null
        }
    }

    private suspend fun downloadThumbnail(url: String?, targetFile: File): String? {
        if (url.isNullOrBlank()) return null
        return withContext(Dispatchers.IO) {
            try {
                targetFile.parentFile?.mkdirs()
                
                val loader = ImageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .allowHardware(false)
                    .build()
                val result = loader.execute(request)
                if (result is SuccessResult) {
                    val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                    if (bitmap != null) {
                        FileOutputStream(targetFile).use { out ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                        }
                        return@withContext targetFile.absolutePath
                    }
                }
                null
            } catch (e: Exception) {
                null
            }
        }
    }

    fun enqueue(item: DownloadItem) {
        val list = _downloads.value.toMutableList()
        // Check if item already exists to avoid duplicates
        if (list.any { it.id == item.id }) return
        
        list.add(0, item)
        _downloads.value = list
        processQueue()
    }

    private fun processQueue() {
        scope.launch {
            val prefs = userPreferences.preferences.first()
            val maxConcurrent = prefs.concurrentDownloads

            val currentActive = activeJobs.size
            if (currentActive >= maxConcurrent) return@launch

            val nextInQueue = _downloads.value
                .filter { it.status == DownloadStatus.QUEUED }
                .sortedBy { it.addedAt }
                .take(maxConcurrent - currentActive)

            nextInQueue.forEach { item ->
                startDownload(item)
            }
        }
    }

    private fun update(id: String, transform: DownloadItem.() -> DownloadItem) {
        _downloads.value = _downloads.value.map { if (it.id == id) it.transform() else it }
    }

    private fun startDownload(item: DownloadItem) {
        val job = scope.launch {
            try {
                var currentTitle = item.title
                val prefs = userPreferences.preferences.first()
                
                val baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val customPath = prefs.downloadPath.removePrefix("primary:").trim('/')
                val omniDir = if (customPath.isEmpty() || customPath == "Downloads/Omni") {
                    File(baseDir, "Omni")
                } else {
                    File(Environment.getExternalStorageDirectory(), customPath)
                }

                val videosDir = File(omniDir, "Videos").apply { if (!exists()) mkdirs() }
                val musicsDir = File(omniDir, "Musics").apply { if (!exists()) mkdirs() }
                val thumbAudioDir = File(omniDir, ".thumbaudio").apply { if (!exists()) mkdirs() }

                val outputDir = if (item.type == DownloadType.VIDEO) videosDir else musicsDir

                if (!YtDlpManager.isReady) {
                    update(item.id) { copy(status = DownloadStatus.FETCHING_INFO) }
                    val ok = YtDlpManager.initialize(context) { _ytdlpProgress.value = it }
                    _ytdlpProgress.value = null
                    if (!ok) {
                        update(item.id) { copy(status = DownloadStatus.FAILED, errorMessage = "Engine failure") }
                        notificationHelper.showFailedNotification(item.id, item.title, "Engine failure")
                        return@launch
                    }
                }

                update(item.id) { copy(status = DownloadStatus.DOWNLOADING, progress = 0f) }

                val result = when (item.type) {
                    DownloadType.VIDEO -> {
                        update(item.id) { copy(title = item.title, thumbnailUrl = item.thumbnailUrl) }
                        
                        val h = item.quality.filter { it.isDigit() }.toIntOrNull()

                        YtDlpManager.downloadVideo(
                            url          = item.url,
                            outputDir    = outputDir,
                            maxHeight    = h,
                            selectedFormatId = item.selectedFormatId,
                            fps60        = item.prefer60fps,
                            outputFormat = item.format.lowercase(),
                            onTitle      = { title -> 
                                currentTitle = title
                                update(item.id) { copy(title = title) } 
                            },
                            onProgress   = { pct, spd, eta ->
                                update(item.id) { copy(progress = pct, speed = spd, eta = eta) }
                                notificationHelper.showProgressNotification(item.id, item.title, pct.toInt(), spd, item.thumbnailUrl)
                            },
                            onSuccess = { file ->
                                scope.launch {
                                    val media = DownloadedMedia(
                                        id = item.id,
                                        title = currentTitle,
                                        filePath = file.absolutePath,
                                        thumbnailUrl = file.absolutePath,
                                        isAudio = false,
                                        format = item.format,
                                        timestamp = System.currentTimeMillis()
                                    )
                                    insertMedia(media)
                                    notificationHelper.showCompletedNotification(item.id, item.title)
                                }
                            }
                        )
                    }

                    DownloadType.AUDIO -> {
                        update(item.id) { copy(title = item.title, thumbnailUrl = item.thumbnailUrl) }

                        YtDlpManager.downloadAudio(
                            context        = context,
                            url            = item.url,
                            outputDir      = outputDir,
                            audioFormat    = "mp3",
                            audioBitrate   = "0",
                            embedThumbnail = item.embedThumbnail,
                            onTitle        = { title -> 
                                if (currentTitle != title) {
                                    currentTitle = title
                                    update(item.id) { copy(title = title) }
                                }
                            },
                            onProgress     = { pct, spd, eta ->
                                update(item.id) {
                                    val status = if (pct >= 100f) DownloadStatus.CONVERTING else DownloadStatus.DOWNLOADING
                                    copy(progress = pct, speed = spd, eta = eta, status = status)
                                }
                                notificationHelper.showProgressNotification(item.id, item.title, pct.toInt(), spd, item.thumbnailUrl)
                            },
                            onSuccess = { file ->
                                scope.launch {
                                    thumbAudioDir.mkdirs()
                                    
                                    val thumbFile = File(thumbAudioDir, "${file.nameWithoutExtension}.jpg")
                                    val localThumbPath = downloadThumbnail(item.thumbnailUrl, thumbFile)

                                    val media = DownloadedMedia(
                                        id = item.id,
                                        title = currentTitle,
                                        filePath = file.absolutePath,
                                        thumbnailUrl = localThumbPath ?: item.thumbnailUrl,
                                        isAudio = true,
                                        format = item.format,
                                        timestamp = System.currentTimeMillis()
                                    )
                                    insertMedia(media)
                                    notificationHelper.showCompletedNotification(item.id, item.title)
                                }
                            }
                        )
                    }
                }

                if (result.isSuccess) {
                    update(item.id) {
                        copy(
                            status   = DownloadStatus.COMPLETED,
                            progress = 100f,
                            speed    = "",
                            eta      = "",
                            outputPath = result.getOrNull()?.absolutePath
                        )
                    }
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Unknown error"
                    notificationHelper.showFailedNotification(item.id, item.title, error)
                    update(item.id) { copy(status = DownloadStatus.FAILED, errorMessage = error) }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("OmniDebug", "Critical download error", e)
                notificationHelper.showFailedNotification(item.id, item.title, e.message)
                update(item.id) { copy(status = DownloadStatus.FAILED, errorMessage = e.message) }
            } finally {
                activeJobs.remove(item.id)
                processQueue()
            }
        }
        activeJobs[item.id] = job
    }

    fun cancel(id: String) {
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        update(id) { copy(status = DownloadStatus.CANCELLED) }
        processQueue()
    }

    fun retry(id: String) {
        update(id) { copy(status = DownloadStatus.QUEUED, progress = 0f, errorMessage = null) }
        processQueue()
    }

    fun remove(id: String) {
        cancel(id)
        _downloads.value = _downloads.value.filter { it.id != id }
    }

    suspend fun deleteMedia(item: DownloadedMedia) {
        withContext(Dispatchers.IO) {
            downloadDao.deleteById(item.id)
            val file = File(item.filePath)
            if (file.exists()) file.delete()
            
            // Delete local thumbnail too if it exists
            item.thumbnailUrl?.let { 
                if (it.startsWith("/")) {
                    val thumbFile = File(it)
                    if (thumbFile.exists()) thumbFile.delete()
                }
            }

            MediaScannerConnection.scanFile(context, arrayOf(item.filePath), null, null)
        }
    }
}
