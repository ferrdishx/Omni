package com.omni.app

import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.util.Rational
import android.app.PictureInPictureParams
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.omni.app.data.prefs.UserPreferences
import com.omni.app.navigation.OmniNavHost
import com.omni.app.navigation.Screen
import com.omni.app.ui.components.LiquidNavItem
import com.omni.app.ui.components.LiquidNavigationBar
import com.omni.app.ui.onboarding.OnboardingScreen
import com.omni.app.ui.player.OmniPlayerViewModel
import com.omni.app.ui.theme.LocalOmniPreferences
import com.omni.app.ui.theme.OmniTheme
import com.omni.app.BuildConfig
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// ─── Dimensions (single source of truth) ─────────────────────────────────────
private val MINI_PLAYER_HEIGHT = 68.dp
private val NAV_BAR_HEIGHT     = 80.dp   // LiquidNavigationBar approx height
private val MINI_CORNER        = 20.dp
private val DRAG_THRESHOLD     = 0.45f   // snap-to-collapsed below this progress
// ─────────────────────────────────────────────────────────────────────────────

class MainActivity : ComponentActivity() {

    private val isInPipMode = mutableStateOf(false)

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean, newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipMode.value = isInPictureInPictureMode
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            val vm = ViewModelProvider(this)[OmniPlayerViewModel::class.java]
            val isVideo = vm.currentMediaItem.value?.mediaMetadata?.let {
                it.mediaType == MediaMetadata.MEDIA_TYPE_VIDEO || it.artist?.toString() == "Video"
            } ?: false
            if (isVideo && vm.isPlaying.value) {
                enterPictureInPictureMode(
                    PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()
                )
            }
        }
    }

    fun updatePiPParams(isVideo: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setPictureInPictureParams(
                PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .setAutoEnterEnabled(isVideo)
                    .build()
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                YoutubeDL.getInstance().init(application)
                FFmpeg.getInstance().init(application)
                if (BuildConfig.ENABLE_ENGINE_UPDATE) {
                    YoutubeDL.getInstance().updateYoutubeDL(application, YoutubeDL.UpdateChannel.STABLE)
                }
            } catch (e: Exception) {
                Log.e("Omni", "Init error: ${e.message}", e)
            }
        }

        enableEdgeToEdge()
        setContent {
            val inPip by isInPipMode
            OmniTheme { OmniApp(isInPipMode = inPip) }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────

@OptIn(UnstableApi::class)
@Composable
fun OmniApp(isInPipMode: Boolean = false) {
    val context       = LocalContext.current
    val density       = LocalDensity.current
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp
    val screenHeightPx = with(density) { screenHeightDp.toPx() }

    val navController    = rememberNavController()
    val backStackEntry  by navController.currentBackStackEntryAsState()
    val currentRoute     = backStackEntry?.destination?.route

    val settings   = LocalOmniPreferences.current
    val userPrefs  = remember { UserPreferences(context) }
    val scope      = rememberCoroutineScope()

    val playerViewModel: OmniPlayerViewModel = viewModel()
    val currentMedia by playerViewModel.currentMediaItem.collectAsStateWithLifecycle()
    val isPlaying   by playerViewModel.isPlaying.collectAsStateWithLifecycle()

    val isVideo = currentMedia?.mediaMetadata?.let {
        it.mediaType == MediaMetadata.MEDIA_TYPE_VIDEO || it.artist?.toString() == "Video"
    } ?: false

    val hasMedia       = currentMedia != null
    val hideBottomArea = currentRoute == Screen.Settings.route || currentRoute == Screen.Player.route

    // ── progress: 0f = mini/collapsed, 1f = full player ──────────────────────
    // We animate in px-space for accurate drag feel, then normalise to 0..1
    val sheetOffsetPx = remember { Animatable(screenHeightPx) } // off-screen initially

    // When media first appears, animate to mini position
    LaunchedEffect(hasMedia) {
        if (hasMedia && sheetOffsetPx.value >= screenHeightPx * 0.95f) {
            sheetOffsetPx.animateTo(
                targetValue = screenHeightPx,   // start off-screen
                animationSpec = snap()
            )
            // sheet at "screenHeightPx" means collapsed (mini) position
            // we use a different coordinate: 0 = fully expanded, screenHeightPx = mini
            // Actually let's re-think: we map offset 0 = fully open, (screenHeight - mini) = collapsed
        }
    }

    // Cleaner coordinate system:
    //   sheetOffsetPx = 0           → fully expanded (full player)
    //   sheetOffsetPx = collapsedPx → collapsed (mini player visible above nav bar)
    //   sheetOffsetPx > collapsedPx → hidden (no media)
    val miniPlayerHeightPx = with(density) { MINI_PLAYER_HEIGHT.toPx() }
    val navBarHeightPx     = with(density) { (NAV_BAR_HEIGHT + 16.dp).toPx() }
    val collapsedOffsetPx  = screenHeightPx - miniPlayerHeightPx - navBarHeightPx

    // Snap to collapsed when media arrives (if currently hidden)
    LaunchedEffect(hasMedia) {
        if (hasMedia && sheetOffsetPx.value > collapsedOffsetPx + 10f) {
            sheetOffsetPx.snapTo(collapsedOffsetPx)
        } else if (!hasMedia) {
            sheetOffsetPx.snapTo(screenHeightPx) // hide
        }
    }

    // progress 0..1 where 0 = mini, 1 = full
    val progress = if (collapsedOffsetPx <= 0f) 0f else
        (1f - (sheetOffsetPx.value / collapsedOffsetPx)).coerceIn(0f, 1f)

    val isExpanded   = progress > 0.5f
    val showNavBar   = !hideBottomArea && !isExpanded
    val showSheet    = hasMedia && !hideBottomArea

    // ── helpers to animate open/close ────────────────────────────────────────
    val expandSpec  = spring<Float>(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioLowBouncy)
    val collapseSpec = tween<Float>(durationMillis = 260, easing = FastOutSlowInEasing)

    fun expand()   { scope.launch { sheetOffsetPx.animateTo(0f,                 expandSpec) } }
    fun collapse() { scope.launch { sheetOffsetPx.animateTo(collapsedOffsetPx,  collapseSpec) } }

    if (!settings.onboardingCompleted) {
        OnboardingScreen(onComplete = { scope.launch { userPrefs.setOnboardingCompleted(true) } })
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // ── Main scaffold ─────────────────────────────────────────────────────
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                // Reserve space for the sheet + nav bar (avoids content going under them)
                val bottomPad = if (showNavBar && showSheet)
                    MINI_PLAYER_HEIGHT + NAV_BAR_HEIGHT + 16.dp
                else if (showNavBar)
                    NAV_BAR_HEIGHT + 8.dp
                else if (showSheet)
                    MINI_PLAYER_HEIGHT + 8.dp
                else
                    0.dp
                Spacer(modifier = Modifier.height(bottomPad).navigationBarsPadding())
            }
        ) { innerPadding ->
            LaunchedEffect(currentMedia, isPlaying) {
                (context as? MainActivity)?.updatePiPParams(isVideo && isPlaying)
            }

            if (isInPipMode && isVideo) {
                val mediaController by playerViewModel.mediaController.collectAsStateWithLifecycle()
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = mediaController
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                    },
                    update = { it.player = mediaController },
                    modifier = Modifier.fillMaxSize().background(Color.Black)
                )
            } else {
                Box(modifier = Modifier.fillMaxSize().padding(top = innerPadding.calculateTopPadding())) {
                    OmniNavHost(
                        navController    = navController,
                        playerViewModel  = playerViewModel,
                        onOpenPlayer     = { expand() }
                    )
                }
            }
        }

        // ── Bottom area: sheet + nav bar ──────────────────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 8.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            // ── Player sheet (the morphing card) ──────────────────────────────
            if (showSheet) {
                PlayerSheet(
                    progress       = progress,
                    isExpanded     = isExpanded,
                    playerViewModel = playerViewModel,
                    onExpand       = { expand() },
                    onCollapse     = { collapse() },
                    onDrag         = { dy ->
                        scope.launch {
                            val newOffset = (sheetOffsetPx.value + dy).coerceIn(0f, collapsedOffsetPx)
                            sheetOffsetPx.snapTo(newOffset)
                        }
                    },
                    onDragEnd = {
                        if (sheetOffsetPx.value < collapsedOffsetPx * DRAG_THRESHOLD) expand()
                        else collapse()
                    },
                    modifier = Modifier
                        .padding(horizontal = lerp(0.dp, 16.dp, progress.coerceIn(0f, 1f)))
                        .padding(bottom = if (progress < 0.05f) 8.dp else 0.dp)
                )
            }

            // ── Navigation bar ────────────────────────────────────────────────
            AnimatedVisibility(
                visible = showNavBar,
                enter   = slideInVertically { it } + fadeIn(),
                exit    = slideOutVertically { it } + fadeOut()
            ) {
                LiquidNavigationBar(
                    items = listOf(
                        LiquidNavItem(Screen.Home.route,      "Home",      Icons.Rounded.Home),
                        LiquidNavItem(Screen.Downloads.route, "Downloads", Icons.Rounded.Download),
                        LiquidNavItem(Screen.Library.route,   "Library",   Icons.Rounded.VideoLibrary),
                        LiquidNavItem(Screen.Favorites.route, "Favorites", Icons.Rounded.Favorite),
                    ),
                    selectedRoute = currentRoute ?: Screen.Home.route,
                    onItemClick   = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState    = true
                        }
                    }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PlayerSheet — the morphing card
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(UnstableApi::class)
@Composable
private fun PlayerSheet(
    progress:        Float,
    isExpanded:      Boolean,
    playerViewModel: OmniPlayerViewModel,
    onExpand:        () -> Unit,
    onCollapse:      () -> Unit,
    onDrag:          (Float) -> Unit,
    onDragEnd:       () -> Unit,
    modifier:        Modifier = Modifier
) {
    val density    = LocalDensity.current
    val screenH    = LocalConfiguration.current.screenHeightDp.dp

    // ── Interpolated visual properties ────────────────────────────────────────
    val cornerRadius = lerp(MINI_CORNER, 0.dp, FastOutSlowInEasing.transform(progress))
    val cardHeight   = lerp(MINI_PLAYER_HEIGHT, screenH, progress)
    val elevation    = lerp(6.dp, 0.dp, progress)

    // Content alphas
    val miniAlpha = (1f - progress * 4f).coerceIn(0f, 1f)   // fades out in first 25%
    val fullAlpha = ((progress - 0.25f) / 0.75f).coerceIn(0f, 1f) // fades in from 25%→100%

    val currentMedia by playerViewModel.currentMediaItem.collectAsStateWithLifecycle()
    val isPlaying    by playerViewModel.isPlaying.collectAsStateWithLifecycle()
    val position     by playerViewModel.currentPosition.collectAsStateWithLifecycle()
    val duration     by playerViewModel.duration.collectAsStateWithLifecycle()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(cardHeight)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount)
                    },
                    onDragEnd = { onDragEnd() }
                )
            },
        shape       = RoundedCornerShape(cornerRadius),
        color       = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = elevation,
        shadowElevation = elevation
    ) {
        Box(modifier = Modifier.fillMaxSize()) {

            // ── Full player ───────────────────────────────────────────────────
            if (fullAlpha > 0f) {
                Box(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = fullAlpha }) {
                    com.omni.app.ui.player.OmniPlayerScreen(
                        viewModel       = playerViewModel,
                        onNavigateBack  = onCollapse
                    )
                }
            }

            // ── Mini player ───────────────────────────────────────────────────
            if (miniAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = miniAlpha }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onExpand
                        )
                ) {
                    // Progress bar at very top of the card
                    if (duration > 0) {
                        LinearProgressIndicator(
                            progress = { (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .align(Alignment.TopStart),
                            color        = MaterialTheme.colorScheme.primary,
                            trackColor   = Color.Transparent
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Artwork — scales up slightly as it morphs
                        val artSize = lerp(48.dp, 56.dp, progress)
                        AsyncImage(
                            model              = currentMedia?.mediaMetadata?.artworkUri,
                            contentDescription = null,
                            modifier           = Modifier
                                .size(artSize)
                                .clip(RoundedCornerShape(lerp(8.dp, 12.dp, progress))),
                            contentScale       = ContentScale.Crop
                        )

                        Spacer(Modifier.width(12.dp))

                        // Title / artist
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentMedia?.mediaMetadata?.title?.toString() ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val rawArtist = currentMedia?.mediaMetadata?.artist?.toString()
                            val artist = when {
                                rawArtist.isNullOrBlank() || rawArtist == "Audio" || rawArtist == "Video" ->
                                    currentMedia?.mediaMetadata?.title?.toString()
                                        ?.substringBeforeLast('.') ?: ""
                                else -> rawArtist
                            }
                            Text(
                                text  = artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Skip previous
                        IconButton(onClick = { playerViewModel.playPrevious() }) {
                            Icon(Icons.Rounded.SkipPrevious, null,
                                modifier = Modifier.size(26.dp))
                        }

                        // Play / pause
                        IconButton(onClick = { playerViewModel.playPause() }) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                null,
                                modifier = Modifier.size(30.dp)
                            )
                        }

                        // Skip next
                        IconButton(onClick = { playerViewModel.playNext() }) {
                            Icon(Icons.Rounded.SkipNext, null,
                                modifier = Modifier.size(26.dp))
                        }
                    }
                }
            }
        }
    }
}
