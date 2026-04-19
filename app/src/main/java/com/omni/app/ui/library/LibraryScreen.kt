package com.omni.app.ui.library

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import coil.imageLoader
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import com.omni.app.data.favorites.FavoriteViewModel
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: DownloadViewModel = viewModel(),
    favoriteViewModel: FavoriteViewModel = viewModel(),
    playerViewModel: com.omni.app.ui.player.OmniPlayerViewModel,
    onNavigateToPlayer: () -> Unit = {}
) {
    val mediaList by viewModel.filteredFiles.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val typeFilter by viewModel.typeFilter.collectAsState()
    
    val context = LocalContext.current
    val prefs = remember { UserPreferences(context) }
    val settings by prefs.preferences.collectAsState(initial = OmniPreferences())
    val gridState = rememberLazyGridState()
    val dimensions = LocalOmniDimensions.current

    var isSearching by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    if (isSearching) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = { Text("Search in library...") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            singleLine = true,
                            trailingIcon = {
                                IconButton(onClick = { 
                                    viewModel.setSearchQuery("")
                                    isSearching = false 
                                }) {
                                    Icon(Icons.Rounded.Close, null)
                                }
                            }
                        )
                    } else {
                        Text(
                            "Library", 
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontSize = MaterialTheme.typography.headlineSmall.fontSize * dimensions.titleSize
                            )
                        )
                    }
                },
                actions = {
                    if (!isSearching) {
                        IconButton(onClick = { isSearching = true }) {
                            Icon(Icons.Rounded.Search, "Search")
                        }
                        
                        var showSortMenu by remember { mutableStateOf(false) }
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Rounded.FilterList, "Sort & Filter")
                        }

                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            Text(
                                "  Sort by",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                            listOf("Date" to "Date added", "Name" to "Title", "Size" to "File size").forEach { (key, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = { 
                                        viewModel.setSortOrder(key)
                                        showSortMenu = false 
                                    },
                                    leadingIcon = { 
                                        if (sortOrder == key) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary)
                                        else Spacer(Modifier.size(24.dp))
                                    }
                                )
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            
                            Text(
                                "  Filter",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                            listOf("All" to "All Media", "Audio" to "Audio only", "Video" to "Video only").forEach { (key, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = { 
                                        viewModel.setTypeFilter(key)
                                        showSortMenu = false 
                                    },
                                    leadingIcon = { 
                                        if (typeFilter == key) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary)
                                        else {
                                            when(key) {
                                                "Audio" -> Icon(Icons.Rounded.AudioFile, null, modifier = Modifier.size(18.dp))
                                                "Video" -> Icon(Icons.Rounded.Videocam, null, modifier = Modifier.size(18.dp))
                                                else -> Spacer(Modifier.size(24.dp))
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (mediaList.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Rounded.FolderOpen, 
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
                contentPadding = PaddingValues(
                    start = dimensions.gridSpacing, 
                    top = dimensions.gridSpacing, 
                    end = dimensions.gridSpacing, 
                    bottom = dimensions.gridSpacing + 170.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(dimensions.gridSpacing),
                verticalArrangement = Arrangement.spacedBy(dimensions.gridSpacing)
            ) {
                items(mediaList, key = { it.filePath }) { media ->
                    val isFav by favoriteViewModel.isFavorite(media.id).collectAsState(initial = false)
                    MediaGridItem(
                        media = media,
                        settings = settings,
                        isFavorite = isFav,
                        onFavoriteToggle = { favoriteViewModel.toggle(media) },
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
fun MediaGridItem(media: DownloadedMedia, settings: OmniPreferences, isFavorite: Boolean = false, onFavoriteToggle: () -> Unit = {}, onClick: () -> Unit, onDelete: () -> Unit) {
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
                val thumbFile = if (media.thumbnailUrl != null) File(media.thumbnailUrl) else null
                
                // 1. Tenta encontrar a thumbnail na pasta .thumbaudios ou .thumbaudio
                val resolvedThumbFile = when {
                    thumbFile?.exists() == true -> {
                        Log.d("OmniThumb", "Usando thumb do DB: ${thumbFile.absolutePath}")
                        thumbFile
                    }
                    else -> {
                        val file = File(media.filePath)
                        val downloadDir = file.parentFile
                        val baseName = file.nameWithoutExtension
                        val possibleFolders = listOf(".thumbaudio", ".thumbaudios")
                        
                        var found: File? = null
                        for (folder in possibleFolders) {
                            // Tenta no diretório Omni (um nível acima) ou no diretório atual
                            val folderPaths = listOf(
                                File(downloadDir, folder),
                                File(downloadDir?.parentFile, folder)
                            )
                            
                            for (folderPath in folderPaths) {
                                val f = File(folderPath, "$baseName.jpg")
                                Log.d("OmniThumb", "Procurando em: ${f.absolutePath}")
                                if (f.exists()) {
                                    Log.d("OmniThumb", "✓ Encontrada em: ${f.absolutePath}")
                                    found = f
                                    break
                                }
                            }
                            if (found != null) break
                        }
                        found
                    }
                }

                if (resolvedThumbFile == null) {
                    Log.w("OmniThumb", "✗ Nenhuma thumb encontrada para: ${media.title}. Usando fallback do arquivo.")
                }

                ImageRequest.Builder(context)
                    // 2. Se não achou na pasta, usa o arquivo de mídia (Coil extrai arte do áudio/vídeo automaticamente)
                    .data(resolvedThumbFile ?: File(media.filePath))
                    .apply {
                        if (media.isAudio) {
                            // Deixa o Coil extrair a arte ID3 do áudio
                        } else {
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
                        imageVector = if (media.isAudio) Icons.Rounded.MusicNote else Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = Color.Gray.copy(alpha = 0.5f),
                        modifier = Modifier.align(Alignment.Center).size(40.dp)
                    )
                }
            )

            // Favorite heart overlay
            val favoriteScale by animateFloatAsState(
                targetValue = if (isFavorite) 1.2f else 0f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                label = "favoriteScale"
            )

            if (favoriteScale > 0.01f) {
                Icon(
                    Icons.Rounded.Favorite, null,
                    tint = Color.Red,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(16.dp)
                        .graphicsLayer {
                            scaleX = favoriteScale
                            scaleY = favoriteScale
                        }
                )
            }
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
                    text = if (settings.showDetailedInfo) media.title else media.title.substringBeforeLast('.'),
                    maxLines = if (isCompact) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                    style = if (isCompact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = (if (isCompact) 12.sp else 14.sp) * dimensions.titleSize,
                    lineHeight = (if (isCompact) 14.sp else 18.sp) * dimensions.titleSize
                )
                Text(
                    text = media.author,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    fontSize = (if (isCompact) 10.sp else 12.sp) * dimensions.titleSize,
                    modifier = Modifier.padding(top = 1.dp)
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
                    Icons.Rounded.MoreVert, 
                    contentDescription = "Menu", 
                    tint = Color.Gray, 
                    modifier = Modifier.size(dimensions.iconSize * 0.7f)
                )
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(if (isFavorite) "Remove from favorites" else "Add to favorites") },
                        onClick = { showMenu = false; onFavoriteToggle() },
                        leadingIcon = { 
                            Icon(
                                if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, 
                                contentDescription = null,
                                tint = if (isFavorite) androidx.compose.ui.graphics.Color.Red else androidx.compose.ui.graphics.Color.Gray
                            )
                        }
                    )
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
                        leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = Color.Red) },
                        onClick = { showMenu = false; onDelete() },
                        leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = Color.Red) }
                    )
                }
            }
        }
    }
}
