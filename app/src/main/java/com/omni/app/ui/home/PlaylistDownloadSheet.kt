package com.omni.app.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.omni.app.data.download.*
import com.omni.app.data.prefs.OmniPreferences
import com.omni.app.data.ytdlp.YtDlpManager
import com.omni.app.data.ytdlp.YtDlpManager.PlaylistInfo
import com.omni.app.data.ytdlp.YtDlpManager.PlaylistItem
import com.omni.app.ui.settings.AUDIO_FORMATS
import com.omni.app.ui.settings.VIDEO_FORMATS
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDownloadSheet(
    url: String,
    settings: OmniPreferences,
    initialType: DownloadType = DownloadType.VIDEO,
    vm: DownloadViewModel = viewModel(),
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var playlistInfo by remember { mutableStateOf<PlaylistInfo?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedItems by remember { mutableStateOf(setOf<Int>()) }

    var downloadType by remember { mutableStateOf(initialType) }
    var selectedQuality by remember { mutableStateOf(if (initialType == DownloadType.VIDEO) settings.videoQuality else settings.audioQuality) }
    var selectedFormat by remember { mutableStateOf(if (initialType == DownloadType.VIDEO) settings.videoFormat else settings.audioFormat) }

    var durationFilter by remember { mutableStateOf("All") }
    val filteredEntries = remember(playlistInfo, durationFilter) {
        playlistInfo?.entries?.filter { item ->
            when (durationFilter) {
                "< 5 min" -> item.duration < 300
                "5-20 min" -> item.duration in 300..1200
                "> 20 min" -> item.duration > 1200
                else -> true
            }
        } ?: emptyList()
    }

    LaunchedEffect(url) {
        isLoading = true
        playlistInfo = YtDlpManager.fetchPlaylistInfo(url, context)
        playlistInfo?.let {
            selectedItems = it.entries.indices.toSet()
        }
        isLoading = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (playlistInfo == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Failed to load playlist info")
                }
            } else {
                Text(
                    text = playlistInfo!!.title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${playlistInfo!!.videoCount} videos • ${playlistInfo!!.author ?: "Unknown"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = downloadType == DownloadType.VIDEO,
                        onClick = {
                            downloadType = DownloadType.VIDEO
                            selectedQuality = settings.videoQuality
                            selectedFormat = settings.videoFormat
                        },
                        label = { Text("Video") },
                        leadingIcon = { Icon(Icons.Rounded.Videocam, null, Modifier.size(18.dp)) }
                    )
                    FilterChip(
                        selected = downloadType == DownloadType.AUDIO,
                        onClick = {
                            downloadType = DownloadType.AUDIO
                            selectedQuality = settings.audioQuality
                            selectedFormat = settings.audioFormat
                        },
                        label = { Text("Audio") },
                        leadingIcon = { Icon(Icons.Rounded.AudioFile, null, Modifier.size(18.dp)) }
                    )
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    var showQualityMenu by remember { mutableStateOf(false) }
                    Box(Modifier.weight(1f)) {
                        OutlinedCard(
                            onClick = { showQualityMenu = true },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("Quality", style = MaterialTheme.typography.labelSmall)
                                    Text(selectedQuality, style = MaterialTheme.typography.bodyMedium)
                                }
                                Icon(Icons.Rounded.ArrowDropDown, null)
                            }
                        }
                        DropdownMenu(expanded = showQualityMenu, onDismissRequest = { showQualityMenu = false }) {
                            val options = if (downloadType == DownloadType.VIDEO)
                                listOf("Best available", "4K (2160p)", "1440p", "1080p", "720p", "480p", "360p")
                            else listOf("320kbps", "256kbps", "192kbps", "128kbps")

                            options.forEach { opt ->
                                DropdownMenuItem(
                                    text = { Text(opt) },
                                    onClick = { selectedQuality = opt; showQualityMenu = false }
                                )
                            }
                        }
                    }

                    var showFormatMenu by remember { mutableStateOf(false) }
                    Box(Modifier.weight(1f)) {
                        OutlinedCard(
                            onClick = { showFormatMenu = true },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("Format", style = MaterialTheme.typography.labelSmall)
                                    Text(selectedFormat, style = MaterialTheme.typography.bodyMedium)
                                }
                                Icon(Icons.Rounded.ArrowDropDown, null)
                            }
                        }
                        DropdownMenu(expanded = showFormatMenu, onDismissRequest = { showFormatMenu = false }) {
                            val options = if (downloadType == DownloadType.VIDEO) VIDEO_FORMATS else AUDIO_FORMATS
                            options.forEach { opt ->
                                DropdownMenuItem(
                                    text = { Text(opt) },
                                    onClick = { selectedFormat = opt; showFormatMenu = false }
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Spacer(Modifier.height(16.dp))

                Text("Filters", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val filters = listOf("All", "< 5 min", "5-20 min", "> 20 min")
                    filters.forEach { filter ->
                        FilterChip(
                            selected = durationFilter == filter,
                            onClick = { durationFilter = filter },
                            label = { Text(filter, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val displayedCount = filteredEntries.size
                    Text("Select Videos ($displayedCount)", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = {
                        val filteredIndices = playlistInfo!!.entries.indices.filter {
                            val item = playlistInfo!!.entries[it]
                            when (durationFilter) {
                                "< 5 min" -> item.duration < 300
                                "5-20 min" -> item.duration in 300..1200
                                "> 20 min" -> item.duration > 1200
                                else -> true
                            }
                        }.toSet()

                        val allSelected = filteredIndices.all { selectedItems.contains(it) }
                        selectedItems = if (allSelected) {
                            selectedItems - filteredIndices
                        } else {
                            selectedItems + filteredIndices
                        }
                    }) {
                        val filteredIndices = playlistInfo!!.entries.indices.filter {
                            val item = playlistInfo!!.entries[it]
                            when (durationFilter) {
                                "< 5 min" -> item.duration < 300
                                "5-20 min" -> item.duration in 300..1200
                                "> 20 min" -> item.duration > 1200
                                else -> true
                            }
                        }
                        val allSelected = filteredIndices.all { selectedItems.contains(it) }
                        Text(if (allSelected) "Deselect Filtered" else "Select Filtered")
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredEntries.size) { index ->
                        val item = filteredEntries[index]
                        val originalIndex = playlistInfo!!.entries.indexOf(item)
                        val isSelected = selectedItems.contains(originalIndex)

                        PlaylistItemRow(
                            item = item,
                            isSelected = isSelected,
                            onToggle = {
                                selectedItems = if (isSelected) selectedItems - originalIndex else selectedItems + originalIndex
                            }
                        )
                    }
                }

                Button(
                    onClick = {
                        val batchId = UUID.randomUUID().toString()
                        selectedItems.sorted().forEach { index ->
                            val item = playlistInfo!!.entries[index]
                            vm.enqueue(DownloadItem(
                                url = item.url,
                                title = item.title,
                                thumbnailUrl = item.thumbnailUrl,
                                type = downloadType,
                                quality = selectedQuality,
                                format = selectedFormat,
                                prefer60fps = settings.prefer60fps,
                                embedThumbnail = settings.embedThumbnail,
                                batchId = batchId
                            ))
                        }
                        onDismiss()
                    },
                    enabled = selectedItems.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Icon(Icons.Rounded.Download, null)
                    Spacer(Modifier.width(12.dp))

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Download ${selectedItems.size} items", fontWeight = FontWeight.Bold)
                        val estimatedSize = estimateSize(selectedItems.size, downloadType, selectedQuality)
                        Text(
                            text = "Est. size: $estimatedSize",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }
}

private fun estimateSize(count: Int, type: DownloadType, quality: String): String {
    val avgMbPerMin = when (type) {
        DownloadType.AUDIO -> 1.5 // ~128-192kbps
        DownloadType.VIDEO -> when {
            quality.contains("2160") -> 150.0
            quality.contains("1440") -> 80.0
            quality.contains("1080") -> 40.0
            quality.contains("720") -> 20.0
            else -> 10.0
        }
    }
    val totalMb = count * 4 * avgMbPerMin
    return if (totalMb > 1000) "%.1f GB".format(totalMb / 1024.0) else "${totalMb.toInt()} MB"
}

@Composable
fun PlaylistItemRow(
    item: PlaylistItem,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                      else Color.Transparent,
        label = "bgColor"
    )

    val elevation by animateDpAsState(
        targetValue = if (isSelected) 4.dp else 0.dp,
        label = "elevation"
    )

    Surface(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor,
        tonalElevation = elevation
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 110.dp, height = 62.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AsyncImage(
                    model = item.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                if (item.duration > 0) {
                    Surface(
                        modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
                        color = Color.Black.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = formatDuration(item.duration),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = isSelected,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.background(Color.White, CircleShape)
                        )
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = item.uploader ?: "Unknown",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
            )
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
