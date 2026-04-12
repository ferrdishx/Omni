package com.omni.app.ui.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.omni.app.ui.theme.LocalOmniDimensions
import com.omni.app.data.prefs.OmniPreferences
import com.omni.app.data.prefs.UserPreferences
import com.omni.app.data.ytdlp.YtDlpManager
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onSettingsClick: () -> Unit = {}) {
    var urlText by remember { mutableStateOf("") }
    var showOptions by remember { mutableStateOf(false) }
    var showFormatSheet by remember { mutableStateOf(false) }
    var showPlaylistSheet by remember { mutableStateOf(false) }
    var downloadType by remember { mutableStateOf("") }

    val searchViewModel: SearchViewModel = viewModel()
    val searchResults by searchViewModel.searchResults.collectAsState()
    val isSearching by searchViewModel.isSearching.collectAsState()

    val isUrl = remember(urlText) {
        urlText.startsWith("http://") || urlText.startsWith("https://") || urlText.contains(".")
    }

    val isPlaylist = remember(urlText) {
        urlText.contains("playlist?list=") || urlText.contains("&list=")
    }

    val clipboard = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val prefs = remember { UserPreferences(context) }
    val settings by prefs.preferences.collectAsState(initial = OmniPreferences())
    val dimensions = LocalOmniDimensions.current

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = dimensions.gridSpacing * 1.5f),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "beta_anim")
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.5f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1500, easing = LinearOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "alpha"
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Omni",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = MaterialTheme.typography.headlineMedium.fontSize * dimensions.titleSize,
                        fontWeight = FontWeight.Black
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(6.dp))
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = alpha),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        "BETA",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }

            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Rounded.Settings, "Settings")
            }
        }

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = urlText,
            onValueChange = { newValue ->
                urlText = newValue
                val looksLikeUrl = newValue.startsWith("http") || 
                                  newValue.contains("youtube.com") || 
                                  newValue.contains("youtu.be")
                
                if (newValue.isBlank()) {
                    showOptions = false
                    searchViewModel.clearResults()
                } else if (looksLikeUrl) {
                    searchViewModel.clearResults()
                    showOptions = true
                } else {
                    showOptions = false
                    searchViewModel.search(newValue)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search or paste link...") },
            leadingIcon = { 
                Crossfade(targetState = isUrl, label = "icon_fade") { isLink ->
                    Icon(if (isLink) Icons.Rounded.Link else Icons.Rounded.Search, null) 
                }
            },
            trailingIcon = {
                AnimatedVisibility(
                    visible = urlText.isNotEmpty(),
                    enter = scaleIn() + fadeIn(),
                    exit = scaleOut() + fadeOut()
                ) {
                    IconButton(onClick = { 
                        urlText = ""
                        showOptions = false
                        searchViewModel.clearResults()
                    }) {
                        Icon(Icons.Rounded.Clear, "Clear")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(dimensions.cornerRadius),
            keyboardOptions = KeyboardOptions(imeAction = if (isUrl) ImeAction.Done else ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    focusManager.clearFocus()
                    if (urlText.isNotBlank() && !isUrl) {
                        searchViewModel.search(urlText)
                    }
                },
                onDone = {
                    focusManager.clearFocus()
                    if (urlText.isNotBlank() && isUrl) {
                        showOptions = true
                    }
                }
            )
        )

        Spacer(Modifier.height(16.dp))

        Box(modifier = Modifier.weight(1f)) {
            AnimatedContent(
                targetState = Triple(isSearching, searchResults, isUrl),
                transitionSpec = {
                    fadeIn(animationSpec = tween(400, easing = EaseInOutQuart)) togetherWith
                    fadeOut(animationSpec = tween(300, easing = EaseInOutQuart))
                },
                label = "main_content_anim"
            ) { (searching, results, url) ->
                if (searching && results.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(strokeCap = androidx.compose.ui.graphics.StrokeCap.Round)
                    }
                } else if (results.isNotEmpty() && !url) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(results, key = { it.id }) { result ->
                            SearchListItem(
                                modifier = Modifier.animateItem(),
                                result = result,
                                onClick = {
                                    urlText = result.url
                                    showOptions = true
                                    focusManager.clearFocus()
                                }
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            modifier = Modifier.size(80.dp * dimensions.titleSize),
                            shape = RoundedCornerShape(24.dp * dimensions.titleSize),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            tonalElevation = 4.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.DownloadForOffline, null,
                                    modifier = Modifier.size(40.dp * dimensions.titleSize),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }

                        Spacer(Modifier.height(20.dp))

                        AnimatedContent(targetState = showOptions, label = "title_fade") { targetShow ->
                            Text(
                                text = if (targetShow) "Choose format" else "Find videos to download",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontSize = MaterialTheme.typography.headlineMedium.fontSize * dimensions.titleSize,
                                    fontWeight = FontWeight.Bold
                                ),
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        
                        Spacer(Modifier.height(24.dp))

                        AnimatedContent(
                            targetState = showOptions,
                            transitionSpec = {
                                (slideInVertically { it / 2 } + fadeIn()).togetherWith(slideOutVertically { -it / 2 } + fadeOut())
                            },
                            label = "options_anim"
                        ) { targetShowOptions ->
                            if (targetShowOptions) {
                                ActionButtons(
                                    settings = settings,
                                    onVideoClick = { 
                                        downloadType = "video"
                                        if (isPlaylist) showPlaylistSheet = true else showFormatSheet = true 
                                    },
                                    onAudioClick = { 
                                        downloadType = "audio"
                                        if (isPlaylist) showPlaylistSheet = true else showFormatSheet = true 
                                    },
                                    onPlaylistClick = if (isPlaylist) { { showPlaylistSheet = true } } else null
                                )
                            } else {
                                PasteButton(clipboard = clipboard, onPaste = { 
                                    urlText = it
                                    showOptions = true 
                                })
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFormatSheet) {
        DownloadFormatSheet(vm = viewModel(),
            url = urlText,
            type = downloadType,
            settings = settings,
            onViewPlaylist = if (isPlaylist) { { showFormatSheet = false; showPlaylistSheet = true } } else null,
            onDismiss = { showFormatSheet = false }
        )
    }

    if (showPlaylistSheet) {
        PlaylistDownloadSheet(
            url = urlText,
            settings = settings,
            initialType = if (downloadType == "audio") com.omni.app.data.download.DownloadType.AUDIO else com.omni.app.data.download.DownloadType.VIDEO,
            onDismiss = { showPlaylistSheet = false }
        )
    }
}

@Composable
fun SearchListItem(
    modifier: Modifier = Modifier,
    result: YtDlpManager.SearchResult, 
    onClick: () -> Unit
) {
    val dimensions = LocalOmniDimensions.current

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.95f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
        label = "scale"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clip(RoundedCornerShape(dimensions.cornerRadius))
            .clickable { onClick() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(120.dp, 68.dp).clip(RoundedCornerShape(12.dp))) {
            AsyncImage(
                model = result.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Surface(
                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
                color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = formatDuration(result.duration),
                    color = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        Spacer(Modifier.width(12.dp))
        
        Column {
            Text(
                text = result.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = result.uploader ?: "Unknown",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) {
        String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(java.util.Locale.US, "%02d:%02d", m, s)
    }
}

@Composable
fun ActionButtons(
    settings: OmniPreferences,
    onVideoClick: () -> Unit,
    onAudioClick: () -> Unit,
    onPlaylistClick: (() -> Unit)? = null
) {
    val dimensions = LocalOmniDimensions.current
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(dimensions.gridSpacing)) {
        if (onPlaylistClick != null) {
            Button(
                onClick = onPlaylistClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(dimensions.cornerRadius),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                contentPadding = PaddingValues(dimensions.itemPadding)
            ) {
                Icon(Icons.Rounded.PlaylistPlay, null, modifier = Modifier.size(dimensions.iconSize * 0.85f))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Download Playlist", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Batch download multiple items", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onTertiary.copy(alpha = 0.75f))
                }
                Spacer(Modifier.weight(1f))
                Icon(Icons.Rounded.ChevronRight, null, modifier = Modifier.size(dimensions.iconSize * 0.75f))
            }
        }

        Button(
            onClick = onVideoClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(dimensions.cornerRadius),
            contentPadding = PaddingValues(dimensions.itemPadding)
        ) {
            Icon(Icons.Rounded.Videocam, null, modifier = Modifier.size(dimensions.iconSize * 0.85f))
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Download Video", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("${settings.videoQuality} • ${settings.videoFormat}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f))
            }
            Spacer(Modifier.weight(1f))
            Icon(Icons.Rounded.ChevronRight, null, modifier = Modifier.size(dimensions.iconSize * 0.75f))
        }

        OutlinedButton(
            onClick = onAudioClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(dimensions.cornerRadius),
            contentPadding = PaddingValues(dimensions.itemPadding)
        ) {
            Icon(Icons.Rounded.AudioFile, null, modifier = Modifier.size(dimensions.iconSize * 0.85f))
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Download Audio", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("${settings.audioFormat} • ${settings.audioQuality}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            Spacer(Modifier.weight(1f))
            Icon(Icons.Rounded.ChevronRight, null, modifier = Modifier.size(dimensions.iconSize * 0.75f))
        }
    }
}

@Composable
fun PasteButton(clipboard: androidx.compose.ui.platform.ClipboardManager, onPaste: (String) -> Unit) {
    val dimensions = LocalOmniDimensions.current
    FilledTonalButton(
        onClick = {
            val text = clipboard.getText()?.text
            if (!text.isNullOrBlank()) onPaste(text)
        },
        shape = RoundedCornerShape(dimensions.cornerRadius)
    ) {
        Icon(Icons.Rounded.ContentPaste, null, modifier = Modifier.size(dimensions.iconSize * 0.75f))
        Spacer(Modifier.width(8.dp))
        Text("Paste link")
    }
}
