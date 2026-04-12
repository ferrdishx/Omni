package com.omni.app.ui.downloads

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.omni.app.ui.theme.LocalOmniDimensions
import com.omni.app.data.prefs.OmniPreferences
import com.omni.app.data.prefs.UserPreferences
import com.omni.app.data.download.*
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    viewModel: DownloadViewModel = viewModel(),
    playerViewModel: com.omni.app.ui.player.OmniPlayerViewModel,
    onNavigateToPlayer: () -> Unit = {}
) {
    val activeDownloads by viewModel.activeDownloads.collectAsState()
    val scannedFiles by viewModel.scannedFiles.collectAsState()
    val context = LocalContext.current
    val prefs = remember { UserPreferences(context) }
    val settings by prefs.preferences.collectAsState(initial = OmniPreferences())
    val listState = rememberLazyListState()
    val dimensions = LocalOmniDimensions.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Downloads", 
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = MaterialTheme.typography.headlineSmall.fontSize * dimensions.titleSize
                        )
                    ) 
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = dimensions.gridSpacing),
            verticalArrangement = Arrangement.spacedBy(dimensions.gridSpacing)
        ) {
            if (activeDownloads.isNotEmpty()) {
                val batchGroups = activeDownloads.groupBy { it.batchId }.filterKeys { it != null }
                
                if (batchGroups.isNotEmpty()) {
                    items(batchGroups.keys.toList().filterNotNull()) { batchId ->
                        val items = batchGroups[batchId] ?: emptyList()
                        val completedCount = items.count { it.status == DownloadStatus.COMPLETED }
                        val totalCount = items.size
                        val avgProgress = items.map { it.progress }.average().toFloat() / 100f
                        
                        BatchProgressHeader(
                            completedCount = completedCount,
                            totalCount = totalCount,
                            progress = avgProgress,
                            dimensions = dimensions
                        )
                    }
                }

                item {
                    Text(
                        "Active downloads",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                items(activeDownloads, key = { it.id }) { item ->
                    ActiveDownloadItem(item, settings = settings, onCancel = { viewModel.cancel(item.id) })
                }
            }

            if (scannedFiles.isNotEmpty()) {
                item {
                    Text(
                        "Recently completed",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                    )
                }
                items(scannedFiles, key = { it.id }) { media ->
                    CompletedMediaItem(
                        media = media,
                        settings = settings,
                        onClick = {
                            val download = DownloadEntity(
                                id = media.id,
                                title = media.title,
                                filePath = media.filePath,
                                thumbnail = media.thumbnailUrl,
                                type = if (media.isAudio) "audio" else "video",
                                quality = "Unknown",
                                format = media.format,
                                size = "Unknown"
                            )
                            playerViewModel.play(download)
                            onNavigateToPlayer()
                        },
                        onDelete = { viewModel.deleteMedia(media) }
                    )
                }
            }

            if (activeDownloads.isEmpty() && scannedFiles.isEmpty()) {
                item {
                    Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No downloads yet", color = Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
fun BatchProgressHeader(
    completedCount: Int,
    totalCount: Int,
    progress: Float,
    dimensions: com.omni.app.ui.theme.OmniDimensions
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(dimensions.cornerRadius),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, 
            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Playlist Progress",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "$completedCount of $totalCount items completed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(Modifier.height(12.dp))
            
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape),
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                strokeCap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun ActiveDownloadItem(item: DownloadItem, settings: OmniPreferences, onCancel: () -> Unit) {
    val dimensions = LocalOmniDimensions.current
    val isCompact = settings.uiStyle == "Compact"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(dimensions.cornerRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(dimensions.itemPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(if (isCompact) 40.dp else 56.dp)
                    .clip(RoundedCornerShape(dimensions.cornerRadius / 2))
                    .background(Color.DarkGray)
            ) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(item.thumbnailUrl)
                        .apply {
                            if (settings.lowPerfMode) {
                                size(200, 200)
                            }
                            if (settings.reduceAnimations) {
                                crossfade(false)
                            } else {
                                crossfade(true)
                            }
                        }
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    loading = { CircularProgressIndicator(modifier = Modifier.padding(12.dp), strokeWidth = 2.dp) },
                    error = { Icon(Icons.Default.Download, contentDescription = null, tint = Color.Gray, modifier = Modifier.align(Alignment.Center)) }
                )
            }
            Spacer(Modifier.width(if (isCompact) 8.dp else 12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, maxLines = if (isCompact) 1 else 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium,
                    style = if (isCompact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                    fontSize = (if (isCompact) 12.sp else 14.sp) * dimensions.titleSize)
                Spacer(Modifier.height(if (isCompact) 4.dp else 8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isCompact) 2.dp else 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                ) {
                    LinearProgressIndicator(
                        progress = { item.progress / 100f },
                        modifier = Modifier.fillMaxSize(),
                    )
                    
                    if (item.status == DownloadStatus.DOWNLOADING && !settings.reduceAnimations) {
                        ShimmerProgress(Modifier.fillMaxSize())
                    }
                }
                if (!isCompact || settings.showDetailedInfo) {
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            if (item.progress <= 0f && item.status == DownloadStatus.DOWNLOADING) "Starting download..." 
                            else "${item.progress.toInt()}%", 
                            style = MaterialTheme.typography.labelSmall, 
                            color = Color.Gray
                        )
                        if (settings.showDetailedInfo) {
                            Text("${item.speed} • ${item.eta}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        } else {
                            Text(item.speed, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        }
                    }
                }
            }
            IconButton(onClick = onCancel, modifier = Modifier.size(dimensions.iconSize * 1.5f)) {
                Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.Gray, modifier = Modifier.size(dimensions.iconSize))
            }
        }
    }
}

@Composable
fun CompletedMediaItem(media: DownloadedMedia, settings: OmniPreferences, onClick: () -> Unit, onDelete: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val dimensions = LocalOmniDimensions.current
    val isCompact = settings.uiStyle == "Compact"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimensions.cornerRadius))
            .clickable { onClick() },
        shape = RoundedCornerShape(dimensions.cornerRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(dimensions.itemPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(if (isCompact) 48.dp else 64.dp)
                    .clip(RoundedCornerShape(dimensions.cornerRadius / 2))
                    .background(Color.DarkGray)
            ) {
                val model = remember(media.thumbnailUrl, media.filePath, settings.lowPerfMode, settings.reduceAnimations) {
                    val thumbFile = if (media.thumbnailUrl != null && !media.thumbnailUrl.startsWith("http")) 
                        File(media.thumbnailUrl) else null
                    
                    ImageRequest.Builder(context)
                        .data(
                            when {
                                thumbFile?.exists() == true -> thumbFile
                                media.thumbnailUrl?.startsWith("http") == true -> media.thumbnailUrl
                                !media.isAudio -> File(media.filePath)
                                else -> null
                            }
                        )
                        .apply {
                            if (!media.isAudio) {
                                decoderFactory(VideoFrameDecoder.Factory())
                                setParameter("coil#video_frame_micros", 2000000L)
                            }
                            
                            if (settings.lowPerfMode) {
                                size(200, 200)
                            }
                            
                            if (settings.reduceAnimations) {
                                crossfade(false)
                            } else {
                                crossfade(true)
                            }
                        }
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .build()
                }

                SubcomposeAsyncImage(
                    model = model,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    error = {
                        Icon(
                            imageVector = if (media.isAudio) Icons.Default.MusicNote else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.align(Alignment.Center).size(30.dp)
                        )
                    }
                )
                
                if (media.timestamp > System.currentTimeMillis() - 60000) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text("NEW", fontSize = 8.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.width(if (isCompact) 8.dp else 12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    media.title, 
                    maxLines = if (isCompact) 1 else 2, 
                    overflow = TextOverflow.Ellipsis, 
                    fontWeight = FontWeight.Medium, 
                    fontSize = (if (isCompact) 13.sp else 14.sp) * dimensions.titleSize
                )
                Text(
                    if (media.isAudio) "Audio" else "Video", 
                    style = MaterialTheme.typography.bodySmall, 
                    color = Color.Gray, 
                    fontSize = (if (isCompact) 11.sp else 12.sp) * dimensions.titleSize
                )
                if (settings.showDetailedInfo) {
                    val sizeMB = File(media.filePath).length() / (1024 * 1024)
                    Text(
                        "$sizeMB MB • ${media.format}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray.copy(alpha = 0.7f),
                        fontSize = 10.sp * dimensions.titleSize
                    )
                }
            }
            
            Box {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(dimensions.iconSize * 1.5f)) {
                    Icon(
                        Icons.Default.CheckCircle, 
                        contentDescription = "Options", 
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), 
                        modifier = Modifier.size(dimensions.iconSize)
                    )
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Play") },
                        onClick = { showMenu = false; onClick() },
                        leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = Color.Red) },
                        onClick = { showMenu = false; onDelete() },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) }
                    )
                }
            }
        }
    }
}

@Composable
fun ShimmerProgress(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = -500f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )

    val shimmerColors = listOf(
        Color.White.copy(alpha = 0.0f),
        Color.White.copy(alpha = 0.4f),
        Color.White.copy(alpha = 0.0f),
    )

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnim, 0f),
        end = Offset(translateAnim + 300f, 0f)
    )

    Box(modifier = modifier.background(brush))
}
