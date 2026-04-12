package com.omni.app.ui.library

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import coil.imageLoader
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import coil.request.CachePolicy
import com.omni.app.ui.theme.LocalOmniDimensions
import com.omni.app.data.prefs.OmniPreferences
import com.omni.app.data.prefs.UserPreferences
import com.omni.app.data.download.*
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: DownloadViewModel = viewModel(),
    playerViewModel: com.omni.app.ui.player.OmniPlayerViewModel,
    onNavigateToPlayer: () -> Unit = {}
) {
    val mediaList by viewModel.scannedFiles.collectAsState()
    val context = LocalContext.current
    val prefs = remember { UserPreferences(context) }
    val settings by prefs.preferences.collectAsState(initial = OmniPreferences())
    val gridState = rememberLazyGridState()
    val dimensions = LocalOmniDimensions.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Library", 
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = MaterialTheme.typography.headlineSmall.fontSize * dimensions.titleSize
                        )
                    ) 
                }
            )
        }
    ) { padding ->
        if (mediaList.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.FolderOpen, 
                        contentDescription = null, 
                        modifier = Modifier.size(60.dp * dimensions.titleSize), 
                        tint = Color.Gray
                    )
                    Spacer(Modifier.height(16.dp * dimensions.titleSize))
                    Text("No media found", color = Color.Gray)
                }
            }
        } else {
            val columns = when (settings.uiStyle) {
                "Compact" -> GridCells.Adaptive(110.dp)
                "Expanded" -> GridCells.Adaptive(200.dp)
                else -> GridCells.Adaptive(160.dp)
            }
            
            LazyVerticalGrid(
                state = gridState,
                columns = columns,
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(dimensions.gridSpacing),
                horizontalArrangement = Arrangement.spacedBy(dimensions.gridSpacing),
                verticalArrangement = Arrangement.spacedBy(dimensions.gridSpacing)
            ) {
                items(mediaList, key = { it.id }) { media ->
                    MediaGridItem(
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
        }
    }
}

@Composable
fun MediaGridItem(media: DownloadedMedia, settings: OmniPreferences, onClick: () -> Unit, onDelete: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val dimensions = LocalOmniDimensions.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimensions.cornerRadius))
            .clickable { onClick() }
    ) {
        val isCompact = settings.uiStyle == "Compact"

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(if (isCompact) 1f else 16f / 9f)
                .clip(RoundedCornerShape(dimensions.cornerRadius))
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
                loading = { CircularProgressIndicator(modifier = Modifier.padding(24.dp)) },
                error = {
                    Icon(
                        imageVector = if (media.isAudio) Icons.Default.MusicNote else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.Gray.copy(alpha = 0.5f),
                        modifier = Modifier.align(Alignment.Center).size(40.dp)
                    )
                }
            )

            Surface(
                modifier = Modifier.align(Alignment.BottomEnd).padding(dimensions.gridSpacing / 3),
                color = Color.Black.copy(alpha = 0.7f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = if (media.isAudio) "AUDIO" else "VIDEO",
                    color = Color.White,
                    fontSize = (if (isCompact) 8.sp else 10.sp) * dimensions.titleSize,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }
        
        Spacer(Modifier.height(dimensions.gridSpacing / 2))
        
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = media.title,
                    maxLines = if (isCompact) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                    style = if (isCompact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = (if (isCompact) 12.sp else 14.sp) * dimensions.titleSize,
                    lineHeight = (if (isCompact) 14.sp else 18.sp) * dimensions.titleSize
                )
                if (settings.advancedMode) {
                    Text(
                        text = "${(File(media.filePath).length() / (1024 * 1024))} MB • ${if (media.isAudio) "Audio" else "Video"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.size(dimensions.iconSize)
            ) {
                Icon(
                    Icons.Default.MoreVert, 
                    contentDescription = "Menu", 
                    tint = Color.Gray, 
                    modifier = Modifier.size(dimensions.iconSize * 0.7f)
                )
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Share") },
                        onClick = {
                            showMenu = false
                            val file = File(media.filePath)
                            if (file.exists()) {
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = if (media.isAudio) "audio/*" else "video/*"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share Media"))
                            }
                        },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) }
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
