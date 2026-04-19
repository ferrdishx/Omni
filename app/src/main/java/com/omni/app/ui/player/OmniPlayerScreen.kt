package com.omni.app.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.decode.VideoFrameDecoder
import com.omni.app.data.download.DownloadEntity
import java.io.File
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.omni.app.data.download.DownloadedMedia
import com.omni.app.data.prefs.OmniPreferences
import com.omni.app.data.prefs.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.math.roundToInt

@OptIn(UnstableApi::class)
@Composable
fun OmniPlayerScreen(
    viewModel: OmniPlayerViewModel,
    onNavigateBack: () -> Unit
) {
    val currentMediaItem   by viewModel.currentMediaItem.collectAsStateWithLifecycle()
    val mediaMetadata      by viewModel.mediaMetadata.collectAsStateWithLifecycle()
    val isPlaying          by viewModel.isPlaying.collectAsStateWithLifecycle()
    val currentPosition    by viewModel.currentPosition.collectAsStateWithLifecycle()
    val duration           by viewModel.duration.collectAsStateWithLifecycle()
    val shuffleModeEnabled by viewModel.shuffleModeEnabled.collectAsStateWithLifecycle()
    val repeatMode         by viewModel.repeatMode.collectAsStateWithLifecycle()
    val audioFormat        by viewModel.audioFormat.collectAsStateWithLifecycle()
    val bitrate            by viewModel.bitrate.collectAsStateWithLifecycle()
    val videoAspectRatio   by viewModel.videoAspectRatio.collectAsStateWithLifecycle()
    val sleepTimerRemaining by viewModel.sleepTimerRemaining.collectAsStateWithLifecycle()
    val mediaController    by viewModel.mediaController.collectAsStateWithLifecycle()
    val isFavorite         by viewModel.isFavorite.collectAsStateWithLifecycle()
    val library            by remember(viewModel) { viewModel.getLibraryMedia() }.collectAsStateWithLifecycle(emptyList())

    val context  = LocalContext.current
    val prefs    = remember { UserPreferences(context) }
    val settingsState by prefs.preferences.collectAsState(initial = OmniPreferences())
    val settings = settingsState
    val showDetailedInfo = settings.showDetailedInfo

    val isDark = when (settings.themeMode) {
        "Light" -> false
        "Dark" -> true
        else -> isSystemInDarkTheme()
    }

    val isVideo = currentMediaItem?.mediaMetadata?.let {
        it.mediaType == androidx.media3.common.MediaMetadata.MEDIA_TYPE_VIDEO ||
                it.artist?.toString() == "Video"
    } ?: false

    val defaultBg = if (isDark) Color(0xFF0F0F14) else Color(0xFFF5F5F7)
    var dominantColor by remember { mutableStateOf(defaultBg) }
    val artworkUri = currentMediaItem?.mediaMetadata?.artworkUri

    LaunchedEffect(artworkUri, isDark) {
        if (artworkUri == null) { dominantColor = defaultBg; return@LaunchedEffect }
        try {
            val loader  = coil.ImageLoader(context)
            val req     = coil.request.ImageRequest.Builder(context).data(artworkUri).allowHardware(false).build()
            val result  = loader.execute(req)
            val bitmap  = (result as? coil.request.SuccessResult)?.drawable?.let {
                (it as? android.graphics.drawable.BitmapDrawable)?.bitmap
            }
            if (bitmap != null) {
                val palette = withContext(Dispatchers.Default) { Palette.from(bitmap).generate() }
                val swatch  = palette.darkVibrantSwatch
                    ?: palette.dominantSwatch
                    ?: palette.darkMutedSwatch
                swatch?.let { dominantColor = Color(it.rgb).copy(alpha = 1f) }
            }
        } catch (_: Exception) {}
    }

    val animDominant by animateColorAsState(
        targetValue = dominantColor,
        animationSpec = tween(800, easing = EaseInOutQuart),
        label = "dominantColor"
    )

    var isFullscreen         by remember { mutableStateOf(false) }
    var showControls         by remember { mutableStateOf(true) }
    val showPlaylist          = remember { mutableStateOf(false) }
    var showAddQueueMenu     by remember { mutableStateOf(false) }
    var showSleepTimerMenu   by remember { mutableStateOf(false) }
    var showPlaybackSettings by remember { mutableStateOf(false) }

    if (isFullscreen) BackHandler { isFullscreen = false }

    LaunchedEffect(isFullscreen) {
        val activity   = context.findActivity() ?: return@LaunchedEffect
        val window     = activity.window
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (isFullscreen) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDark) Color.Black else Color.White)
    ) {
        if (!settings.lowPerfMode) {
            AsyncImage(
                model = artworkUri,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            renderEffect = RenderEffect
                                .createBlurEffect(80f, 80f, Shader.TileMode.CLAMP)
                                .asComposeRenderEffect()
                        }
                        alpha = if (isDark) 0.35f else 0.20f
                    },
                contentScale = ContentScale.Crop
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f   to animDominant.copy(alpha = if (isDark) 0.45f else 0.3f),
                        0.5f to (if (isDark) Color.Black.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.4f)),
                        1f   to (if (isDark) Color.Black.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.9f))
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Rounded.KeyboardArrowDown, null,
                        tint = if (isDark) Color.White else Color.Black, modifier = Modifier.size(32.dp))
                }

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (!isVideo) {
                        Text(
                            text = "From your library",
                            style = MaterialTheme.typography.labelSmall,
                            color = (if (isDark) Color.White else Color.Black).copy(alpha = 0.55f)
                        )
                    }
                    Text(
                        text = (currentMediaItem?.mediaMetadata?.albumTitle?.toString()
                            ?.takeIf { it.isNotBlank() && it != "Unknown Album" }
                            ?: currentMediaItem?.mediaMetadata?.title?.toString()
                            ?: "").let { if (showDetailedInfo) it else it.substringBeforeLast('.') },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isDark) Color.White else Color.Black,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee()
                    )
                }

                IconButton(onClick = { showPlaybackSettings = true }) {
                    Icon(Icons.Rounded.MoreVert, null, tint = if (isDark) Color.White else Color.Black)
                }
            }

            Spacer(Modifier.weight(0.08f))

            val artScale by animateFloatAsState(
                targetValue = if (settings.lowPerfMode) 1f else (if (isPlaying) 1f else 0.93f),
                animationSpec = if (settings.lowPerfMode) snap() else spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
                label = "artScale"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(if (isVideo && videoAspectRatio > 0f) videoAspectRatio else 1f)
                    .padding(if (isVideo && videoAspectRatio > 1.2f) 0.dp else 24.dp)
                    .graphicsLayer {
                        scaleX = artScale
                        scaleY = artScale
                    }
                    .clip(RoundedCornerShape(if (isVideo && videoAspectRatio > 1.2f) 0.dp else 20.dp))
                    .background(Color(0xFF1A1A1A))
                    .clickable { if (isVideo) isFullscreen = true }
            ) {
                AnimatedContent(
                    targetState = artworkUri,
                    transitionSpec = {
                        if (settings.lowPerfMode) {
                            fadeIn(animationSpec = snap()) togetherWith fadeOut(animationSpec = snap())
                        } else {
                            (fadeIn(animationSpec = tween(500)) + scaleIn(initialScale = 0.9f))
                                .togetherWith(fadeOut(animationSpec = tween(500)))
                        }
                    },
                    label = "artworkTransition"
                ) { targetUri ->
                    if (isVideo && !isFullscreen) {
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    player = mediaController
                                    useController = false
                                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    setKeepContentOnPlayerReset(true)
                                }
                            },
                            update = { it.player = mediaController },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        AsyncImage(
                            model = targetUri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }

            Spacer(Modifier.weight(0.08f))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
            ) {
                AnimatedContent(
                    targetState = currentMediaItem?.mediaMetadata?.title?.toString() ?: "Unknown Title",
                    transitionSpec = {
                        if (settings.lowPerfMode) {
                            fadeIn(animationSpec = snap()) togetherWith fadeOut(animationSpec = snap())
                        } else {
                            (slideInHorizontally { width -> width / 2 } + fadeIn())
                                .togetherWith(slideOutHorizontally { width -> -width / 2 } + fadeOut())
                        }
                    },
                    label = "titleTransition"
                ) { rawTitle ->
                    val title = rawTitle.let { if (showDetailedInfo) it else it.substringBeforeLast('.') }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = if (isDark) Color.White else Color.Black,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        modifier = Modifier
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                            .drawWithContent {
                                drawContent()
                                drawRect(
                                    brush = Brush.horizontalGradient(
                                        0f to Color.Transparent,
                                        0.05f to (if (isDark) Color.Black else Color.White),
                                        0.95f to (if (isDark) Color.Black else Color.White),
                                        1f to Color.Transparent
                                    ),
                                    blendMode = BlendMode.DstIn
                                )
                            }
                            .basicMarquee(iterations = Int.MAX_VALUE)
                    )
                }
                Spacer(Modifier.height(4.dp))
                val displayArtist = remember(mediaMetadata) {
                    val artist = mediaMetadata.artist?.toString() ?: mediaMetadata.albumArtist?.toString()
                    val omniAuthor = mediaMetadata.extras?.getString("omni_author")

                    when {
                        !artist.isNullOrBlank() && artist != "Audio" && artist != "Video" -> artist
                        !omniAuthor.isNullOrBlank() && omniAuthor != "Audio" && omniAuthor != "Video" && omniAuthor != "Local File" && omniAuthor != "Unknown" -> omniAuthor
                        else -> "Unknown"
                    }
                }
                AnimatedContent(
                    targetState = displayArtist,
                    transitionSpec = {
                        if (settings.lowPerfMode) {
                            fadeIn(animationSpec = snap()) togetherWith fadeOut(animationSpec = snap())
                        } else {
                            fadeIn(tween(400)).togetherWith(fadeOut(tween(400)))
                        }
                    },
                    label = "artistTransition"
                ) { artist ->
                    Text(
                        text = artist,
                        style = MaterialTheme.typography.titleMedium,
                        color = (if (isDark) Color.White else Color.Black).copy(alpha = 0.65f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            PlayerProgressBar(
                currentPosition = currentPosition,
                duration = duration,
                accentColor = if (isDark) animDominant.lighten(0.6f) else animDominant,
                isDark = isDark,
                onSeek = { viewModel.seekTo(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp)
            )

            Spacer(Modifier.height(4.dp))

            val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            val btnScale  by animateFloatAsState(
                if (settings.lowPerfMode) 1f else (if (isPressed) 0.86f else 1f),
                animationSpec = if (settings.lowPerfMode) snap() else spring(),
                label = "btnScale"
            )
            val cornerDp  by animateDpAsState(
                if (settings.lowPerfMode) 22.dp else (if (isPlaying) 22.dp else 36.dp),
                animationSpec = if (settings.lowPerfMode) snap() else spring(),
                label = "corner"
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ControlIcon(
                    icon = Icons.Rounded.Shuffle,
                    tint = (if (isDark) Color.White else Color.Black).copy(alpha = 0.5f),
                    isActive = shuffleModeEnabled,
                    activeColor = if (isDark) animDominant.lighten(0.7f) else animDominant,
                    onClick = { viewModel.toggleShuffle() },
                    lowPerfMode = settings.lowPerfMode
                )
                ControlIcon(
                    icon = Icons.Rounded.SkipPrevious,
                    size = 44,
                    tint = if (isDark) Color.White else Color.Black,
                    onClick = { viewModel.playPrevious() },
                    lowPerfMode = settings.lowPerfMode
                )

                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .graphicsLayer { scaleX = btnScale; scaleY = btnScale }
                        .clip(RoundedCornerShape(cornerDp))
                        .background(if (isDark) Color.White else Color.Black)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null
                        ) { viewModel.playPause() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        null, tint = if (isDark) Color.Black else Color.White, modifier = Modifier.size(40.dp)
                    )
                }

                ControlIcon(
                    icon = Icons.Rounded.SkipNext,
                    size = 44,
                    tint = if (isDark) Color.White else Color.Black,
                    onClick = { viewModel.playNext() },
                    lowPerfMode = settings.lowPerfMode
                )
                ControlIcon(
                    icon = when (repeatMode) {
                        Player.REPEAT_MODE_ONE -> Icons.Rounded.RepeatOne
                        else                   -> Icons.Rounded.Repeat
                    },
                    tint = (if (isDark) Color.White else Color.Black).copy(alpha = 0.5f),
                    isActive = repeatMode != Player.REPEAT_MODE_OFF,
                    activeColor = if (isDark) animDominant.lighten(0.7f) else animDominant,
                    onClick = { viewModel.toggleRepeatMode() },
                    lowPerfMode = settings.lowPerfMode
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    IconButton(onClick = { showAddQueueMenu = true }) {
                        Icon(Icons.Rounded.AddCircleOutline, null,
                            tint = (if (isDark) Color.White else Color.Black).copy(alpha = 0.65f), modifier = Modifier.size(24.dp))
                    }
                }

                Row(
                    modifier = Modifier.weight(2f),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (audioFormat != null) {
                            QualityChip(label = audioFormat!!, accentColor = if (isDark) animDominant.lighten(0.7f) else animDominant)
                        }
                        if (bitrate > 0) {
                            QualityChip(label = "${bitrate / 1000}kbps", accentColor = if (isDark) animDominant.lighten(0.7f) else animDominant)
                        }
                    }
                }

                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val favScale by animateFloatAsState(
                        targetValue = if (isFavorite) 1.25f else 1f,
                        animationSpec = if (settings.lowPerfMode) snap() else spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "favScale"
                    )
                    IconButton(onClick = { viewModel.toggleFavorite() }) {
                        Icon(
                            if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            null,
                            tint = if (isFavorite) Color(0xFFFF4B6E) else (if (isDark) Color.White else Color.Black).copy(alpha = 0.65f),
                            modifier = Modifier.size(22.dp).graphicsLayer { scaleX = favScale; scaleY = favScale }
                        )
                    }

                    IconButton(onClick = { showPlaylist.value = true }) {
                        Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, null,
                            tint = (if (isDark) Color.White else Color.Black).copy(alpha = 0.65f), modifier = Modifier.size(22.dp))
                    }
                }
            }
        }

        if (isFullscreen) {
            Dialog(
                onDismissRequest = { isFullscreen = false },
                properties = androidx.compose.ui.window.DialogProperties(
                    usePlatformDefaultWidth = false,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = false
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) { showControls = !showControls }
                ) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = mediaController
                                useController = false
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                setKeepContentOnPlayerReset(true)
                            }
                        },
                        update = { it.player = mediaController },
                        modifier = Modifier.fillMaxSize()
                    )
                    AnimatedVisibility(
                        visible = showControls,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.45f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.TopStart)
                                    .statusBarsPadding()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { isFullscreen = false }) {
                                    Icon(Icons.Rounded.FullscreenExit, null, tint = Color.White)
                                }
                                Text(
                                    currentMediaItem?.mediaMetadata?.title?.toString() ?: "",
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
                                )
                            }
                            Row(
                                modifier = Modifier.align(Alignment.Center),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(48.dp)
                            ) {
                                ControlIcon(Icons.Rounded.SkipPrevious, size = 48, onClick = { viewModel.playPrevious() })
                                ControlIcon(
                                    icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    size = 64,
                                    onClick = { viewModel.playPause() }
                                )
                                ControlIcon(Icons.Rounded.SkipNext, size = 48, onClick = { viewModel.playNext() })
                            }
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .navigationBarsPadding()
                                    .padding(horizontal = 24.dp, vertical = 24.dp)
                            ) {
                                Text(
                                    "${formatTime(currentPosition)} / ${formatTime(duration)}",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Slider(
                                    value = currentPosition.toFloat(),
                                    onValueChange = { viewModel.seekTo(it.toLong()) },
                                    valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color.White,
                                        activeTrackColor = Color.White,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showAddQueueMenu) {
            AddQueueDialog(
                library = library,
                isDark = isDark,
                onDismiss = { showAddQueueMenu = false },
                onAdd = { item ->
                    viewModel.addToQueue(item)
                    showAddQueueMenu = false
                }
            )
        }

        if (showPlaylist.value) {
            PlaylistDrawer(
                queue = viewModel.getQueue(),
                currentMediaItem = currentMediaItem,
                isDark = isDark,
                onClose = { showPlaylist.value = false },
                onSkipTo = { viewModel.skipToQueueItem(it) }
            )
        }

        if (showSleepTimerMenu) {
            AlertDialog(
                onDismissRequest = { showSleepTimerMenu = false },
                containerColor = if (isDark) Color(0xFF1A1A1A) else Color.White,
                title = { Text("Sleep Timer", color = if (isDark) Color.White else Color.Black) },
                text = {
                    Column {
                        if (sleepTimerRemaining != null) {
                            Text("Active timer: ${formatTime(sleepTimerRemaining!!)}",
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 16.dp))
                        }
                        listOf(15, 30, 45, 60, 0).forEach { mins ->
                            TextButton(
                                onClick = {
                                    if (mins == 0) viewModel.stopSleepTimer() else viewModel.startSleepTimer(mins)
                                    showSleepTimerMenu = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    if (mins == 0) "Turn off" else "$mins minutes",
                                    color = if (mins == 0) Color(0xFFFF4B6E) else (if (isDark) Color.White else Color.Black)
                                )
                            }
                        }
                    }
                },
                confirmButton = {}
            )
        }

        if (showPlaybackSettings) {
            PlaybackSettingsDialog(
                viewModel = viewModel,
                isDark = isDark,
                onDismiss = { showPlaybackSettings = false },
                onShowSleepTimer = { showPlaybackSettings = false; showSleepTimerMenu = true }
            )
        }
    }
}

@Composable
fun AddQueueDialog(
    library: List<DownloadedMedia>,
    isDark: Boolean,
    onDismiss: () -> Unit,
    onAdd: (DownloadedMedia) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = if (isDark) Color(0xFF1C1C1E) else Color.White,
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f).padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    "Add to Queue",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isDark) Color.White else Color.Black
                )
                Spacer(Modifier.height(16.dp))
                if (library.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No songs found", color = Color.Gray)
                    }
                } else {
                    androidx.compose.foundation.lazy.LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(library.size) { index ->
                            val item = library[index]
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onAdd(item) }
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val context = LocalContext.current
                                    val thumbModel = remember(item.thumbnailUrl, item.filePath) {
                                        val thumbFile = if (item.thumbnailUrl != null) File(item.thumbnailUrl) else null
                                        val resolvedThumbFile = when {
                                            thumbFile?.exists() == true -> thumbFile
                                            else -> {
                                                val file = File(item.filePath)
                                                val downloadDir = file.parentFile
                                                val baseName = file.nameWithoutExtension
                                                val possibleFolders = listOf(".thumbaudio", ".thumbaudios", ".thumbnails")
                                                var found: File? = null
                                                for (folder in possibleFolders) {
                                                    val folderPaths = listOf(
                                                        File(downloadDir, folder),
                                                        File(downloadDir?.parentFile, folder)
                                                    )
                                                    for (folderPath in folderPaths) {
                                                        val f = File(folderPath, "$baseName.jpg")
                                                        if (f.exists()) {
                                                            found = f
                                                            break
                                                        }
                                                    }
                                                    if (found != null) break
                                                }
                                                found
                                            }
                                        }

                                        ImageRequest.Builder(context)
                                            .data(resolvedThumbFile ?: File(item.filePath))
                                            .apply {
                                                if (!item.isAudio) {
                                                    decoderFactory(VideoFrameDecoder.Factory())
                                                    setParameter("coil#video_frame_micros", 2000000L)
                                                }
                                            }
                                            .crossfade(true)
                                            .build()
                                    }

                                    SubcomposeAsyncImage(
                                        model = thumbModel,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop,
                                        loading = { Box(Modifier.background(Color.DarkGray)) },
                                        error = {
                                            Box(Modifier.background(Color.DarkGray), contentAlignment = Alignment.Center) {
                                                Icon(
                                                    if (item.isAudio) Icons.Rounded.MusicNote else Icons.Rounded.PlayArrow,
                                                    null, tint = Color.Gray, modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }
                                    )
                                    Spacer(Modifier.width(16.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            item.title,
                                            color = if (isDark) Color.White else Color.Black,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        Text(
                                            item.author,
                                            color = Color.Gray,
                                            style = MaterialTheme.typography.labelMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerProgressBar(
    currentPosition: Long,
    duration: Long,
    accentColor: Color,
    isDark: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }

    Column(modifier = modifier) {
        Slider(
            value = if (isDragging) dragValue else currentPosition.toFloat(),
            onValueChange = {
                isDragging = true
                dragValue = it
            },
            onValueChangeFinished = {
                onSeek(dragValue.toLong())
                isDragging = false
            },
            valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
            colors = SliderDefaults.colors(
                thumbColor = if (isDark) Color.White else Color.Black,
                activeTrackColor = accentColor,
                inactiveTrackColor = (if (isDark) Color.White else Color.Black).copy(alpha = 0.18f)
            )
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(formatTime(if (isDragging) dragValue.toLong() else currentPosition),
                color = (if (isDark) Color.White else Color.Black).copy(alpha = 0.55f),
                style = MaterialTheme.typography.labelSmall)
            Text(formatTime(duration),
                color = (if (isDark) Color.White else Color.Black).copy(alpha = 0.55f),
                style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun QualityChip(label: String, accentColor: Color) {
    Surface(
        color = accentColor.copy(alpha = 0.18f),
        shape = RoundedCornerShape(6.dp),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, accentColor.copy(alpha = 0.35f))
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = accentColor,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ControlIcon(
    icon: ImageVector,
    size: Int = 28,
    tint: Color = Color.White,
    onClick: () -> Unit,
    isActive: Boolean = false,
    activeColor: Color = Color.White,
    lowPerfMode: Boolean = false
) {
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1.25f else 1f,
        animationSpec = if (lowPerfMode) snap() else spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "iconScale"
    )

    val finalTint by animateColorAsState(
        targetValue = if (isActive) activeColor else tint,
        animationSpec = if (lowPerfMode) snap() else tween(400),
        label = "iconTint"
    )

    IconButton(onClick = onClick) {
        Box(contentAlignment = Alignment.Center) {
            if (isActive && !lowPerfMode) {
                Box(
                    modifier = Modifier
                        .size((size * 1.5).dp)
                        .background(activeColor.copy(alpha = 0.15f), CircleShape)
                )
            }
            Icon(
                icon,
                contentDescription = null,
                tint = finalTint,
                modifier = Modifier
                    .size(size.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
            )
        }
    }
}

private fun Color.lighten(by: Float): Color =
    copy(
        red   = (red   + (1f - red)   * by).coerceIn(0f, 1f),
        green = (green + (1f - green) * by).coerceIn(0f, 1f),
        blue  = (blue  + (1f - blue)  * by).coerceIn(0f, 1f)
    )

@Composable
fun PlaybackSettingsDialog(
    viewModel: OmniPlayerViewModel,
    isDark: Boolean,
    onDismiss: () -> Unit,
    onShowSleepTimer: () -> Unit
) {
    val currentSpeed by viewModel.playbackSpeed.collectAsStateWithLifecycle()
    val currentPitch by viewModel.playbackPitch.collectAsStateWithLifecycle()

    var tempSpeed by remember { mutableFloatStateOf(currentSpeed) }
    var tempPitch by remember { mutableFloatStateOf(currentPitch) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = if (isDark) Color(0xFF1C1C1E) else Color.White,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Audio settings",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isDark) Color.White else Color.Black)

                Spacer(Modifier.height(28.dp))
                PlaybackSlider("Playback speed", tempSpeed, 0.5f..4.0f,
                    { tempSpeed = it }, "x", isDark)
                Spacer(Modifier.height(28.dp))
                PlaybackSlider("Pitch", tempPitch, 0.5f..2.0f,
                    { tempPitch = it }, "", isDark)
                Spacer(Modifier.height(28.dp))

                Button(
                    onClick = onShowSleepTimer,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = (if (isDark) Color.White else Color.Black).copy(alpha = 0.10f),
                        contentColor = if (isDark) Color.White else Color.Black
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.Timer, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Sleep Timer", fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(
                        onClick = {
                            tempSpeed = 1f
                            tempPitch = 1f
                            viewModel.setPlaybackSpeed(1f)
                            viewModel.setPitch(1f)
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Reset", color = (if (isDark) Color.White else Color.Black).copy(alpha = 0.6f)) }
                    Button(
                        onClick = {
                            viewModel.setPlaybackSpeed(tempSpeed)
                            viewModel.setPitch(tempPitch)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) { Text("Done", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
fun PlaybackSlider(
    label: String, value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit, unit: String,
    isDark: Boolean
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text(label, color = (if (isDark) Color.White else Color.Black).copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            AnimatedContent(targetState = value, label = "val") {
                Text(String.format(Locale.getDefault(), "%.2f%s", it, unit),
                    color = if (isDark) Color.White else Color.Black, style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black)
            }
        }
        Spacer(Modifier.height(10.dp))
        Slider(
            value = value,
            onValueChange = { onValueChange((it * 20f).roundToInt() / 20f) },
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = if (isDark) Color.White else Color.Black,
                activeTrackColor = if (isDark) Color.White else Color.Black,
                inactiveTrackColor = (if (isDark) Color.White else Color.Black).copy(alpha = 0.1f)
            )
        )
    }
}

@Composable
fun PlaylistDrawer(
    queue: List<androidx.media3.common.MediaItem>,
    currentMediaItem: androidx.media3.common.MediaItem?,
    isDark: Boolean,
    onClose: () -> Unit,
    onSkipTo: (Int) -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = (if (isDark) Color.Black else Color.White).copy(alpha = 0.95f)) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("Up Next", color = if (isDark) Color.White else Color.Black,
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, null, tint = if (isDark) Color.White else Color.Black) }
            }
            Spacer(Modifier.height(16.dp))
            androidx.compose.foundation.lazy.LazyColumn {
                items(queue.size) { index ->
                    val item      = queue[index]
                    val isCurrent = item.mediaId == currentMediaItem?.mediaId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSkipTo(index) }
                            .background(
                                if (isCurrent) (if (isDark) Color.White else Color.Black).copy(alpha = 0.08f) else Color.Transparent,
                                RoundedCornerShape(10.dp)
                            )
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = item.mediaMetadata.artworkUri,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                item.mediaMetadata.title?.toString() ?: "Unknown",
                                color = if (isCurrent) MaterialTheme.colorScheme.primary else (if (isDark) Color.White else Color.Black),
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                item.mediaMetadata.artist?.toString() ?: "Unknown",
                                color = Color.Gray, style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val s = ms / 1000
    return String.format(Locale.getDefault(), "%02d:%02d", s / 60, s % 60)
}

fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) { if (ctx is Activity) return ctx; ctx = ctx.baseContext }
    return null
}
