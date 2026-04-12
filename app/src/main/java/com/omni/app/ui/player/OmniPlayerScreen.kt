package com.omni.app.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asComposeRenderEffect
import android.graphics.RenderEffect as AndroidRenderEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.omni.app.data.prefs.OmniPreferences
import com.omni.app.data.prefs.UserPreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OmniPlayerScreen(
    viewModel: OmniPlayerViewModel,
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val mediaController by viewModel.mediaController.collectAsStateWithLifecycle()
    val currentMediaItem by viewModel.currentMediaItem.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val currentPosition by viewModel.currentPosition.collectAsStateWithLifecycle()
    val duration by viewModel.duration.collectAsStateWithLifecycle()
    val shuffleModeEnabled by viewModel.shuffleModeEnabled.collectAsStateWithLifecycle()
    val repeatMode by viewModel.repeatMode.collectAsStateWithLifecycle()
    val audioFormat by viewModel.audioFormat.collectAsStateWithLifecycle()
    val bitrate by viewModel.bitrate.collectAsStateWithLifecycle()
    val sampleRate by viewModel.sampleRate.collectAsStateWithLifecycle()
    val sleepTimerRemaining by viewModel.sleepTimerRemaining.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val userPrefs by remember { UserPreferences(context).preferences }.collectAsState(initial = OmniPreferences())
    
    var isFullscreen by remember { mutableStateOf(false) }
    var isInPictureInPicture by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }

    DisposableEffect(context) {
        val activity = context.findActivity() as? androidx.activity.ComponentActivity
        val listener = androidx.core.util.Consumer<androidx.core.app.PictureInPictureModeChangedInfo> { info ->
            isInPictureInPicture = info.isInPictureInPictureMode
        }
        
        activity?.addOnPictureInPictureModeChangedListener(listener)
        isInPictureInPicture = activity?.isInPictureInPictureMode == true
        
        onDispose {
            activity?.removeOnPictureInPictureModeChangedListener(listener)
        }
    }

    if (isInPictureInPicture) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                        player = mediaController
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        return
    }

    var isLocked by remember { mutableStateOf(false) }
    var seekFeedback by remember { mutableIntStateOf(0) } // -1 for rewind, 1 for forward
    var brightnessLevel by remember { mutableFloatStateOf(-1f) }
    val swipeOffset = remember { Animatable(0f) }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val isVideo = remember(currentMediaItem) {
        currentMediaItem?.mediaMetadata?.artist?.toString() != "Audio"
    }

    val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
    var volumeLevel by remember { mutableFloatStateOf(currentVol.toFloat() / maxVol.toFloat()) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showSleepTimerMenu by remember { mutableStateOf(false) }

    DisposableEffect(isFullscreen, isVideo) {
        val activity = context.findActivity()
        if (isFullscreen && isVideo) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            val window = activity?.window
            if (window != null) {
                androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
                    hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                    systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            }
        } else {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            val window = activity?.window
            if (window != null) {
                androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).show(
                    androidx.core.view.WindowInsetsCompat.Type.systemBars()
                )
            }
        }
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    val backgroundColor = Color.Black
    val showPlaylist = remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .offset { IntOffset(0, swipeOffset.value.roundToInt()) }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { change, dragAmount ->
                        if (!isFullscreen) {
                            scope.launch {
                                swipeOffset.snapTo((swipeOffset.value + dragAmount).coerceAtLeast(0f))
                            }
                        }
                    },
                    onDragEnd = {
                        if (swipeOffset.value > 300f) {
                            onClose()
                        } else {
                            scope.launch {
                                swipeOffset.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy))
                            }
                        }
                    }
                )
            }
    ) {
        if (!isVideo) {
            AsyncImage(
                model = currentMediaItem?.mediaMetadata?.artworkUri,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        if (android.os.Build.VERSION.SDK_INT >= 31 && !userPrefs.lowPerfMode) {
                            renderEffect = AndroidRenderEffect.createBlurEffect(
                                100f, 100f, android.graphics.Shader.TileMode.CLAMP
                            ).asComposeRenderEffect()
                        }
                    },
                contentScale = ContentScale.Crop,
                alpha = 0.4f
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.4f),
                            Color.Black.copy(alpha = 0.8f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .systemBarsPadding()
        ) {
            if (!isFullscreen) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.KeyboardArrowDown, null, tint = Color.White)
                    }
                    if (isVideo) {
                        IconButton(onClick = { isFullscreen = true }) {
                            Icon(Icons.Default.Fullscreen, null, tint = Color.White)
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.2f))
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = { offset ->
                                    val width = size.width
                                    if (offset.x < width / 2) {
                                        viewModel.seekTo((currentPosition - 10000).coerceAtLeast(0))
                                        seekFeedback = -1
                                    } else {
                                        viewModel.seekTo((currentPosition + 10000).coerceAtMost(duration))
                                        seekFeedback = 1
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isVideo) {
                        AndroidView(
                            factory = { context ->
                                PlayerView(context).apply {
                                    useController = false
                                    resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                                }
                            },
                            update = { playerView -> playerView.player = mediaController },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        AsyncImage(
                            model = currentMediaItem?.mediaMetadata?.artworkUri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    if (seekFeedback != 0) {
                        LaunchedEffect(seekFeedback) {
                            delay(600)
                            seekFeedback = 0
                        }
                        Icon(
                            if (seekFeedback == -1) Icons.Default.FastRewind else Icons.Default.FastForward,
                            null, tint = Color.White, modifier = Modifier.size(64.dp)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                Column(modifier = Modifier.fillMaxWidth()) {
                    Slider(
                        value = if (duration > 0) (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f,
                        onValueChange = { viewModel.seekTo((it * duration).toLong()) },
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier.height(20.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(formatTime(currentPosition), color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelMedium)
                        Text(formatTime(duration), color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelMedium)
                    }
                }

                Spacer(Modifier.height(24.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = currentMediaItem?.mediaMetadata?.title?.toString() ?: "Unknown Title",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = currentMediaItem?.mediaMetadata?.artist?.toString() ?: "Unknown Artist",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )

                    if (userPrefs.showDetailedInfo) {
                        Text(
                            text = if (audioFormat != null) {
                                val kbps = if (bitrate > 0) " • ~${bitrate / 1000} kb/s" else ""
                                val khz = if (sampleRate > 0) " • ${sampleRate / 1000.0} kHz" else ""
                                "$audioFormat$kbps$khz"
                            } else "...",
                            color = Color.White.copy(alpha = 0.4f),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.toggleRepeatMode() }) {
                        Icon(
                            if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                            null, tint = if (repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else Color.White
                        )
                    }
                    IconButton(onClick = { viewModel.playPrevious() }) {
                        Icon(Icons.Default.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                    Surface(
                        onClick = { viewModel.playPause() },
                        shape = CircleShape,
                        color = Color.White,
                        modifier = Modifier.size(72.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                null, tint = Color.Black, modifier = Modifier.size(40.dp)
                            )
                        }
                    }
                    IconButton(onClick = { viewModel.playNext() }) {
                        Icon(Icons.Default.SkipNext, null, tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                    IconButton(onClick = { viewModel.toggleShuffle() }) {
                        Icon(
                            Icons.Default.Shuffle,
                            null, tint = if (shuffleModeEnabled) MaterialTheme.colorScheme.primary else Color.White
                        )
                    }
                }

                Spacer(Modifier.height(32.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.VolumeDown, null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
                    Slider(
                        value = volumeLevel,
                        onValueChange = {
                            volumeLevel = it
                            val targetVol = (it * maxVol).toInt()
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                        },
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.1f)
                        )
                    )
                    Icon(Icons.Default.VolumeUp, null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
                }

                Spacer(Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = { /* Empty for spacing */ }) { }
                    
                    IconButton(onClick = { showPlaylist.value = true }) {
                        Icon(Icons.Default.PlaylistPlay, null, tint = Color.White.copy(alpha = 0.7f))
                    }

                    Box {
                        IconButton(onClick = { showSpeedMenu = true }) {
                            Icon(Icons.Default.MoreVert, null, tint = Color.White.copy(alpha = 0.7f))
                        }
                        DropdownMenu(
                            expanded = showSpeedMenu,
                            onDismissRequest = { showSpeedMenu = false },
                            modifier = Modifier.background(Color(0xFF2A2A2A))
                        ) {
                            DropdownMenuItem(
                                text = { Text("Playback Speed", color = Color.White, fontWeight = FontWeight.Bold) },
                                onClick = { },
                                enabled = false
                            )
                            listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f).forEach { speed ->
                                DropdownMenuItem(
                                    text = { Text("${speed}x", color = Color.White) },
                                    onClick = {
                                        viewModel.setPlaybackSpeed(speed)
                                        showSpeedMenu = false
                                    }
                                )
                            }
                            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                            DropdownMenuItem(
                                text = { 
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Sleep Timer", color = Color.White)
                                        if (sleepTimerRemaining != null) {
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                text = formatTime(sleepTimerRemaining!!),
                                                color = MaterialTheme.colorScheme.primary,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    showSpeedMenu = false
                                    showSleepTimerMenu = true
                                }
                            )
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        factory = { context ->
                            PlayerView(context).apply {
                                useController = false
                                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                                (this.getChildAt(0) as? ViewGroup)?.setOnClickListener {
                                    showControls = !showControls
                                }
                            }
                        },
                        update = { playerView -> playerView.player = mediaController },
                        modifier = Modifier.fillMaxSize()
                    )

                    androidx.compose.animation.AnimatedVisibility(
                        visible = showControls,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.4f))
                        ) {
                            IconButton(
                                onClick = { isFullscreen = false },
                                modifier = Modifier.padding(16.dp).align(Alignment.TopStart)
                            ) {
                                Icon(Icons.Default.FullscreenExit, null, tint = Color.White)
                            }

                            // Middle controls
                            Row(
                                modifier = Modifier.align(Alignment.Center),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(48.dp)
                            ) {
                                IconButton(onClick = { viewModel.playPrevious() }) {
                                    Icon(Icons.Default.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(48.dp))
                                }
                                IconButton(onClick = { viewModel.playPause() }) {
                                    Icon(
                                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        null, tint = Color.White, modifier = Modifier.size(64.dp)
                                    )
                                }
                                IconButton(onClick = { viewModel.playNext() }) {
                                    Icon(Icons.Default.SkipNext, null, tint = Color.White, modifier = Modifier.size(48.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
        
        if (showPlaylist.value) {
            PlaylistDrawer(
                queue = viewModel.getQueue(),
                currentMediaItem = currentMediaItem,
                onClose = { showPlaylist.value = false },
                onSkipTo = { viewModel.skipToQueueItem(it) }
            )
        }

        if (showSleepTimerMenu) {
            AlertDialog(
                onDismissRequest = { showSleepTimerMenu = false },
                containerColor = Color(0xFF1A1A1A),
                title = { Text("Sleep Timer", color = Color.White) },
                text = {
                    Column {
                        if (sleepTimerRemaining != null) {
                            Text(
                                "Timer active: ${formatTime(sleepTimerRemaining!!)}",
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                        }
                        listOf(15, 30, 45, 60, 0).forEach { mins ->
                            TextButton(
                                onClick = {
                                    if (mins == 0) viewModel.stopSleepTimer()
                                    else viewModel.startSleepTimer(mins)
                                    showSleepTimerMenu = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    if (mins == 0) "Turn Off" else "$mins minutes",
                                    color = if (mins == 0) Color.Red.copy(alpha = 0.7f) else Color.White
                                )
                            }
                        }
                    }
                },
                confirmButton = {}
            )
        }
    }
}

@Composable
fun PlaylistDrawer(
    queue: List<androidx.media3.common.MediaItem>,
    currentMediaItem: androidx.media3.common.MediaItem?,
    onClose: () -> Unit,
    onSkipTo: (Int) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black.copy(alpha = 0.95f)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Up Next", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, null, tint = Color.White)
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            androidx.compose.foundation.lazy.LazyColumn {
                items(queue.size) { index ->
                    val item = queue[index]
                    val isCurrent = item.mediaId == currentMediaItem?.mediaId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSkipTo(index) }
                            .padding(vertical = 12.dp, horizontal = 8.dp)
                            .background(
                                if (isCurrent) Color.White.copy(alpha = 0.1f) 
                                else Color.Transparent,
                                RoundedCornerShape(8.dp)
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = item.mediaMetadata.artworkUri,
                            contentDescription = null,
                            modifier = Modifier.size(50.dp).clip(RoundedCornerShape(4.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = item.mediaMetadata.title?.toString() ?: "Unknown",
                                color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.White,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = item.mediaMetadata.artist?.toString() ?: "Unknown",
                                color = Color.Gray,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}

fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
