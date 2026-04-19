package com.omni.app.ui.player

import android.app.Application
import android.net.Uri
import android.os.Environment
import java.io.File
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import com.omni.app.data.download.DownloadEntity
import com.omni.app.data.download.DownloadedMedia
import com.omni.app.data.download.AppDatabase
import com.omni.app.data.favorites.FavoriteItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class OmniPlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val playerController = OmniPlayerController(application)
    private val favoriteDao = AppDatabase.getDatabase(application).favoriteDao()
    private val downloadDao = AppDatabase.getDatabase(application).downloadDao()

    val mediaController: StateFlow<Player?> = playerController.mediaController
    val currentMediaItem: StateFlow<MediaItem?> = playerController.currentMediaItem
    val mediaMetadata: StateFlow<MediaMetadata> = playerController.mediaMetadata
    
    @OptIn(ExperimentalCoroutinesApi::class)
    val isFavorite: StateFlow<Boolean> = currentMediaItem
        .flatMapLatest { item ->
            if (item != null) favoriteDao.isFavorite(item.mediaId)
            else flowOf(false)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val isPlaying: StateFlow<Boolean> = playerController.isPlaying
    val playbackState: StateFlow<Int> = playerController.playbackState
    val currentPosition: StateFlow<Long> = playerController.currentPosition
    val duration: StateFlow<Long> = playerController.duration
    val shuffleModeEnabled: StateFlow<Boolean> = playerController.shuffleModeEnabled
    val repeatMode: StateFlow<Int> = playerController.repeatMode
    val audioFormat: StateFlow<String?> = playerController.audioFormat
    val bitrate: StateFlow<Int> = playerController.bitrate
    val sampleRate: StateFlow<Int> = playerController.sampleRate
    val videoAspectRatio: StateFlow<Float> = playerController.videoAspectRatio
    val sleepTimerRemaining: StateFlow<Long?> = playerController.sleepTimerRemaining

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _playbackPitch = MutableStateFlow(1.0f)
    val playbackPitch: StateFlow<Float> = _playbackPitch.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) {
                playerController.updateProgress()
                _playbackSpeed.value = playerController.mediaController.value?.playbackParameters?.speed ?: 1.0f
                _playbackPitch.value = playerController.mediaController.value?.playbackParameters?.pitch ?: 1.0f
                delay(500)
            }
        }
    }

    fun play(download: DownloadEntity) {
        val mediaItem = createMediaItem(download)
        playerController.play(listOf(mediaItem))
    }

    fun play(download: DownloadedMedia) {
        val mediaItem = createMediaItem(download)
        playerController.play(listOf(mediaItem))
    }

    fun playAll(downloads: List<DownloadEntity>, startIndex: Int = 0) {
        val mediaItems = downloads.map { createMediaItem(it) }
        playerController.play(mediaItems, startIndex)
    }

    fun playAllMedia(downloads: List<DownloadedMedia>, startIndex: Int = 0) {
        val mediaItems = downloads.map { createMediaItem(it) }
        playerController.play(mediaItems, startIndex)
    }

    private fun resolveThumbnail(filePath: String, providedThumb: String?): Uri? {
        if (providedThumb != null && providedThumb.startsWith("http")) {
            return Uri.parse(providedThumb)
        }

        if (providedThumb != null && providedThumb.isNotEmpty()) {
            val thumbFile = File(providedThumb)
            if (thumbFile.exists()) return Uri.fromFile(thumbFile)
        }

        val mediaFile = File(filePath)
        val nameNoExt = mediaFile.nameWithoutExtension
        val parentDir = mediaFile.parentFile
        val omniDir = parentDir?.parentFile
        val thumbAudioDir = File(omniDir, ".thumbaudio")

        val extraThumb = File(thumbAudioDir, "$nameNoExt.jpg").takeIf { it.exists() }
            ?: File(parentDir, "$nameNoExt.jpg").takeIf { it.exists() }
            ?: File(parentDir, "$nameNoExt.png").takeIf { it.exists() }

        if (extraThumb != null) return Uri.fromFile(extraThumb)

        return Uri.fromFile(mediaFile)
    }

    private fun createMediaItem(download: DownloadEntity): MediaItem {
        val file = File(download.filePath)
        val uri = Uri.fromFile(file)
        val thumbUri = resolveThumbnail(download.filePath, download.thumbnail)

        val cleanTitle = download.title.substringBeforeLast('.')

        return MediaItem.Builder()
            .setMediaId(download.id)
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(cleanTitle)
                    .setArtist(download.author.takeIf { it != "Unknown" && it != "Local File" && it != "Audio" && it != "Video" })
                    .setAlbumArtist(download.author.takeIf { it != "Unknown" && it != "Local File" && it != "Audio" && it != "Video" })
                    .setAlbumTitle(cleanTitle)
                    .setMediaType(androidx.media3.common.MediaMetadata.MEDIA_TYPE_MUSIC)
                    .setExtras(android.os.Bundle().apply {
                        putString("omni_author", download.author)
                    })
                    .setArtworkUri(thumbUri)
                    .setDisplayTitle(cleanTitle)
                    .setIsPlayable(true)
                    .setMediaType(if (download.type == "audio") MediaMetadata.MEDIA_TYPE_MUSIC else MediaMetadata.MEDIA_TYPE_VIDEO)
                    .build()
            )
            .build()
    }

    private fun createMediaItem(download: DownloadedMedia): MediaItem {
        val file = File(download.filePath)
        val uri = Uri.fromFile(file)
        val thumbUri = resolveThumbnail(download.filePath, download.thumbnailUrl)

        val cleanTitle = download.title.substringBeforeLast('.')

        return MediaItem.Builder()
            .setMediaId(download.id)
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(cleanTitle)
                    .setArtist(download.author.takeIf { it != "Unknown" && it != "Local File" && it != "Audio" && it != "Video" })
                    .setAlbumArtist(download.author.takeIf { it != "Unknown" && it != "Local File" && it != "Audio" && it != "Video" })
                    .setAlbumTitle(cleanTitle)
                    .setMediaType(androidx.media3.common.MediaMetadata.MEDIA_TYPE_MUSIC)
                    .setExtras(android.os.Bundle().apply {
                        putString("omni_author", download.author)
                    })
                    .setArtworkUri(thumbUri)
                    .setDisplayTitle(cleanTitle)
                    .setIsPlayable(true)
                    .setMediaType(if (download.isAudio) MediaMetadata.MEDIA_TYPE_MUSIC else MediaMetadata.MEDIA_TYPE_VIDEO)
                    .build()
            )
            .build()
    }

    fun playPause() {
        playerController.playPause()
    }

    fun playNext() {
        playerController.playNext()
    }

    fun playPrevious() {
        playerController.playPrevious()
    }

    fun toggleShuffle() {
        playerController.toggleShuffle()
    }

    fun toggleRepeatMode() {
        val nextMode = when (playerController.repeatMode.value) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        playerController.setRepeatMode(nextMode)
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
        playerController.setPlaybackSpeed(speed)
    }

    fun setPitch(pitch: Float) {
        _playbackPitch.value = pitch
        playerController.setPitch(pitch)
    }

    fun seekTo(positionMs: Long) {
        playerController.seekTo(positionMs)
    }

    fun getQueue(): List<MediaItem> = playerController.getQueue()

    fun skipToQueueItem(index: Int) {
        playerController.skipToQueueItem(index)
    }

    fun addToQueue(media: DownloadedMedia) {
        val mediaItem = createMediaItem(media)
        playerController.mediaController.value?.addMediaItem(mediaItem)
    }

    fun getLibraryMedia(): Flow<List<DownloadedMedia>> = downloadDao.getAllMedia().map { dbList ->
        val baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val omniDir = File(baseDir, "Omni")
        if (!omniDir.exists()) return@map dbList

        val physicalFiles = omniDir.walkTopDown().filter { file ->
            file.isFile && file.extension.lowercase() in listOf("mp4", "mp3", "m4a", "wav", "flac", "ogg", "mkv", "webm")
        }.toList()

        physicalFiles.map { file ->
            val dbMatch = dbList.find { it.filePath == file.absolutePath || File(it.filePath).name == file.name }
            if (dbMatch != null) {
                dbMatch
            } else {
                val nameNoExt = file.nameWithoutExtension
                val thumbAudioDir = File(omniDir, ".thumbaudio")
                
                val localThumb = File(file.parent, "$nameNoExt.jpg").takeIf { it.exists() }
                    ?: File(thumbAudioDir, "$nameNoExt.jpg").takeIf { it.exists() }

                DownloadedMedia(
                    id = file.absolutePath,
                    title = file.nameWithoutExtension.substringBeforeLast('.'),
                    filePath = file.absolutePath,
                    thumbnailUrl = localThumb?.absolutePath ?: file.absolutePath,
                    isAudio = file.extension.lowercase() in listOf("mp3", "m4a", "wav", "flac", "ogg"),
                    author = "Local File",
                    format = file.extension.uppercase(),
                    timestamp = file.lastModified()
                )
            }
        }.sortedByDescending { it.timestamp }
    }.flowOn(Dispatchers.IO)

    fun toggleFavorite() {
        val item = currentMediaItem.value ?: return
        viewModelScope.launch {
            if (isFavorite.value) {
                favoriteDao.deleteById(item.mediaId)
            } else {
                val downloadedMedia = downloadDao.getMediaById(item.mediaId)
                if (downloadedMedia != null) {
                    favoriteDao.insert(
                        FavoriteItem(
                            id = downloadedMedia.id,
                            url = "", 
                            title = downloadedMedia.title,
                            author = downloadedMedia.author,
                            thumbnailUrl = downloadedMedia.thumbnailUrl,
                            isAudio = downloadedMedia.isAudio,
                            format = downloadedMedia.format,
                            filePath = downloadedMedia.filePath,
                            website = ""
                        )
                    )
                } else {
                    val metadata = item.mediaMetadata
                    favoriteDao.insert(
                        FavoriteItem(
                            id = item.mediaId,
                            url = "",
                            title = metadata.title?.toString() ?: "Unknown",
                            author = metadata.artist?.toString() ?: "Unknown",
                            thumbnailUrl = metadata.artworkUri?.toString(),
                            isAudio = metadata.mediaType == MediaMetadata.MEDIA_TYPE_MUSIC,
                            format = "Unknown",
                            filePath = item.localConfiguration?.uri?.path ?: "",
                            website = ""
                        )
                    )
                }
            }
        }
    }

    fun stopSleepTimer() {
        playerController.stopSleepTimer()
    }

    fun startSleepTimer(minutes: Int) {
        playerController.startSleepTimer(minutes)
    }

    override fun onCleared() {
        super.onCleared()
        playerController.release()
    }
}
