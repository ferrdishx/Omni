package com.omni.app.ui.player

import android.app.Application
import android.net.Uri
import java.io.File
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import com.omni.app.data.download.DownloadEntity
import com.omni.app.data.download.DownloadedMedia
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class OmniPlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val playerController = OmniPlayerController(application)

    val mediaController: StateFlow<Player?> = playerController.mediaController
    val currentMediaItem: StateFlow<MediaItem?> = playerController.currentMediaItem
    val isPlaying: StateFlow<Boolean> = playerController.isPlaying
    val playbackState: StateFlow<Int> = playerController.playbackState
    val currentPosition: StateFlow<Long> = playerController.currentPosition
    val duration: StateFlow<Long> = playerController.duration
    val shuffleModeEnabled: StateFlow<Boolean> = playerController.shuffleModeEnabled
    val repeatMode: StateFlow<Int> = playerController.repeatMode
    val audioFormat: StateFlow<String?> = playerController.audioFormat
    val bitrate: StateFlow<Int> = playerController.bitrate
    val sampleRate: StateFlow<Int> = playerController.sampleRate
    val sleepTimerRemaining: StateFlow<Long?> = playerController.sleepTimerRemaining

    init {
        viewModelScope.launch {
            while (true) {
                playerController.updateProgress()
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

    private fun createMediaItem(download: DownloadEntity): MediaItem {
        val file = File(download.filePath)
        val uri = Uri.fromFile(file)

        val thumbUri = download.thumbnail?.let {
            when {
                it.startsWith("http") -> Uri.parse(it)
                it.isNotEmpty() -> Uri.fromFile(File(it))
                else -> null
            }
        } ?: if (download.type == "video") uri else null

        return MediaItem.Builder()
            .setMediaId(download.id)
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(download.title)
                    .setArtist(if (download.type == "audio") "Audio" else "Video")
                    .setArtworkUri(thumbUri)
                    .setDisplayTitle(download.title)
                    .setIsPlayable(true)
                    .setMediaType(if (download.type == "audio") MediaMetadata.MEDIA_TYPE_MUSIC else MediaMetadata.MEDIA_TYPE_VIDEO)
                    .build()
            )
            .build()
    }

    private fun createMediaItem(download: DownloadedMedia): MediaItem {
        val file = File(download.filePath)
        val uri = Uri.fromFile(file)

        val thumbUri = download.thumbnailUrl?.let {
            when {
                it.startsWith("http") -> Uri.parse(it)
                it.isNotEmpty() -> Uri.fromFile(File(it))
                else -> null
            }
        } ?: if (!download.isAudio) uri else null

        return MediaItem.Builder()
            .setMediaId(download.id)
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(download.title)
                    .setArtist(if (download.isAudio) "Audio" else "Video")
                    .setArtworkUri(thumbUri)
                    .setDisplayTitle(download.title)
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
        playerController.setPlaybackSpeed(speed)
    }

    fun seekTo(positionMs: Long) {
        playerController.seekTo(positionMs)
    }

    fun getQueue(): List<MediaItem> = playerController.getQueue()

    fun skipToQueueItem(index: Int) {
        playerController.skipToQueueItem(index)
    }

    fun startSleepTimer(minutes: Int) {
        playerController.startSleepTimer(minutes)
    }

    fun stopSleepTimer() {
        playerController.stopSleepTimer()
    }

    override fun onCleared() {
        super.onCleared()
        playerController.release()
    }
}
