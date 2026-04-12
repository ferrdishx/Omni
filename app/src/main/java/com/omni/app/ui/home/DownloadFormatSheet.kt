package com.omni.app.ui.home

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.omni.app.data.download.*
import com.omni.app.data.prefs.OmniPreferences
import com.omni.app.data.ytdlp.YtDlpManager
import com.omni.app.ui.settings.AUDIO_FORMATS
import com.omni.app.ui.settings.AUDIO_QUALITIES
import com.omni.app.ui.settings.VIDEO_FORMATS
import kotlinx.coroutines.launch

enum class QualityMode { DATA_SAVER, BALANCED, HIGH_QUALITY }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadFormatSheet(
    url: String,
    type: String,
    settings: OmniPreferences,
    vm: DownloadViewModel = viewModel(),
    onViewPlaylist: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val scope   = rememberCoroutineScope()
    val context = LocalContext.current

    var videoInfo  by remember { mutableStateOf<YtDlpManager.VideoInfo?>(null) }
    var fetchState by remember { mutableStateOf<String>("idle") }
    var fullFormatsFetched by remember { mutableStateOf(false) }

    var selectedQuality by remember { mutableStateOf(if (type == "video") settings.videoQuality else settings.audioQuality) }
    var selectedFormat  by remember { mutableStateOf(if (type == "video") settings.videoFormat  else settings.audioFormat) }
    var prefer60fps     by remember { mutableStateOf(settings.prefer60fps) }
    var embedThumb      by remember { mutableStateOf(settings.embedThumbnail) }
    var selectedMode    by remember { mutableStateOf(QualityMode.BALANCED) }

    val processedFormats = remember(videoInfo, prefer60fps, type) {
        if (type == "video" && videoInfo != null) {
            videoInfo!!.availableFormats
                .filter { (it.height ?: 0) >= 240 }
                .sortedWith(compareByDescending<AvailableFormat> { it.height ?: 0 }
                    .thenByDescending { 
                        val isHighFps = (it.fps ?: 0) >= 50
                        if (prefer60fps) isHighFps else !isHighFps 
                    })
        } else emptyList()
    }

    val availableQualities: List<String> = when {
        type == "video" && fetchState == "done" && videoInfo != null ->
            processedFormats.map { it.label }
        type == "video" -> listOf("Best available", "4K (2160p)", "1440p", "1080p", "720p", "480p", "360p")
        else -> AUDIO_QUALITIES
    }

    val selectFormatByMode = remember(processedFormats) {
        { mode: QualityMode ->
            if (processedFormats.isNotEmpty()) {
                val target = when (mode) {
                    QualityMode.DATA_SAVER -> {
                        processedFormats.findLast { (it.height ?: 0) >= 360 } ?: processedFormats.last()
                    }
                    QualityMode.BALANCED -> {
                        val candidates = processedFormats.filter { it.height in 720..1080 }
                        if (candidates.isNotEmpty()) {
                            candidates.find { (it.fps ?: 0) <= 30 } ?: candidates.first()
                        } else {
                            processedFormats.find { (it.height ?: 0) <= 1080 } ?: processedFormats.first()
                        }
                    }
                    QualityMode.HIGH_QUALITY -> {
                        processedFormats.first()
                    }
                }
                selectedQuality = target.label
            }
        }
    }

    LaunchedEffect(url) {
        fetchState = "loading"
        val info = YtDlpManager.fetchVideoInfoWithFormats(url, context)
        videoInfo = info
        
        if (info != null) {
            fullFormatsFetched = true
            fetchState = "done"
        } else {
            fetchState = "error"
        }
    }

    LaunchedEffect(processedFormats, selectedMode) {
        if (type == "video" && fetchState == "done") {
            selectFormatByMode(selectedMode)
        }
    }

    val formats = if (type == "video") VIDEO_FORMATS else AUDIO_FORMATS

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (type == "video") Icons.Rounded.Videocam else Icons.Rounded.AudioFile,
                    contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(if (type == "video") "Video Options" else "Audio Options", style = MaterialTheme.typography.titleLarge)
                    if (videoInfo?.title != null) {
                        Text(videoInfo!!.title!!, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1)
                    }
                }
            }

            // Playlist detected hint
            if (onViewPlaylist != null) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    onClick = onViewPlaylist,
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlaylistPlay, 
                            contentDescription = null, 
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Playlist Detected", 
                                style = MaterialTheme.typography.labelLarge, 
                                color = MaterialTheme.colorScheme.tertiary
                            )
                            Text(
                                "Click here to download the whole playlist", 
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight, 
                            contentDescription = null, 
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            AnimatedVisibility(visible = fetchState == "loading") {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Fetching video info...", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (type == "video") {
                Text("Quality Mode", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QualityModeChip("Data Saver", selectedMode == QualityMode.DATA_SAVER) { selectedMode = QualityMode.DATA_SAVER }
                    QualityModeChip("Balanced", selectedMode == QualityMode.BALANCED) { selectedMode = QualityMode.BALANCED }
                    QualityModeChip("High Quality", selectedMode == QualityMode.HIGH_QUALITY) { selectedMode = QualityMode.HIGH_QUALITY }
                }
                Spacer(Modifier.height(16.dp))
            }

            Text("Quality", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            
            if (type == "video" && fetchState == "loading") {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("Fetching video info...", style = MaterialTheme.typography.bodyLarge)
                            Text("Please wait a moment", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                ChipGroup(options = availableQualities, selected = selectedQuality) { selectedQuality = it }
            }

            Spacer(Modifier.height(20.dp))

            Text("Format", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            ChipGroup(options = formats, selected = selectedFormat) { selectedFormat = it }

            if (type == "video") {
                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth().clickable { prefer60fps = !prefer60fps }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Speed, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Prefer 60fps", style = MaterialTheme.typography.bodyLarge)
                        Text("When available", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = prefer60fps, onCheckedChange = { prefer60fps = it })
                }
            }

            if (type == "audio") {
                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth().clickable { embedThumb = !embedThumb }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Image, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Embed thumbnail", style = MaterialTheme.typography.bodyLarge)
                        Text("Add cover art to the audio file", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = embedThumb, onCheckedChange = { embedThumb = it })
                }
            }

            Spacer(Modifier.height(16.dp))
            FormatInfoCard(type = type, format = selectedFormat, quality = selectedQuality)

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = {
                    Log.d("OmniDebug", "Download button clicked for URL: $url")
                    if (fetchState == "done" && videoInfo != null) {
                        val selectedFormatObj = processedFormats.find { it.label == selectedQuality }
                        Log.d("OmniDebug", "Download click - Quality: $selectedQuality, FPS: ${selectedFormatObj?.fps}, Prefer60: $prefer60fps")
                        val item = DownloadItem(
                            url            = url,
                            title          = videoInfo?.title ?: "Downloading...",
                            thumbnailUrl   = videoInfo?.thumbnailUrl,
                            type           = if (type == "video") DownloadType.VIDEO else DownloadType.AUDIO,
                            quality        = selectedQuality,
                            selectedFormatId = selectedFormatObj?.formatId,
                            format         = selectedFormat,
                            prefer60fps    = prefer60fps,
                            embedThumbnail = embedThumb
                        )
                        Log.d("OmniDebug", "Enqueuing download: ${item.title} (${item.quality}) with formatId: ${item.selectedFormatId}")
                        vm.enqueue(item)
                        onDismiss()
                    } else {
                        Log.e("OmniDebug", "Download failed to start: Data not ready. fetchState=$fetchState, videoInfo=${videoInfo != null}")
                    }
                },
                enabled = fetchState == "done",
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                Icon(Icons.Rounded.Download, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Start Download", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
fun QualityModeChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = { Text(label) },
        shape = RoundedCornerShape(10.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipGroup(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected, onClick = { onSelect(option) },
                label = { Text(option) },
                leadingIcon = if (option == selected) { { Icon(Icons.Rounded.Check, null, modifier = Modifier.size(16.dp)) } } else null,
                shape = RoundedCornerShape(10.dp)
            )
        }
    }
}

@Composable
fun FormatInfoCard(type: String, format: String, quality: String) {
    val info = when {
        type == "video" && format == "MP4"  -> "Best compatibility · supports HDR"
        type == "video" && format == "MKV"  -> "Keeps all streams · larger file"
        type == "video" && format == "WEBM" -> "Web optimized · smaller file"
        type == "audio" && format == "MP3"  -> "Universal compatibility · lossy"
        type == "audio" && format == "FLAC" -> "Lossless quality · larger file"
        type == "audio" && format == "AAC"  -> "High quality · great for mobile"
        type == "audio" && format == "OPUS" -> "Best compression · great quality"
        type == "audio" && format == "M4A"  -> "iTunes compatible · high quality"
        type == "audio" && format == "WAV"  -> "Uncompressed · very large file"
        else -> "Selected format"
    }
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (type == "video") Icons.Rounded.Videocam else Icons.Rounded.AudioFile,
                null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text("$format · $quality · $info", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}
