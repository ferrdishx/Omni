package com.omni.app.ui.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.omni.app.ui.theme.*
import androidx.compose.ui.text.font.FontFamily
import com.omni.app.data.prefs.OmniPreferences
import com.omni.app.data.prefs.UserPreferences
import com.omni.app.data.ytdlp.YtDlpManager
import com.omni.app.data.download.DownloadType
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.res.painterResource
import com.omni.app.R
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class PlatformInfo(
    val name: String,
    val urlFragment: String,
    val color: Color,
    val emoji: String,
    val iconRes: Int? = null
)

sealed class HomeSheet {
    data class Format(val type: DownloadType) : HomeSheet()
    data class Playlist(val type: DownloadType) : HomeSheet()
}

private val PLATFORMS = listOf(
    PlatformInfo("YouTube",    "youtube.com",       Color(0xFFFF0000), "▶", R.drawable.ic_youtube),
    PlatformInfo("SoundCloud", "soundcloud.com",    Color(0xFFFF5500), "☁", R.drawable.ic_soundcloud),
    PlatformInfo("Twitter/X",  "x.com",             Color(0xFF1DA1F2), "✕", R.drawable.ic_x),
    PlatformInfo("TikTok",     "tiktok.com",        Color(0xFF69C9D0), "♪", R.drawable.ic_tiktok),
    PlatformInfo("Instagram",  "instagram.com",     Color(0xFFE1306C), "◉", R.drawable.ic_instagram),
    PlatformInfo("Twitch",     "twitch.tv",         Color(0xFF9146FF), "◈", R.drawable.ic_twitch),
    PlatformInfo("Spotify",    "open.spotify.com",  Color(0xFF1DB954), "♫", R.drawable.ic_spotify),
    PlatformInfo("Vimeo",      "vimeo.com",         Color(0xFF1AB7EA), "◷", R.drawable.ic_vimeo),
    PlatformInfo("Dailymotion","dailymotion.com",   Color(0xFF0A60FF), "◑", R.drawable.ic_dailymotion),
    PlatformInfo("Reddit",     "reddit.com",        Color(0xFFFF4500), "◎", R.drawable.ic_reddit),
)

private fun detectPlatform(url: String): PlatformInfo? =
    PLATFORMS.find { url.contains(it.urlFragment, ignoreCase = true) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onSettingsClick: () -> Unit = {}
) {
    var urlText by remember { mutableStateOf("") }
    var showOptions by remember { mutableStateOf(false) }
    var activeSheet by remember { mutableStateOf<HomeSheet?>(null) }
    var isSearchFocused by remember { mutableStateOf(false) }

    val searchViewModel: SearchViewModel = viewModel()
    val searchResults by searchViewModel.searchResults.collectAsState()
    val isSearching by searchViewModel.isSearching.collectAsState()

    val isUrl = remember(urlText) {
        urlText.startsWith("http://") || urlText.startsWith("https://") ||
                PLATFORMS.any { urlText.contains(it.urlFragment, ignoreCase = true) }
    }
    val isPlaylist = remember(urlText) {
        urlText.contains("playlist?list=") || urlText.contains("&list=")
    }
    val detectedPlatform = remember(urlText) { if (urlText.isNotBlank()) detectPlatform(urlText) else null }

    val clipboard    = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current
    val context      = LocalContext.current
    val prefs        = remember { UserPreferences(context) }
    val settings     by prefs.preferences.collectAsState(initial = OmniPreferences())
    val isLowPerf    = settings.lowPerfMode
    val dimensions   = LocalOmniDimensions.current
    val scope        = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var isHoldingHero by remember { mutableStateOf(false) }

    var clickCount by remember { mutableIntStateOf(0) }
    var lastClickTime by remember { mutableLongStateOf(0L) }
    var isRocketLaunched by remember { mutableStateOf(false) }
    val rocketTranslationY = remember { Animatable(0f) }

    val shakeTransition = rememberInfiniteTransition(label = "rocketShake")
    val shakeOffset by shakeTransition.animateFloat(
        initialValue = -2f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(40, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shake"
    )

    val searchScale by animateFloatAsState(
        targetValue = if (isSearchFocused) 1.015f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "searchScale"
    )

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = dimensions.gridSpacing * 1.5f).padding(top = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val infiniteTransition = rememberInfiniteTransition(label = "betaAnim")
                val badgeAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.5f, targetValue = 1f,
                    animationSpec = infiniteRepeatable(animation = tween(1500, easing = LinearOutSlowInEasing), repeatMode = RepeatMode.Reverse), label = "badgeAlpha"
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Omni", style = MaterialTheme.typography.headlineMedium.copy(fontSize = MaterialTheme.typography.headlineMedium.fontSize * dimensions.titleSize, fontWeight = FontWeight.Black), color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(6.dp))
                    Surface(color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = badgeAlpha), shape = RoundedCornerShape(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                        Text("BETA", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
                IconButton(onClick = onSettingsClick) { Icon(Icons.Rounded.Settings, "Settings") }
            }

            OutlinedTextField(
                value = urlText,
                onValueChange = { newValue ->
                    urlText = newValue
                    val looksLikeUrl = newValue.startsWith("http") || PLATFORMS.any { newValue.contains(it.urlFragment, ignoreCase = true) }
                    when {
                        newValue.isBlank() -> { showOptions = false; searchViewModel.clearResults() }
                        looksLikeUrl       -> { searchViewModel.clearResults(); showOptions = true }
                        else               -> { showOptions = false; searchViewModel.search(newValue) }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = dimensions.gridSpacing * 1.5f).graphicsLayer(scaleX = searchScale, scaleY = searchScale).onFocusChanged { isSearchFocused = it.isFocused },
                placeholder = { Text("Search or paste link…") },
                leadingIcon = {
                    Crossfade(
                        targetState = detectedPlatform,
                        animationSpec = if (isLowPerf) snap() else tween(400),
                        label = "leadIcon"
                    ) { platform ->
                        if (platform != null) {
                            Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(platform.color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                                if (platform.iconRes != null) {
                                    Icon(painter = painterResource(platform.iconRes), contentDescription = null, tint = platform.color, modifier = Modifier.size(18.dp))
                                } else {
                                    Text(platform.emoji, color = platform.color, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        } else {
                            Crossfade(
                                targetState = isUrl,
                                animationSpec = if (isLowPerf) snap() else tween(400),
                                label = "icon_fade"
                            ) { isLink ->
                                Icon(if (isLink) Icons.Rounded.Link else Icons.Rounded.Search, null)
                            }
                        }
                    }
                },
                trailingIcon = {
                    AnimatedVisibility(
                        visible = urlText.isNotEmpty(),
                        enter = if (isLowPerf) fadeIn() else (scaleIn() + fadeIn()),
                        exit = if (isLowPerf) fadeOut() else (scaleOut() + fadeOut())
                    ) {
                        IconButton(onClick = { urlText = ""; showOptions = false; searchViewModel.clearResults() }) { Icon(Icons.Rounded.Clear, "Clear") }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(dimensions.cornerRadius + 4.dp),
                keyboardOptions = KeyboardOptions(imeAction = if (isUrl) ImeAction.Done else ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { focusManager.clearFocus(); if (urlText.isNotBlank() && !isUrl) searchViewModel.search(urlText) },
                    onDone = { focusManager.clearFocus(); if (urlText.isNotBlank() && isUrl) showOptions = true }
                )
            )

            Spacer(Modifier.height(10.dp))

            AnimatedVisibility(visible = !isUrl && searchResults.isEmpty() && !isSearching, enter = slideInVertically { -it / 2 } + fadeIn(), exit  = slideOutVertically { -it / 2 } + fadeOut()) {
                LazyRow(contentPadding = PaddingValues(horizontal = dimensions.gridSpacing * 1.5f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(PLATFORMS) { platform ->
                        PlatformQuickChip(
                            platform = platform,
                            onClick = {
                                urlText = "https://${platform.urlFragment}"; showOptions = false; searchViewModel.clearResults(); focusManager.clearFocus()
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Box(modifier = Modifier.weight(1f).padding(horizontal = dimensions.gridSpacing * 1.5f)) {
                AnimatedContent(
                    targetState = Triple(isSearching, searchResults, isUrl),
                    transitionSpec = {
                        if (isLowPerf) fadeIn(snap()) togetherWith fadeOut(snap())
                        else fadeIn(tween(400, easing = EaseInOutQuart)) togetherWith fadeOut(tween(300, easing = EaseInOutQuart))
                    },
                    label = "mainContent"
                ) { (searching, results, url) ->
                    when {
                        searching && results.isEmpty() -> { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(strokeCap = androidx.compose.ui.graphics.StrokeCap.Round) } }
                        results.isNotEmpty() && !url -> {
                            LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 80.dp)) {
                                items(results, key = { it.id }) { result ->
                                    SearchListItem(
                                        result = result,
                                        onClick = { urlText = result.url; showOptions = true; focusManager.clearFocus() }
                                    )
                                }
                            }
                        }
                        else -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .offset(y = (-40).dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    AnimatedVisibility(
                                        visible = detectedPlatform != null && isUrl,
                                        enter = if (isLowPerf) fadeIn() else (scaleIn(spring(Spring.DampingRatioLowBouncy)) + fadeIn()),
                                        exit = if (isLowPerf) fadeOut() else (scaleOut() + fadeOut())
                                    ) {
                                        if (detectedPlatform != null) {
                                            Surface(modifier = Modifier.padding(bottom = 16.dp), shape = RoundedCornerShape(20.dp), color = detectedPlatform.color.copy(alpha = 0.12f), border = BorderStroke(1.dp, detectedPlatform.color.copy(alpha = 0.3f))) {
                                                Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    if (detectedPlatform.iconRes != null) Icon(painter = painterResource(detectedPlatform.iconRes), contentDescription = null, tint = detectedPlatform.color, modifier = Modifier.size(16.dp))
                                                    else Icon(Icons.Rounded.CheckCircle, null, tint = detectedPlatform.color, modifier = Modifier.size(15.dp))
                                                    Text(detectedPlatform.name, style = MaterialTheme.typography.labelLarge, color = detectedPlatform.color)
                                                    if (isPlaylist) {
                                                        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                                                            Text("Playlist", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        val iconTranslationY by animateFloatAsState(
                                            targetValue = if (isHoldingHero && !isLowPerf) 8f else 0f,
                                            animationSpec = spring(Spring.DampingRatioMediumBouncy),
                                            label = "iconSinking"
                                        )

                                        val iconScale by animateFloatAsState(
                                            targetValue = if (isHoldingHero && !isLowPerf) 0.88f else if (showOptions && !isLowPerf) 0.82f else 1f,
                                            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium),
                                            label = "iconScale"
                                        )

                                        val currentRocketShake = if (clickCount in 7..9 && !isRocketLaunched) shakeOffset else 0f

                                        Surface(
                                            modifier = Modifier
                                                .size(80.dp * dimensions.titleSize)
                                                .graphicsLayer {
                                                    scaleX = iconScale
                                                    scaleY = iconScale
                                                    translationY = (iconTranslationY + currentRocketShake + rocketTranslationY.value).dp.toPx()
                                                }
                                                .pointerInput(Unit) {
                                                    detectTapGestures(
                                                        onPress = {
                                                            isHoldingHero = true
                                                            tryAwaitRelease()
                                                            isHoldingHero = false
                                                        },
                                                        onTap = {
                                                            if (isRocketLaunched) return@detectTapGestures
                                                            val now = System.currentTimeMillis()
                                                            if (now - lastClickTime > 1000) clickCount = 1 else clickCount++
                                                            lastClickTime = now

                                                            if (clickCount >= 10) {
                                                                scope.launch {
                                                                    isRocketLaunched = true
                                                                    rocketTranslationY.animateTo(
                                                                        targetValue = -1000f,
                                                                        animationSpec = tween(800, easing = EaseIn)
                                                                    )
                                                                    delay(2000)

                                                                    rocketTranslationY.snapTo(100f)
                                                                    clickCount = 0
                                                                    isRocketLaunched = false
                                                                    rocketTranslationY.animateTo(
                                                                        targetValue = 0f,
                                                                        animationSpec = spring(
                                                                            dampingRatio = Spring.DampingRatioLowBouncy,
                                                                            stiffness = Spring.StiffnessMediumLow
                                                                        )
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    )
                                                },
                                            shape = RoundedCornerShape(24.dp * dimensions.titleSize),
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            tonalElevation = 4.dp
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Crossfade(targetState = isRocketLaunched, label = "rocketIcon") { launched ->
                                                    Icon(
                                                        if (launched) Icons.Rounded.RocketLaunch else Icons.Rounded.DownloadForOffline,
                                                        null,
                                                        modifier = Modifier.size(40.dp * dimensions.titleSize),
                                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(Modifier.height(24.dp))

                                        val phrases = remember {
                                            listOf(
                                                "Find anything to download" to FontFamily.Default,
                                                "Paste a link to start" to InterFont,
                                                "Your media, anywhere" to SerifFont,
                                                "Simple. Fast. Omni." to MonospaceFont,
                                                "Ready for your next favorite?" to PoppinsFont,
                                                "What are we saving today?" to RobotoFont,
                                                "Pure magic, zero effort" to InterFont,
                                                "Everything in one place" to SerifFont,
                                                "Capture the moment" to MonospaceFont,
                                                "Your library is waiting" to PoppinsFont
                                            )
                                        }
                                        var phraseIndex by remember { mutableIntStateOf(0) }
                                        LaunchedEffect(showOptions) { if (!showOptions) { while (true) { kotlinx.coroutines.delay(4500); phraseIndex = (phraseIndex + 1) % phrases.size } } }

                                        val currentPhrase = if (showOptions) "Choose format" to FontFamily.Default else phrases[phraseIndex]

                                        AnimatedContent(
                                            targetState = currentPhrase,
                                            transitionSpec = {
                                                if (isLowPerf) fadeIn(snap()) togetherWith fadeOut(snap())
                                                else fadeIn(tween(800)) + scaleIn(initialScale = 0.96f) togetherWith fadeOut(tween(800)) + scaleOut(targetScale = 1.04f)
                                            },
                                            label = "titleFade"
                                        ) { (text, fontFamily) ->
                                            Text(
                                                text = text,
                                                style = MaterialTheme.typography.headlineMedium.copy(
                                                    fontSize = MaterialTheme.typography.headlineMedium.fontSize * dimensions.titleSize,
                                                    fontWeight = if (fontFamily == MonospaceFont) FontWeight.Normal else FontWeight.Black,
                                                    letterSpacing = (-0.5).sp,
                                                    fontFamily = fontFamily
                                                ),
                                                textAlign = TextAlign.Center,
                                                color = MaterialTheme.colorScheme.onBackground
                                            )
                                        }

                                        Spacer(Modifier.height(24.dp))

                                        AnimatedContent(
                                            targetState = showOptions,
                                            transitionSpec = {
                                                if (isLowPerf) fadeIn(snap()) togetherWith fadeOut(snap())
                                                else (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut())
                                            },
                                            label = "optionsAnim"
                                        ) { show ->
                                            if (show) ActionButtons(
                                                settings = settings,
                                                onVideoClick = {
                                                    activeSheet = if (isPlaylist) HomeSheet.Playlist(DownloadType.VIDEO) else HomeSheet.Format(DownloadType.VIDEO)
                                                },
                                                onAudioClick = {
                                                    activeSheet = if (isPlaylist) HomeSheet.Playlist(DownloadType.AUDIO) else HomeSheet.Format(DownloadType.AUDIO)
                                                },
                                                onPlaylistClick = if (isPlaylist) { { activeSheet = HomeSheet.Playlist(DownloadType.VIDEO) } } else null
                                            )
                                            else PasteButton(clipboard = clipboard, onPaste = { urlText = it; showOptions = true })
                                        }
                                    }
                                }
                        }
                    }
                }
            }
        }
    }

    when (val sheet = activeSheet) {
        is HomeSheet.Format -> {
            DownloadFormatSheet(
                url = urlText,
                type = if (sheet.type == DownloadType.AUDIO) "audio" else "video",
                settings = settings,
                onViewPlaylist = if (isPlaylist) { { activeSheet = HomeSheet.Playlist(sheet.type) } } else null,
                onDismiss = { activeSheet = null }
            )
        }
        is HomeSheet.Playlist -> {
            PlaylistDownloadSheet(
                url = urlText,
                settings = settings,
                initialType = sheet.type,
                onDismiss = { activeSheet = null }
            )
        }
        null -> {}
    }
}

@Composable
fun PlatformQuickChip(
    platform: PlatformInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = platform.color.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, platform.color.copy(alpha = 0.28f))
    ) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (platform.iconRes != null) Icon(painter = painterResource(id = platform.iconRes), contentDescription = null, tint = platform.color, modifier = Modifier.size(16.dp))
            else Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(platform.color))
            Text(platform.name, style = MaterialTheme.typography.labelMedium, color = platform.color)
        }
    }
}

@Composable
fun SearchListItem(
    result: YtDlpManager.SearchResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dimensions = LocalOmniDimensions.current
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val scale by animateFloatAsState(targetValue = if (visible) 1f else 0.95f, animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy))
    Row(modifier = modifier.fillMaxWidth().graphicsLayer(scaleX = scale, scaleY = scale).clip(RoundedCornerShape(dimensions.cornerRadius)).clickable { onClick() }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(120.dp, 68.dp).clip(RoundedCornerShape(12.dp))) {
            AsyncImage(model = result.thumbnailUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Surface(modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp), color = Color.Black.copy(alpha = 0.72f), shape = RoundedCornerShape(6.dp)) {
                Text(text = formatDuration(result.duration), color = Color.White, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(text = result.title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(text = result.uploader ?: "Unknown", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val h = seconds / 3600; val m = (seconds % 3600) / 60; val s = seconds % 60
    return if (h > 0) String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, s) else String.format(java.util.Locale.US, "%02d:%02d", m, s)
}

@Composable
fun ActionButtons(
    settings: OmniPreferences,
    onVideoClick: () -> Unit,
    onAudioClick: () -> Unit,
    modifier: Modifier = Modifier,
    onPlaylistClick: (() -> Unit)? = null
) {
    val dimensions = LocalOmniDimensions.current
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(dimensions.gridSpacing)) {
        if (onPlaylistClick != null) {
            Button(onClick = onPlaylistClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(dimensions.cornerRadius), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary), contentPadding = PaddingValues(dimensions.itemPadding)) {
                Icon(Icons.Rounded.PlaylistPlay, null, modifier = Modifier.size(dimensions.iconSize * 0.85f))
                Spacer(Modifier.width(10.dp))
                Column { Text("Download Playlist", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text("Batch download multiple items", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onTertiary.copy(alpha = 0.75f)) }
                Spacer(Modifier.weight(1f)); Icon(Icons.Rounded.ChevronRight, null, modifier = Modifier.size(dimensions.iconSize * 0.75f))
            }
        }
        Button(onClick = onVideoClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(dimensions.cornerRadius), contentPadding = PaddingValues(dimensions.itemPadding)) {
            Icon(Icons.Rounded.Videocam, null, modifier = Modifier.size(dimensions.iconSize * 0.85f))
            Spacer(Modifier.width(10.dp))
            Column { Text("Download Video", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text("${settings.videoQuality} · ${settings.videoFormat}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f)) }
            Spacer(Modifier.weight(1f)); Icon(Icons.Rounded.ChevronRight, null, modifier = Modifier.size(dimensions.iconSize * 0.75f))
        }
        OutlinedButton(onClick = onAudioClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(dimensions.cornerRadius), contentPadding = PaddingValues(dimensions.itemPadding)) {
            Icon(Icons.Rounded.AudioFile, null, modifier = Modifier.size(dimensions.iconSize * 0.85f))
            Spacer(Modifier.width(10.dp))
            Column { Text("Download Audio", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text("${settings.audioFormat} · ${settings.audioQuality}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)) }
            Spacer(Modifier.weight(1f)); Icon(Icons.Rounded.ChevronRight, null, modifier = Modifier.size(dimensions.iconSize * 0.75f))
        }
    }
}

@Composable
fun PasteButton(
    clipboard: androidx.compose.ui.platform.ClipboardManager,
    onPaste: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val dimensions = LocalOmniDimensions.current
    FilledTonalButton(
        onClick = { val text = clipboard.getText()?.text; if (!text.isNullOrBlank()) onPaste(text) },
        modifier = modifier,
        shape = RoundedCornerShape(dimensions.cornerRadius)
    ) {
        Icon(Icons.Rounded.ContentPaste, null, modifier = Modifier.size(dimensions.iconSize * 0.75f)); Spacer(Modifier.width(8.dp)); Text("Paste link")
    }
}
