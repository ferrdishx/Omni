package com.omni.app.ui.home

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.omni.app.data.download.*
import com.omni.app.data.prefs.OmniPreferences
import com.omni.app.data.ytdlp.YtDlpManager
import com.omni.app.ui.settings.AUDIO_FORMATS
import com.omni.app.ui.settings.AUDIO_QUALITIES
import com.omni.app.ui.settings.VIDEO_FORMATS
import kotlinx.coroutines.launch

private const val TAG = "DownloadFormatSheet"

enum class QualityMode { DATA_SAVER, BALANCED, HIGH_QUALITY }

// ─────────────────────────────────────────────────────────────────────────────
// Main sheet
// ─────────────────────────────────────────────────────────────────────────────

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

    // ── Video info ─────────────────────────────────────────────────────────
    var videoInfo  by remember { mutableStateOf<YtDlpManager.VideoInfo?>(null) }
    var fetchState by remember { mutableStateOf("idle") }

    // ── Tab ───────────────────────────────────────────────────────────────
    var selectedTab by remember { mutableIntStateOf(0) }

    // ── Tab 0 – Format ────────────────────────────────────────────────────
    var selectedQuality by remember { mutableStateOf(if (type == "video") settings.videoQuality else settings.audioQuality) }
    var selectedFormat  by remember { mutableStateOf(if (type == "video") settings.videoFormat  else settings.audioFormat) }
    var prefer60fps     by remember { mutableStateOf(settings.prefer60fps) }
    var embedThumb      by remember { mutableStateOf(settings.embedThumbnail) }
    var selectedMode    by remember { mutableStateOf(QualityMode.BALANCED) }

    // ── Tab 1 – Options ───────────────────────────────────────────────────
    // Subtitles
    var embedSubtitles  by remember { mutableStateOf(settings.embedSubtitles) }
    var subtitleLang    by remember { mutableStateOf(settings.subtitleLanguage) }
    var subtitleFmt     by remember { mutableStateOf(settings.subtitleFormat) }
    var autoSubs        by remember { mutableStateOf(settings.autoSubtitles) }
    // Chapters & Metadata
    var embedChapters   by remember { mutableStateOf(settings.embedChapters) }
    var splitChapters   by remember { mutableStateOf(settings.splitByChapters) }
    var writeMetadata   by remember { mutableStateOf(settings.writeMetadata) }
    // SponsorBlock
    var sponsorBlock    by remember { mutableStateOf(settings.sponsorBlockEnabled) }
    var sbCategories    by remember {
        mutableStateOf(settings.sponsorBlockCategories.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            .ifEmpty { setOf("sponsor") })
    }
    var sbAction        by remember { mutableStateOf(SponsorBlockAction.entries.find { it.label == settings.sponsorBlockAction } ?: SponsorBlockAction.SKIP) }
    // Time range
    var startTime       by remember { mutableStateOf("") }
    var endTime         by remember { mutableStateOf("") }
    // Audio only
    var normalizeAudio  by remember { mutableStateOf(settings.normalizeAudio) }
    var trimSilence     by remember { mutableStateOf(settings.trimSilence) }

    // ── Tab 2 – Advanced ──────────────────────────────────────────────────
    var cookieSource    by remember { mutableStateOf(CookieSource.entries.find { it.label == settings.cookieSource } ?: CookieSource.NONE) }
    var speedLimit      by remember { mutableStateOf(settings.speedLimit) }
    var proxy           by remember { mutableStateOf(settings.proxy) }
    var maxFragments    by remember { mutableIntStateOf(settings.maxFragments) }
    var outputTemplate  by remember { mutableStateOf(settings.outputTemplate) }
    var customArgs      by remember { mutableStateOf("") }

    // ── Computed formats ───────────────────────────────────────────────────
    val processedFormats = remember(videoInfo, prefer60fps, type) {
        if (type == "video" && videoInfo != null) {
            val allFormats = videoInfo!!.availableFormats
            Log.d(TAG, "📊 Formatos brutos recebidos: ${allFormats.size}")
            allFormats.forEach { fmt ->
                Log.d(TAG, "  - ${fmt.label} (height=${fmt.height}, fps=${fmt.fps}, ext=${fmt.ext})")
            }

            val filtered = allFormats
                .filter { (it.height ?: 0) > 0 }
                .sortedWith(compareByDescending<AvailableFormat> { it.height }
                    .thenByDescending { val hi = (it.fps ?: 0) >= 50; if (prefer60fps) hi else !hi })
                .distinctBy { it.label }
                .sortedByDescending { it.height }

            Log.d(TAG, "📊 Formatos após processamento: ${filtered.size}")
            filtered.forEach { fmt ->
                Log.d(TAG, "  - ${fmt.label} (height=${fmt.height}, fps=${fmt.fps})")
            }

            filtered
        } else emptyList()
    }

    val availableQualities: List<String> = when {
        type == "video" && fetchState == "done" && videoInfo != null -> processedFormats.map { it.label }
        type == "video" -> listOf("Best available", "4K (2160p)", "1440p", "1080p", "720p", "480p", "360p")
        else -> AUDIO_QUALITIES
    }

    // Log das qualidades que serão exibidas no UI
    Log.d(TAG, "🎯 Qualidades disponíveis no UI: ${availableQualities.size}")
    availableQualities.forEach { quality ->
        Log.d(TAG, "  - UI: $quality")
    }

    val selectByMode: (QualityMode) -> Unit = remember(processedFormats) {
        { mode ->
            if (processedFormats.isNotEmpty()) {
                selectedQuality = when (mode) {
                    QualityMode.DATA_SAVER   -> (processedFormats.findLast { (it.height ?: 0) >= 360 } ?: processedFormats.last()).label
                    QualityMode.BALANCED     -> {
                        val c = processedFormats.filter { it.height in 720..1080 }
                        (if (c.isNotEmpty()) c.find { (it.fps ?: 0) <= 30 } ?: c.first()
                        else processedFormats.find { (it.height ?: 0) <= 1080 } ?: processedFormats.first()).label
                    }
                    QualityMode.HIGH_QUALITY -> processedFormats.first().label
                }
            }
        }
    }

    LaunchedEffect(url) {
        fetchState = "loading"
        val info = YtDlpManager.fetchVideoInfoWithFormats(url, context)
        videoInfo = info
        fetchState = if (info != null) "done" else "error"
    }

    LaunchedEffect(processedFormats, selectedMode) {
        if (type == "video" && fetchState == "done") selectByMode(selectedMode)
    }

    // ─────────────────────────────────────────────────────────────────────
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 40.dp)
        ) {
            // ── Header ──────────────────────────────────────────────────────
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (type == "video") Icons.Rounded.Videocam else Icons.Rounded.AudioFile,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        if (type == "video") "Video Options" else "Audio Options",
                        style = MaterialTheme.typography.titleLarge
                    )
                    if (videoInfo?.title != null) {
                        Text(
                            videoInfo!!.title!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }

            // Playlist hint
            if (onViewPlaylist != null) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    onClick = onViewPlaylist,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.PlaylistPlay, null, tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Playlist Detected", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
                            Text("Tap to download the whole playlist", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.tertiary)
                    }
                }
            }

            // Fetching info indicator
            AnimatedVisibility(visible = fetchState == "loading") {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Fetching video info…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(4.dp))

            // ── Tab row ──────────────────────────────────────────────────────
            SecondaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Format") },
                    icon = { Icon(Icons.Rounded.Tune, null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Options") },
                    icon = { Icon(Icons.Rounded.Layers, null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Advanced") },
                    icon = { Icon(Icons.Rounded.Code, null, modifier = Modifier.size(18.dp)) }
                )
            }

            Spacer(Modifier.height(16.dp))

            // ── Tab content ──────────────────────────────────────────────────
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(200)) },
                label = "tabContent"
            ) { tab ->
                when (tab) {
                    0 -> FormatTab(
                        type = type,
                        fetchState = fetchState,
                        selectedMode = selectedMode, onModeChange = { selectedMode = it; selectByMode(it) },
                        availableQualities = availableQualities,
                        selectedQuality = selectedQuality, onQualityChange = { selectedQuality = it },
                        formats = if (type == "video") VIDEO_FORMATS else AUDIO_FORMATS,
                        selectedFormat = selectedFormat, onFormatChange = { selectedFormat = it },
                        prefer60fps = prefer60fps, onPrefer60fpsChange = { prefer60fps = it },
                        embedThumb = embedThumb, onEmbedThumbChange = { embedThumb = it }
                    )
                    1 -> OptionsTab(
                        type = type,
                        embedSubtitles = embedSubtitles, onEmbedSubtitlesChange = { embedSubtitles = it },
                        subtitleLang = subtitleLang, onSubtitleLangChange = { subtitleLang = it },
                        subtitleFmt = subtitleFmt, onSubtitleFmtChange = { subtitleFmt = it },
                        autoSubs = autoSubs, onAutoSubsChange = { autoSubs = it },
                        embedChapters = embedChapters, onEmbedChaptersChange = { embedChapters = it },
                        splitChapters = splitChapters, onSplitChaptersChange = { splitChapters = it },
                        writeMetadata = writeMetadata, onWriteMetadataChange = { writeMetadata = it },
                        sponsorBlock = sponsorBlock, onSponsorBlockChange = { sponsorBlock = it },
                        sbCategories = sbCategories, onSbCategoriesChange = { sbCategories = it },
                        sbAction = sbAction, onSbActionChange = { sbAction = it },
                        startTime = startTime, onStartTimeChange = { startTime = it },
                        endTime = endTime, onEndTimeChange = { endTime = it },
                        normalizeAudio = normalizeAudio, onNormalizeAudioChange = { normalizeAudio = it },
                        trimSilence = trimSilence, onTrimSilenceChange = { trimSilence = it }
                    )
                    else -> AdvancedTab(
                        cookieSource = cookieSource, onCookieSourceChange = { cookieSource = it },
                        speedLimit = speedLimit, onSpeedLimitChange = { speedLimit = it },
                        proxy = proxy, onProxyChange = { proxy = it },
                        maxFragments = maxFragments, onMaxFragmentsChange = { maxFragments = it },
                        outputTemplate = outputTemplate, onOutputTemplateChange = { outputTemplate = it },
                        customArgs = customArgs, onCustomArgsChange = { customArgs = it }
                    )
                }
            }

            // ── Download button ──────────────────────────────────────────────
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    if (fetchState == "done" && videoInfo != null) {
                        val selectedFormatObj = processedFormats.find { it.label == selectedQuality }
                        val item = DownloadItem(
                            url            = url,
                            title          = videoInfo?.title ?: "Downloading…",
                            author         = videoInfo?.uploader ?: "Unknown",
                            thumbnailUrl   = videoInfo?.thumbnailUrl,
                            type           = if (type == "video") DownloadType.VIDEO else DownloadType.AUDIO,
                            quality        = selectedQuality,
                            selectedFormatId = selectedFormatObj?.formatId,
                            format         = selectedFormat,
                            prefer60fps    = prefer60fps,
                            embedThumbnail = embedThumb,
                            // Options
                            embedSubtitles = embedSubtitles,
                            subtitleLanguage = subtitleLang,
                            subtitleFormat = subtitleFmt,
                            autoGeneratedSubtitles = autoSubs,
                            embedChapters  = embedChapters,
                            splitByChapters = splitChapters,
                            writeMetadata  = writeMetadata,
                            sponsorBlockEnabled = sponsorBlock,
                            sponsorBlockCategories = sbCategories,
                            sponsorBlockAction = sbAction,
                            startTime      = startTime,
                            endTime        = endTime,
                            normalizeAudio = normalizeAudio,
                            trimSilence    = trimSilence,
                            // Advanced
                            cookieSource   = cookieSource,
                            speedLimit     = speedLimit,
                            proxy          = proxy,
                            maxFragments   = maxFragments,
                            outputTemplate = outputTemplate,
                            customArgs     = customArgs
                        )
                        Log.d("OmniDebug", "Enqueuing: ${item.title}")
                        vm.enqueue(item)
                        onDismiss()
                    }
                },
                enabled = fetchState == "done",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                Icon(Icons.Rounded.Download, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Start Download", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Tab 0 – Format
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FormatTab(
    type: String,
    fetchState: String,
    selectedMode: QualityMode, onModeChange: (QualityMode) -> Unit,
    availableQualities: List<String>,
    selectedQuality: String, onQualityChange: (String) -> Unit,
    formats: List<String>,
    selectedFormat: String, onFormatChange: (String) -> Unit,
    prefer60fps: Boolean, onPrefer60fpsChange: (Boolean) -> Unit,
    embedThumb: Boolean, onEmbedThumbChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Quality mode chips (video only)
        if (type == "video") {
            SectionLabel("Quality Mode")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QualityModeChip("Data Saver", selectedMode == QualityMode.DATA_SAVER) { onModeChange(QualityMode.DATA_SAVER) }
                QualityModeChip("Balanced",   selectedMode == QualityMode.BALANCED)   { onModeChange(QualityMode.BALANCED) }
                QualityModeChip("High Quality", selectedMode == QualityMode.HIGH_QUALITY) { onModeChange(QualityMode.HIGH_QUALITY) }
            }
        }

        // Quality
        SectionLabel("Quality")
        if (type == "video" && fetchState == "loading") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(16.dp))
                    Text("Fetching available formats…", style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            ChipGroup(options = availableQualities, selected = selectedQuality, onSelect = onQualityChange)
        }

        // Format
        SectionLabel("Container")
        ChipGroup(options = formats, selected = selectedFormat, onSelect = onFormatChange)

        // Format info card
        FormatInfoCard(type = type, format = selectedFormat, quality = selectedQuality)

        // Toggles
        if (type == "video") {
            OptionToggleRow(
                icon = Icons.Rounded.Speed,
                title = "Prefer 60fps",
                subtitle = "Prioritise higher frame rate when available",
                checked = prefer60fps,
                onCheckedChange = onPrefer60fpsChange
            )
        }

        OptionToggleRow(
            icon = Icons.Rounded.Image,
            title = "Embed thumbnail",
            subtitle = "Add cover art to the file",
            checked = embedThumb,
            onCheckedChange = onEmbedThumbChange
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Tab 1 – Options
// ─────────────────────────────────────────────────────────────────────────────

private val SUBTITLE_LANGUAGES = listOf("en", "pt", "es", "fr", "de", "it", "ja", "ko", "zh", "ar", "ru", "auto")
private val SUBTITLE_FORMATS   = listOf("srt", "vtt", "ass", "lrc", "json3")

private val SPONSORBLOCK_CATEGORIES = mapOf(
    "sponsor"       to "Sponsor",
    "selfpromo"     to "Self-promo",
    "interaction"   to "Interaction",
    "intro"         to "Intro",
    "outro"         to "Outro",
    "preview"       to "Preview",
    "music_offtopic" to "Music off-topic",
    "filler"        to "Filler"
)

@Composable
private fun OptionsTab(
    type: String,
    embedSubtitles: Boolean, onEmbedSubtitlesChange: (Boolean) -> Unit,
    subtitleLang: String, onSubtitleLangChange: (String) -> Unit,
    subtitleFmt: String, onSubtitleFmtChange: (String) -> Unit,
    autoSubs: Boolean, onAutoSubsChange: (Boolean) -> Unit,
    embedChapters: Boolean, onEmbedChaptersChange: (Boolean) -> Unit,
    splitChapters: Boolean, onSplitChaptersChange: (Boolean) -> Unit,
    writeMetadata: Boolean, onWriteMetadataChange: (Boolean) -> Unit,
    sponsorBlock: Boolean, onSponsorBlockChange: (Boolean) -> Unit,
    sbCategories: Set<String>, onSbCategoriesChange: (Set<String>) -> Unit,
    sbAction: SponsorBlockAction, onSbActionChange: (SponsorBlockAction) -> Unit,
    startTime: String, onStartTimeChange: (String) -> Unit,
    endTime: String, onEndTimeChange: (String) -> Unit,
    normalizeAudio: Boolean, onNormalizeAudioChange: (Boolean) -> Unit,
    trimSilence: Boolean, onTrimSilenceChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // ── Subtitles ──────────────────────────────────────────────────────
        OptionsSectionHeader("Subtitles", Icons.Rounded.Subtitles)

        OptionToggleRow(
            icon = Icons.Rounded.ClosedCaption,
            title = "Embed subtitles",
            subtitle = "Mux subtitle track into video file",
            checked = embedSubtitles,
            onCheckedChange = onEmbedSubtitlesChange
        )

        AnimatedVisibility(visible = embedSubtitles) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Spacer(Modifier.height(4.dp))
                SectionLabel("Language")
                ChipGroup(options = SUBTITLE_LANGUAGES, selected = subtitleLang, onSelect = onSubtitleLangChange)
                SectionLabel("Subtitle format")
                ChipGroup(options = SUBTITLE_FORMATS, selected = subtitleFmt, onSelect = onSubtitleFmtChange)
                OptionToggleRow(
                    icon = Icons.Rounded.AutoFixHigh,
                    title = "Auto-generated subtitles",
                    subtitle = "Include AI-generated captions",
                    checked = autoSubs,
                    onCheckedChange = onAutoSubsChange
                )
                Spacer(Modifier.height(4.dp))
            }
        }

        OptionsDivider()

        // ── Chapters & Metadata ────────────────────────────────────────────
        OptionsSectionHeader("Chapters & Metadata", Icons.Rounded.BookmarkBorder)

        OptionToggleRow(
            icon = Icons.Rounded.LibraryBooks,
            title = "Embed chapters",
            subtitle = "Add chapter markers to the file",
            checked = embedChapters,
            onCheckedChange = onEmbedChaptersChange
        )

        OptionToggleRow(
            icon = Icons.Rounded.ContentCut,
            title = "Split by chapters",
            subtitle = "Creates one file per chapter",
            checked = splitChapters,
            onCheckedChange = onSplitChaptersChange
        )

        AnimatedVisibility(visible = splitChapters) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
            ) {
                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Multiple files will be created, one per chapter.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }

        OptionToggleRow(
            icon = Icons.Rounded.Tag,
            title = "Write metadata",
            subtitle = "Embed title, artist, album and more",
            checked = writeMetadata,
            onCheckedChange = onWriteMetadataChange
        )

        OptionsDivider()

        // ── SponsorBlock (video only) ──────────────────────────────────────
        if (type == "video") {
            OptionsSectionHeader("SponsorBlock", Icons.Rounded.Block)

            OptionToggleRow(
                icon = Icons.Rounded.SkipNext,
                title = "Enable SponsorBlock",
                subtitle = "Remove sponsored segments from video",
                checked = sponsorBlock,
                onCheckedChange = onSponsorBlockChange
            )

            AnimatedVisibility(visible = sponsorBlock) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Spacer(Modifier.height(4.dp))
                    SectionLabel("Categories")
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SPONSORBLOCK_CATEGORIES.forEach { (key, label) ->
                            val checked = key in sbCategories
                            FilterChip(
                                selected = checked,
                                onClick = {
                                    onSbCategoriesChange(
                                        if (checked) sbCategories - key
                                        else sbCategories + key
                                    )
                                },
                                label = { Text(label) },
                                leadingIcon = if (checked) { { Icon(Icons.Rounded.Check, null, modifier = Modifier.size(16.dp)) } } else null,
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }

                    SectionLabel("Action")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SponsorBlockAction.entries.forEach { action ->
                            FilterChip(
                                selected = sbAction == action,
                                onClick = { onSbActionChange(action) },
                                label = { Text(action.label) },
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }

            OptionsDivider()
        }

        // ── Time range ─────────────────────────────────────────────────────
        OptionsSectionHeader("Time Range", Icons.Rounded.Schedule)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = startTime,
                onValueChange = onStartTimeChange,
                modifier = Modifier.weight(1f),
                label = { Text("Start time") },
                placeholder = { Text("00:00:00") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                leadingIcon = { Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(18.dp)) }
            )
            OutlinedTextField(
                value = endTime,
                onValueChange = onEndTimeChange,
                modifier = Modifier.weight(1f),
                label = { Text("End time") },
                placeholder = { Text("00:00:00") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                leadingIcon = { Icon(Icons.Rounded.Stop, null, modifier = Modifier.size(18.dp)) }
            )
        }

        Text(
            "Format: hh:mm:ss  ·  Leave blank to use full video",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // ── Audio post-processing (audio only) ────────────────────────────
        if (type == "audio") {
            OptionsDivider()
            OptionsSectionHeader("Audio Processing", Icons.Rounded.Equalizer)

            OptionToggleRow(
                icon = Icons.Rounded.VolumeUp,
                title = "Normalize audio",
                subtitle = "Adjust loudness to a consistent level",
                checked = normalizeAudio,
                onCheckedChange = onNormalizeAudioChange
            )

            OptionToggleRow(
                icon = Icons.Rounded.GraphicEq,
                title = "Trim silence",
                subtitle = "Remove leading and trailing silence",
                checked = trimSilence,
                onCheckedChange = onTrimSilenceChange
            )
        }

        Spacer(Modifier.height(8.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Tab 2 – Advanced
// ─────────────────────────────────────────────────────────────────────────────

private val OUTPUT_TEMPLATE_VARS = listOf("%(title)s", "%(uploader)s", "%(upload_date)s", "%(id)s", "%(ext)s", "%(resolution)s", "%(playlist_index)s")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdvancedTab(
    cookieSource: CookieSource, onCookieSourceChange: (CookieSource) -> Unit,
    speedLimit: String, onSpeedLimitChange: (String) -> Unit,
    proxy: String, onProxyChange: (String) -> Unit,
    maxFragments: Int, onMaxFragmentsChange: (Int) -> Unit,
    outputTemplate: String, onOutputTemplateChange: (String) -> Unit,
    customArgs: String, onCustomArgsChange: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // ── Cookies ────────────────────────────────────────────────────────
        OptionsSectionHeader("Cookies", Icons.Rounded.Cookie)

        SectionLabel("Source")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CookieSource.entries.forEach { src ->
                FilterChip(
                    selected = cookieSource == src,
                    onClick = { onCookieSourceChange(src) },
                    label = { Text(src.label) },
                    leadingIcon = if (cookieSource == src) { { Icon(Icons.Rounded.Check, null, modifier = Modifier.size(16.dp)) } } else null,
                    shape = RoundedCornerShape(10.dp)
                )
            }
        }

        AnimatedVisibility(visible = cookieSource != CookieSource.NONE) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when (cookieSource) {
                            CookieSource.CHROME  -> "Will read cookies from Chrome browser profile."
                            CookieSource.FIREFOX -> "Will read cookies from Firefox browser profile."
                            CookieSource.EDGE    -> "Will read cookies from Edge browser profile."
                            CookieSource.BRAVE   -> "Will read cookies from Brave browser profile."
                            CookieSource.FILE    -> "Specify a Netscape-format cookies file path below."
                            else                 -> ""
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        OptionsDivider()

        // ── Network ────────────────────────────────────────────────────────
        OptionsSectionHeader("Network", Icons.Rounded.NetworkCheck)

        OutlinedTextField(
            value = speedLimit,
            onValueChange = onSpeedLimitChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Download speed limit") },
            placeholder = { Text("e.g.  5M  or  500K") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            leadingIcon = { Icon(Icons.Rounded.Speed, null, modifier = Modifier.size(18.dp)) }
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = proxy,
            onValueChange = onProxyChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Proxy") },
            placeholder = { Text("e.g.  socks5://127.0.0.1:1080") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            leadingIcon = { Icon(Icons.Rounded.VpnKey, null, modifier = Modifier.size(18.dp)) }
        )

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Max concurrent fragments", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "$maxFragments fragments",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Slider(
            value = maxFragments.toFloat(),
            onValueChange = { onMaxFragmentsChange(it.toInt()) },
            valueRange = 1f..32f,
            steps = 30,
            modifier = Modifier.fillMaxWidth()
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("1", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("32", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        OptionsDivider()

        // ── Output template ────────────────────────────────────────────────
        OptionsSectionHeader("Output Template", Icons.Rounded.DriveFileRenameOutline)

        OutlinedTextField(
            value = outputTemplate,
            onValueChange = onOutputTemplateChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Filename template") },
            placeholder = { Text("%(title)s.%(ext)s") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            leadingIcon = { Icon(Icons.Rounded.TextFields, null, modifier = Modifier.size(18.dp)) }
        )

        Spacer(Modifier.height(8.dp))
        Text("Variables:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))

        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OUTPUT_TEMPLATE_VARS.forEach { variable ->
                Surface(
                    onClick = {
                        val cursor = outputTemplate.lastIndexOf(".%(ext)s")
                        val insertPos = if (cursor >= 0) cursor else outputTemplate.length
                        val newTemplate = outputTemplate.substring(0, insertPos) + variable + outputTemplate.substring(insertPos)
                        onOutputTemplateChange(newTemplate)
                    },
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        variable,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        OptionsDivider()

        // ── Custom extra args ──────────────────────────────────────────────
        OptionsSectionHeader("Extra Arguments", Icons.Rounded.Terminal)

        OutlinedTextField(
            value = customArgs,
            onValueChange = onCustomArgsChange,
            modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
            label = { Text("Custom yt-dlp arguments") },
            placeholder = { Text("e.g.  --no-playlist  --write-subs") },
            maxLines = 4,
            shape = RoundedCornerShape(12.dp)
        )

        Surface(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        ) {
            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Incorrect arguments may cause downloads to fail.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun OptionsSectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun OptionsDivider() {
    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
}

@Composable
fun OptionToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
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
                selected = option == selected,
                onClick = { onSelect(option) },
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
        type == "video" && format == "WEBM" -> "Web-optimised · smaller file"
        type == "video" && format == "AVI"  -> "Legacy format · wide compatibility"
        type == "video" && format == "MOV"  -> "Apple QuickTime · ProRes support"
        type == "audio" && format == "MP3"  -> "Universal compatibility · lossy"
        type == "audio" && format == "FLAC" -> "Lossless quality · larger file"
        type == "audio" && format == "AAC"  -> "High quality · great for mobile"
        type == "audio" && format == "OPUS" -> "Best compression · excellent quality"
        type == "audio" && format == "M4A"  -> "iTunes compatible · high quality"
        type == "audio" && format == "WAV"  -> "Uncompressed PCM · very large file"
        type == "audio" && format == "OGG"  -> "Open source · good compression"
        else -> "Selected format"
    }
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (type == "video") Icons.Rounded.Videocam else Icons.Rounded.AudioFile,
                null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "$format · $quality · $info",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}
