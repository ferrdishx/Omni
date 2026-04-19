package com.omni.app.ui.player

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class OmniPlayerController(private val context: Context) {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val _mediaController = MutableStateFlow<MediaController?>(null)
    val mediaController: StateFlow<MediaController?> = _mediaController.asStateFlow()

    private val _currentMediaItem = MutableStateFlow<MediaItem?>(null)
    val currentMediaItem: StateFlow<MediaItem?> = _currentMediaItem.asStateFlow()

    private val _mediaMetadata = MutableStateFlow(androidx.media3.common.MediaMetadata.EMPTY)
    val mediaMetadata: StateFlow<androidx.media3.common.MediaMetadata> = _mediaMetadata.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playbackState = MutableStateFlow(Player.STATE_IDLE)
    val playbackState: StateFlow<Int> = _playbackState.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _shuffleModeEnabled = MutableStateFlow(false)
    val shuffleModeEnabled: StateFlow<Boolean> = _shuffleModeEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _audioFormat = MutableStateFlow<String?>(null)
    val audioFormat: StateFlow<String?> = _audioFormat.asStateFlow()

    private val _bitrate = MutableStateFlow<Int>(0)
    val bitrate: StateFlow<Int> = _bitrate.asStateFlow()

    private val _sampleRate = MutableStateFlow<Int>(0)
    val sampleRate: StateFlow<Int> = _sampleRate.asStateFlow()

    private val _videoAspectRatio = MutableStateFlow(1f)
    val videoAspectRatio: StateFlow<Float> = _videoAspectRatio.asStateFlow()

    private val _sleepTimerRemaining = MutableStateFlow<Long?>(null)
    val sleepTimerRemaining: StateFlow<Long?> = _sleepTimerRemaining.asStateFlow()

    private var sleepTimerJob: kotlinx.coroutines.Job? = null
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.SupervisorJob())

    init {
        val sessionToken = SessionToken(context, ComponentName(context, OmniPlayerService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            val controller = controllerFuture?.get()
            _mediaController.value = controller
            controller?.addListener(object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    _currentMediaItem.value = mediaItem
                    _duration.value = controller.duration.coerceAtLeast(0L)
                    _mediaMetadata.value = controller.mediaMetadata
                }

                override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
                    _mediaMetadata.value = mediaMetadata
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _isPlaying.value = isPlaying
                    if (isPlaying) {
                        startProgressUpdate()
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    _playbackState.value = playbackState
                    if (playbackState == Player.STATE_READY) {
                        _duration.value = controller.duration.coerceAtLeast(0L)
                    }
                }

                override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                    _shuffleModeEnabled.value = shuffleModeEnabled
                }

                override fun onRepeatModeChanged(repeatMode: Int) {
                    _repeatMode.value = repeatMode
                }

                override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                    updateAudioFormat(controller)
                }

                override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                    if (videoSize.width > 0 && videoSize.height > 0) {
                        _videoAspectRatio.value = videoSize.width.toFloat() / videoSize.height.toFloat()
                    }
                }
            })
            _currentMediaItem.value = controller?.currentMediaItem
            _mediaMetadata.value = controller?.mediaMetadata ?: androidx.media3.common.MediaMetadata.EMPTY
            updateAudioFormat(controller)
            _isPlaying.value = controller?.isPlaying ?: false
            _playbackState.value = controller?.playbackState ?: Player.STATE_IDLE
            _duration.value = controller?.duration?.coerceAtLeast(0L) ?: 0L
            _shuffleModeEnabled.value = controller?.shuffleModeEnabled ?: false
            _repeatMode.value = controller?.repeatMode ?: Player.REPEAT_MODE_OFF
            
            if (controller?.isPlaying == true) {
                startProgressUpdate()
            }
        }, MoreExecutors.directExecutor())
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun updateAudioFormat(controller: Player?) {
        val format = controller?.currentTracks?.groups
            ?.filter { it.type == androidx.media3.common.C.TRACK_TYPE_AUDIO }
            ?.firstOrNull { it.isSelected }
            ?.getTrackFormat(0)

        format?.let {
            _audioFormat.value = when {
                it.sampleMimeType?.contains("mpeg") == true -> "MP3"
                it.sampleMimeType?.contains("aac") == true -> "AAC"
                it.sampleMimeType?.contains("flac") == true -> "FLAC"
                it.sampleMimeType?.contains("opus") == true -> "OPUS"
                it.sampleMimeType?.contains("vorbis") == true -> "OGG"
                else -> it.sampleMimeType?.split("/")?.lastOrNull()?.uppercase() ?: "UNK"
            }
            _bitrate.value = it.bitrate
            _sampleRate.value = it.sampleRate
        }
    }

    private fun startProgressUpdate() {
        val controller = _mediaController.value ?: return
        _currentPosition.value = controller.currentPosition
    }

    fun updateProgress() {
        _mediaController.value?.let {
            _currentPosition.value = it.currentPosition
            _duration.value = it.duration.coerceAtLeast(0L)
        }
    }

    fun play(mediaItems: List<MediaItem>, startIndex: Int = 0) {
        _mediaController.value?.let {
            it.setMediaItems(mediaItems)
            it.prepare()
            if (startIndex >= 0 && startIndex < it.mediaItemCount) {
                it.seekTo(startIndex, 0L)
            }
            it.play()
        }
    }

    fun playPause() {
        _mediaController.value?.let {
            if (it.isPlaying) {
                it.pause()
            } else {
                it.play()
            }
        }
    }

    fun playNext() {
        _mediaController.value?.seekToNextMediaItem()
    }

    fun playPrevious() {
        _mediaController.value?.seekToPreviousMediaItem()
    }

    fun toggleShuffle() {
        _mediaController.value?.let {
            val nextMode = !it.shuffleModeEnabled
            it.shuffleModeEnabled = nextMode
            // Força o estado a atualizar para o UI
            _shuffleModeEnabled.value = nextMode
        }
    }

    fun setRepeatMode(mode: Int) {
        _mediaController.value?.repeatMode = mode
    }

    fun getQueue(): List<MediaItem> {
        val controller = _mediaController.value ?: return emptyList()
        val items = mutableListOf<MediaItem>()
        for (i in 0 until controller.mediaItemCount) {
            items.add(controller.getMediaItemAt(i))
        }
        return items
    }

    fun skipToQueueItem(index: Int) {
        _mediaController.value?.seekTo(index, 0L)
    }

    fun setPlaybackSpeed(speed: Float) {
        _mediaController.value?.let {
            val currentPitch = it.playbackParameters.pitch
            it.playbackParameters = androidx.media3.common.PlaybackParameters(speed, currentPitch)
        }
    }

    fun setPitch(pitch: Float) {
        _mediaController.value?.let {
            val currentSpeed = it.playbackParameters.speed
            it.playbackParameters = androidx.media3.common.PlaybackParameters(currentSpeed, pitch)
        }
    }

    fun seekTo(positionMs: Long) {
        _mediaController.value?.seekTo(positionMs)
    }

    fun startSleepTimer(minutes: Int) {
        stopSleepTimer()
        if (minutes <= 0) return

        val totalMs = minutes * 60 * 1000L
        val endTime = System.currentTimeMillis() + totalMs
        
        sleepTimerJob = scope.launch {
            while (System.currentTimeMillis() < endTime) {
                _sleepTimerRemaining.value = (endTime - System.currentTimeMillis()).coerceAtLeast(0L)
                delay(1000)
            }
            _sleepTimerRemaining.value = null
            _mediaController.value?.pause()
        }
    }

    fun stopSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _sleepTimerRemaining.value = null
    }

    fun release() {
        stopSleepTimer()
        controllerFuture?.let {
            MediaController.releaseFuture(it)
        }
    }
}
