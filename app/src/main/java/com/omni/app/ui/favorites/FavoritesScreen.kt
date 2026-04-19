package com.omni.app.ui.favorites

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import com.omni.app.data.download.DownloadEntity
import com.omni.app.data.favorites.FavoriteItem
import com.omni.app.data.favorites.FavoriteViewModel
import com.omni.app.ui.player.OmniPlayerViewModel
import com.omni.app.data.prefs.OmniPreferences
import com.omni.app.data.prefs.UserPreferences
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    vm: FavoriteViewModel = viewModel(),
    playerViewModel: OmniPlayerViewModel,
    onNavigateToPlayer: () -> Unit = {}
) {
    val state     by vm.uiState.collectAsState()
    val favorites by vm.favorites.collectAsState()
    val context = LocalContext.current
    val prefs = remember { UserPreferences(context) }
    val settings by prefs.preferences.collectAsState(initial = OmniPreferences())
    val focusManager = LocalFocusManager.current

    var showSortMenu    by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var searchActive    by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (searchActive) {
                        OutlinedTextField(
                            value = state.query,
                            onValueChange = vm::setQuery,
                            placeholder = { Text("Search favorites...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                        )
                    } else {
                        Text("Favorites", fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.headlineSmall)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        searchActive = !searchActive
                        if (!searchActive) vm.setQuery("")
                    }) {
                        Icon(
                            if (searchActive) Icons.Rounded.Close else Icons.Rounded.Search,
                            contentDescription = "Search"
                        )
                    }

                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(Icons.Rounded.FilterList, contentDescription = "Sort & Filter")
                    }

                    if (favorites.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(Icons.Rounded.DeleteSweep, contentDescription = "Clear all")
                        }
                    }

                    DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                        Text("  Sort by", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

                        listOf("date" to "Date added", "title" to "Title", "author" to "Author", "size" to "File size")
                            .forEach { (key, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    leadingIcon = {
                                        if (state.sortBy == key) Icon(Icons.Rounded.Check, null,
                                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                        else Spacer(Modifier.size(18.dp))
                                    },
                                    onClick = { vm.setSortBy(key); showSortMenu = false }
                                )
                            }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Text("  Filter", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

                        DropdownMenuItem(
                            text = { Text("Audio only") },
                            leadingIcon = {
                                if (state.filterAudio) Icon(Icons.Rounded.Check, null,
                                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                else Icon(Icons.Rounded.AudioFile, null, modifier = Modifier.size(18.dp))
                            },
                            onClick = { vm.setFilterAudio(!state.filterAudio); showSortMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Video only") },
                            leadingIcon = {
                                if (state.filterVideo) Icon(Icons.Rounded.Check, null,
                                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                else Icon(Icons.Rounded.Videocam, null, modifier = Modifier.size(18.dp))
                            },
                            onClick = { vm.setFilterVideo(!state.filterVideo); showSortMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Clear filters") },
                            leadingIcon = { Icon(Icons.Rounded.FilterListOff, null, modifier = Modifier.size(18.dp)) },
                            onClick = { vm.clearFilters(); showSortMenu = false }
                        )
                    }
                }
            )
        }
    ) { padding ->

        val hasFilter = state.filterAudio || state.filterVideo || state.query.isNotBlank()
        AnimatedVisibility(visible = hasFilter) {
            Row(
                modifier = Modifier
                    .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                    .padding(top = padding.calculateTopPadding()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (state.filterAudio) FilterChip(selected = true, onClick = { vm.setFilterAudio(false) },
                    label = { Text("Audio") }, trailingIcon = { Icon(Icons.Rounded.Close, null, modifier = Modifier.size(14.dp)) })
                if (state.filterVideo) FilterChip(selected = true, onClick = { vm.setFilterVideo(false) },
                    label = { Text("Video") }, trailingIcon = { Icon(Icons.Rounded.Close, null, modifier = Modifier.size(14.dp)) })
                if (state.query.isNotBlank()) FilterChip(selected = true, onClick = { vm.setQuery("") },
                    label = { Text("\"${state.query}\"") }, trailingIcon = { Icon(Icons.Rounded.Close, null, modifier = Modifier.size(14.dp)) })
            }
        }

        if (favorites.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(modifier = Modifier.size(80.dp), shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.FavoriteBorder, null, modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(if (state.query.isNotBlank()) "No results for \"${state.query}\""
                    else "No favorites yet",
                        style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = if (state.query.isNotBlank()) "Try a different search"
                        else "Tap ♡ on any item in your Library\nto save it here",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(160.dp),
                modifier = Modifier.fillMaxSize().padding(
                    top = if (hasFilter) 0.dp else padding.calculateTopPadding()
                ),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    top = 12.dp,
                    end = 12.dp,
                    bottom = 170.dp // Space for MiniPlayer + Nav Bar
                ),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(favorites, key = { it.filePath }) { item ->
                    FavoriteCard(
                        item = item,
                        settings = settings,
                        onClick = {
                            val entity = DownloadEntity(
                                id = item.id, title = item.title,
                                filePath = item.filePath, thumbnail = item.thumbnailUrl,
                                type = if (item.isAudio) "audio" else "video",
                                quality = "", format = item.format, size = ""
                            )
                            playerViewModel.play(entity)
                            onNavigateToPlayer()
                        },
                        onRemove = { vm.remove(item.id) }
                    )
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear all favorites?") },
            text = { Text("This will remove all ${favorites.size} items from your favorites. Your files won't be deleted.") },
            confirmButton = {
                TextButton(onClick = { vm.clearAll(); showClearDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text("Clear all")
                }
            },
            dismissButton = { TextButton(onClick = { showClearDialog = false }) { Text("Cancel") } },
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
fun FavoriteCard(item: FavoriteItem, settings: OmniPreferences, onClick: () -> Unit, onRemove: () -> Unit) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            val model = remember(item.thumbnailUrl, item.filePath) {
                val thumbFile = if (item.thumbnailUrl != null && !item.thumbnailUrl.startsWith("http"))
                    File(item.thumbnailUrl) else null

                val nameNoExt = File(item.filePath).nameWithoutExtension
                val omniDir = File(item.filePath).parentFile?.parentFile
                val thumbAudioDir = File(omniDir, ".thumbaudio")
                val thumbVideoDir = File(omniDir, ".thumbnails")
                val extraThumb = File(thumbAudioDir, "$nameNoExt.jpg").takeIf { it.exists() }
                    ?: File(thumbVideoDir, "$nameNoExt.jpg").takeIf { it.exists() }

                ImageRequest.Builder(context)
                    .data(when {
                        thumbFile?.exists() == true -> thumbFile
                        extraThumb?.exists() == true -> extraThumb
                        item.thumbnailUrl?.startsWith("http") == true -> item.thumbnailUrl
                        !item.isAudio -> File(item.filePath)
                        else -> File(item.filePath) // Try to extract from audio metadata
                    })
                    .apply { if (!item.isAudio) decoderFactory(VideoFrameDecoder.Factory()) }
                    .crossfade(true)
                    .build()
            }

            SubcomposeAsyncImage(
                model = model, contentDescription = null,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                loading = { },
                error = {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            if (item.isAudio) Icons.Rounded.MusicNote else Icons.Rounded.PlayArrow,
                            null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            )

            Surface(
                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
                color = Color.Black.copy(alpha = 0.65f), shape = RoundedCornerShape(4.dp)
            ) {
                Text(if (item.isAudio) "AUDIO" else "VIDEO",
                    color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
            }

            Icon(Icons.Rounded.Favorite, null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(18.dp))

            Box(modifier = Modifier.align(Alignment.TopStart)) {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.MoreVert, null,
                        tint = Color.White, modifier = Modifier.size(18.dp))
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Remove from favorites") },
                        leadingIcon = { Icon(Icons.Rounded.HeartBroken, null,
                            tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) },
                        onClick = { showMenu = false; onRemove() }
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = if (settings.showDetailedInfo) item.title else item.title.substringBeforeLast('.'),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        if (item.author.isNotBlank()) {
            Text(item.author, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
        }
        Spacer(Modifier.height(4.dp))
    }
}
