package com.omni.app.ui.settings

import android.content.Context
import com.omni.app.BuildConfig
import android.net.Uri
import android.widget.Toast
import com.yausername.youtubedl_android.YoutubeDL
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omni.app.data.prefs.OmniPreferences
import com.omni.app.data.prefs.UserPreferences
import kotlinx.coroutines.launch

// Options from original code
val VIDEO_QUALITIES  = listOf("Best available", "4K (2160p)", "1440p", "1080p", "720p", "480p", "360p")
val VIDEO_FORMATS    = listOf("MP4", "MKV", "WEBM", "AVI", "MOV")
val AUDIO_FORMATS    = listOf("MP3", "AAC", "FLAC", "OPUS", "M4A", "WAV", "OGG")
val AUDIO_QUALITIES  = listOf("320kbps", "256kbps", "192kbps", "128kbps", "96kbps", "Best available")
val THEME_MODES      = listOf("Light", "Dark", "System default")
val ACCENT_COLORS    = listOf("Blue", "Purple", "Green", "Red")
val UI_STYLES        = listOf("Comfort", "Compact", "Expanded")
val FONT_FAMILIES    = listOf("System default", "Inter", "Roboto", "Poppins", "Serif", "Monospace")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val prefs = remember { UserPreferences(context) }
    val state by prefs.preferences.collectAsState(initial = OmniPreferences())
    val scope = rememberCoroutineScope()

    var expandedCategory by remember { mutableStateOf<String?>(null) }

    var showVideoQualityDialog by remember { mutableStateOf(false) }
    var showAudioFormatDialog by remember { mutableStateOf(false) }
    var showAudioQualityDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showAccentDialog by remember { mutableStateOf(false) }
    var showUiStyleDialog by remember { mutableStateOf(false) }
    var showFontDialog by remember { mutableStateOf(false) }
    var showConcurrentDialog by remember { mutableStateOf(false) }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            val path = it.lastPathSegment?.replace("primary:", "")?.replace(":", "/") ?: "Downloads/Omni"
            scope.launch { prefs.setDownloadPath(path) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
            }
            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .height(52.dp),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = Color.Gray)
                Spacer(Modifier.width(12.dp))
                Text("Search", color = Color.Gray)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            SettingsCategoryItem(
                icon = Icons.Rounded.Palette,
                iconColor = Color(0xFF3F51B5),
                title = "Appearance",
                description = "Change the theme and app colors",
                isExpanded = expandedCategory == "Appearance",
                onClick = { expandedCategory = if (expandedCategory == "Appearance") null else "Appearance" }
            ) {
                SubSettingItem("Theme", state.themeMode) { showThemeDialog = true }
                SubSettingToggle("Dynamic colors", "Material You (Android 12+)", state.dynamicColor) {
                    scope.launch { prefs.setDynamicColor(it) }
                }
                if (!state.dynamicColor) {
                    SubSettingItem("Accent color", state.accentColor) { showAccentDialog = true }
                }
                SubSettingItem("UI style", state.uiStyle) { showUiStyleDialog = true }
                SubSettingItem("Font", state.fontFamily) { showFontDialog = true }
            }

            SettingsCategoryItem(
                icon = Icons.Rounded.PlayCircle,
                iconColor = Color(0xFFE91E63),
                title = "Playback Screen",
                description = "Customize the playback screen",
                isExpanded = expandedCategory == "Player",
                onClick = { expandedCategory = if (expandedCategory == "Player") null else "Player" }
            ) {
                SubSettingToggle("Advanced Mode", "Show more controls in the player", state.advancedMode) {
                    scope.launch { prefs.setAdvancedMode(it) }
                }
                SubSettingToggle("Detailed Info", "Show technical info (codec, bitrate) in player", state.showDetailedInfo) {
                    scope.launch { prefs.setShowDetailedInfo(it) }
                }
                SubSettingItem("Default video quality", state.videoQuality) { showVideoQualityDialog = true }
            }

            SettingsCategoryItem(
                icon = Icons.AutoMirrored.Rounded.VolumeUp,
                iconColor = Color(0xFF3F51B5),
                title = "Audio",
                description = "Sound and quality settings",
                isExpanded = expandedCategory == "Audio",
                onClick = { expandedCategory = if (expandedCategory == "Audio") null else "Audio" }
            ) {
                SubSettingItem("Audio format", state.audioFormat) { showAudioFormatDialog = true }
                SubSettingItem("Audio quality", state.audioQuality) { showAudioQualityDialog = true }
            }

            SettingsCategoryItem(
                icon = Icons.Rounded.SettingsInputComponent,
                iconColor = Color(0xFF009688),
                title = "Personalize",
                description = "Customize the user interface and downloads",
                isExpanded = expandedCategory == "Personalize",
                onClick = { expandedCategory = if (expandedCategory == "Personalize") null else "Personalize" }
            ) {
                SubSettingItem("Max concurrent downloads", state.concurrentDownloads.toString()) { showConcurrentDialog = true }
                SubSettingToggle("Prefer 60fps", "Always try to fetch 60fps videos", state.prefer60fps) {
                    scope.launch { prefs.setPrefer60fps(it) }
                }
                SubSettingToggle("Auto max quality", "Select highest quality by default", state.maxQuality) {
                    scope.launch { prefs.setMaxQuality(it) }
                }
            }

            SettingsCategoryItem(
                icon = Icons.Rounded.Image,
                iconColor = Color(0xFF882C3D),
                title = "Images",
                description = "Cover and thumbnail settings",
                isExpanded = expandedCategory == "Images",
                onClick = { expandedCategory = if (expandedCategory == "Images") null else "Images" }
            ) {
                SubSettingToggle("Embed covers", "Add cover art to downloaded files", state.embedThumbnail) {
                    scope.launch { prefs.setEmbedThumbnail(it) }
                }
            }

            SettingsCategoryItem(
                icon = Icons.Rounded.Category,
                iconColor = Color(0xFF1A4585),
                title = "Performance",
                description = "Settings for slower devices",
                isExpanded = expandedCategory == "Other",
                onClick = { expandedCategory = if (expandedCategory == "Other") null else "Other" }
            ) {
                SubSettingToggle("Low performance mode", "Disables all animations, blurs and visual effects", state.lowPerfMode) {
                    scope.launch { 
                        prefs.setLowPerfMode(it)
                        prefs.setReduceAnimations(it)
                    }
                }
            }

            SettingsCategoryItem(
                icon = Icons.Rounded.Info,
                iconColor = Color(0xFF2E5936),
                title = "About",
                description = "App info and version",
                isExpanded = expandedCategory == "About",
                onClick = { expandedCategory = if (expandedCategory == "About") null else "About" }
            ) {
                SubSettingItem("Version", "Omni 1.1") { }
                EngineUpdateItem()
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    if (showThemeDialog)
        OptionsDialog("Theme", THEME_MODES, state.themeMode, { showThemeDialog = false }) {
            scope.launch { prefs.setThemeMode(it) }
            showThemeDialog = false
        }
    if (showAccentDialog)
        OptionsDialog("Accent Color", ACCENT_COLORS, state.accentColor, { showAccentDialog = false }) {
            scope.launch { prefs.setAccentColor(it) }
            showAccentDialog = false
        }
    if (showAudioFormatDialog)
        OptionsDialog("Audio Format", AUDIO_FORMATS, state.audioFormat, { showAudioFormatDialog = false }) {
            scope.launch { prefs.setAudioFormat(it) }
            showAudioFormatDialog = false
        }
    if (showAudioQualityDialog)
        OptionsDialog("Audio Quality", AUDIO_QUALITIES, state.audioQuality, { showAudioQualityDialog = false }) {
            scope.launch { prefs.setAudioQuality(it) }
            showAudioQualityDialog = false
        }
    if (showVideoQualityDialog)
        OptionsDialog("Video Quality", VIDEO_QUALITIES, state.videoQuality, { showVideoQualityDialog = false }) {
            scope.launch { prefs.setVideoQuality(it) }
            showVideoQualityDialog = false
        }
    if (showUiStyleDialog)
        OptionsDialog("UI Style", UI_STYLES, state.uiStyle, { showUiStyleDialog = false }) {
            scope.launch { prefs.setUiStyle(it) }
            showUiStyleDialog = false
        }
    if (showFontDialog)
        OptionsDialog("Font", FONT_FAMILIES, state.fontFamily, { showFontDialog = false }) {
            scope.launch { prefs.setFontFamily(it) }
            showFontDialog = false
        }
    if (showConcurrentDialog)
        OptionsDialog("Max Concurrent Downloads", listOf("1", "2", "3", "4", "5"), state.concurrentDownloads.toString(), { showConcurrentDialog = false }) {
            scope.launch { prefs.setConcurrentDownloads(it.toInt()) }
            showConcurrentDialog = false
        }
}

@Composable
fun SettingsCategoryItem(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    description: String,
    isExpanded: Boolean,
    onClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = iconColor, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
                Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp)
            }
            Icon(
                if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                null, tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        AnimatedVisibility(visible = isExpanded) {
            Column(
                modifier = Modifier
                    .padding(start = 64.dp, bottom = 12.dp)
                    .fillMaxWidth()
            ) {
                content()
            }
        }
    }
}

@Composable
fun EngineUpdateItem() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var updateState by remember { mutableStateOf<String>("idle") }
    // "idle" | "checking" | "updated" | "uptodate" | "error"

    val (statusText, statusColor) = when (updateState) {
        "checking"  -> "Checking for updates..." to MaterialTheme.colorScheme.primary
        "updated"   -> "Updated to latest version!" to Color(0xFF2E7D32)
        "uptodate"  -> "Already up to date" to MaterialTheme.colorScheme.onSurfaceVariant
        "error"     -> "Update failed. Check internet." to MaterialTheme.colorScheme.error
        else        -> {
            val version = try {
                YoutubeDL.getInstance().version(context) ?: "Unknown"
            } catch (e: Exception) { "Unknown" }
            "yt-dlp $version · tap to update" to MaterialTheme.colorScheme.onSurfaceVariant
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = updateState != "checking") {
                updateState = "checking"
                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        val result = YoutubeDL.getInstance().updateYoutubeDL(
                            context,
                            YoutubeDL.UpdateChannel.STABLE
                        )
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            updateState = when (result) {
                                YoutubeDL.UpdateStatus.DONE -> "updated"
                                YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> "uptodate"
                                else -> "error"
                            }
                        }
                    } catch (e: Exception) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            updateState = "error"
                        }
                    }
                }
            }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("Engine", style = MaterialTheme.typography.bodyLarge)
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = statusColor
            )
        }
        if (updateState == "checking") {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Icon(
                imageVector = when (updateState) {
                    "updated"  -> Icons.Rounded.CheckCircle
                    "uptodate" -> Icons.Rounded.CheckCircle
                    "error"    -> Icons.Rounded.Warning
                    else       -> Icons.Rounded.Refresh
                },
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun SubSettingItem(title: String, value: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
    ) {
        Text(title, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.bodyLarge)
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun SubSettingToggle(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.bodyLarge)
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun OptionsDialog(
    title: String,
    options: List<String>,
    selected: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = option == selected, onClick = { onSelect(option) })
                        Spacer(Modifier.width(12.dp))
                        Text(option, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        shape = RoundedCornerShape(28.dp)
    )
}
