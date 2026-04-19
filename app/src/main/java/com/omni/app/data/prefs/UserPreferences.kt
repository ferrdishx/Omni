package com.omni.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "omni_prefs")

object PreferenceKeys {
    // Video
    val VIDEO_QUALITY       = stringPreferencesKey("video_quality")
    val VIDEO_FORMAT        = stringPreferencesKey("video_format")
    val PREFER_60FPS        = booleanPreferencesKey("prefer_60fps")
    val MAX_QUALITY         = booleanPreferencesKey("max_quality")

    // Audio
    val AUDIO_FORMAT        = stringPreferencesKey("audio_format")
    val AUDIO_QUALITY       = stringPreferencesKey("audio_quality")
    val NORMALIZE_AUDIO     = booleanPreferencesKey("normalize_audio")
    val TRIM_SILENCE        = booleanPreferencesKey("trim_silence")

    // Subtitles
    val EMBED_SUBTITLES     = booleanPreferencesKey("embed_subtitles")
    val SUBTITLE_LANGUAGE   = stringPreferencesKey("subtitle_language")
    val SUBTITLE_FORMAT     = stringPreferencesKey("subtitle_format")
    val AUTO_SUBTITLES      = booleanPreferencesKey("auto_subtitles")

    // Chapters & Metadata
    val EMBED_CHAPTERS      = booleanPreferencesKey("embed_chapters")
    val SPLIT_BY_CHAPTERS   = booleanPreferencesKey("split_by_chapters")
    val WRITE_METADATA      = booleanPreferencesKey("write_metadata")
    val EMBED_THUMBNAIL     = booleanPreferencesKey("embed_thumbnail")

    // SponsorBlock
    val SPONSORBLOCK_ENABLED    = booleanPreferencesKey("sponsorblock_enabled")
    val SPONSORBLOCK_CATEGORIES = stringPreferencesKey("sponsorblock_categories")
    val SPONSORBLOCK_ACTION     = stringPreferencesKey("sponsorblock_action")

    // Network & Cookies
    val COOKIE_SOURCE       = stringPreferencesKey("cookie_source")
    val SPEED_LIMIT         = stringPreferencesKey("speed_limit")
    val PROXY               = stringPreferencesKey("proxy")
    val MAX_FRAGMENTS       = intPreferencesKey("max_fragments")
    val WIFI_ONLY           = booleanPreferencesKey("wifi_only")
    val CONCURRENT_DL       = intPreferencesKey("concurrent_downloads")

    // Output
    val DOWNLOAD_PATH       = stringPreferencesKey("download_path")
    val OUTPUT_TEMPLATE     = stringPreferencesKey("output_template")

    // Appearance
    val THEME_MODE          = stringPreferencesKey("theme_mode")
    val ACCENT_COLOR        = stringPreferencesKey("accent_color")
    val UI_STYLE            = stringPreferencesKey("ui_style")
    val FONT_FAMILY         = stringPreferencesKey("font_family")
    val DYNAMIC_COLOR       = booleanPreferencesKey("dynamic_color")

    // Misc
    val SHOW_DETAILED_INFO  = booleanPreferencesKey("show_detailed_info")
    val ADVANCED_MODE       = booleanPreferencesKey("advanced_mode")
    val REDUCE_ANIM         = booleanPreferencesKey("reduce_animations")
    val LOW_PERF_MODE       = booleanPreferencesKey("low_perf_mode")
    val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
}

data class OmniPreferences(
    // Video
    val videoQuality: String = "Best available",
    val videoFormat: String = "MP4",
    val prefer60fps: Boolean = true,
    val maxQuality: Boolean = true,

    // Audio
    val audioFormat: String = "MP3",
    val audioQuality: String = "320kbps",
    val normalizeAudio: Boolean = false,
    val trimSilence: Boolean = false,

    // Subtitles
    val embedSubtitles: Boolean = false,
    val subtitleLanguage: String = "en",
    val subtitleFormat: String = "srt",
    val autoSubtitles: Boolean = false,

    // Chapters & Metadata
    val embedChapters: Boolean = false,
    val splitByChapters: Boolean = false,
    val writeMetadata: Boolean = true,
    val embedThumbnail: Boolean = true,

    // SponsorBlock
    val sponsorBlockEnabled: Boolean = false,
    val sponsorBlockCategories: String = "sponsor",
    val sponsorBlockAction: String = "Skip",

    // Network & Cookies
    val cookieSource: String = "None",
    val speedLimit: String = "",
    val proxy: String = "",
    val maxFragments: Int = 16,
    val wifiOnly: Boolean = false,
    val concurrentDownloads: Int = 2,

    // Output
    val downloadPath: String = "Downloads/Omni",
    val outputTemplate: String = "%(title)s.%(ext)s",

    // Appearance
    val themeMode: String = "System default",
    val accentColor: String = "Blue",
    val uiStyle: String = "Comfort",
    val fontFamily: String = "System default",
    val dynamicColor: Boolean = false,

    // Misc
    val showDetailedInfo: Boolean = false,
    val advancedMode: Boolean = false,
    val reduceAnimations: Boolean = false,
    val lowPerfMode: Boolean = false,
    val onboardingCompleted: Boolean = false
)

class UserPreferences(private val context: Context) {

    val preferences: Flow<OmniPreferences> = context.dataStore.data.map { prefs ->
        val savedFont = prefs[PreferenceKeys.FONT_FAMILY] ?: "System default"
        val validFonts = listOf("System default", "Inter", "Roboto", "Poppins", "Serif", "Monospace")
        val validatedFont = if (savedFont in validFonts) savedFont else "System default"

        OmniPreferences(
            // Video
            videoQuality    = prefs[PreferenceKeys.VIDEO_QUALITY]   ?: "Best available",
            videoFormat     = prefs[PreferenceKeys.VIDEO_FORMAT]    ?: "MP4",
            prefer60fps     = prefs[PreferenceKeys.PREFER_60FPS]    ?: true,
            maxQuality      = prefs[PreferenceKeys.MAX_QUALITY]     ?: true,

            // Audio
            audioFormat     = prefs[PreferenceKeys.AUDIO_FORMAT]    ?: "MP3",
            audioQuality    = prefs[PreferenceKeys.AUDIO_QUALITY]   ?: "320kbps",
            normalizeAudio  = prefs[PreferenceKeys.NORMALIZE_AUDIO] ?: false,
            trimSilence     = prefs[PreferenceKeys.TRIM_SILENCE]    ?: false,

            // Subtitles
            embedSubtitles  = prefs[PreferenceKeys.EMBED_SUBTITLES]   ?: false,
            subtitleLanguage = prefs[PreferenceKeys.SUBTITLE_LANGUAGE] ?: "en",
            subtitleFormat  = prefs[PreferenceKeys.SUBTITLE_FORMAT]   ?: "srt",
            autoSubtitles   = prefs[PreferenceKeys.AUTO_SUBTITLES]    ?: false,

            // Chapters & Metadata
            embedChapters   = prefs[PreferenceKeys.EMBED_CHAPTERS]    ?: false,
            splitByChapters = prefs[PreferenceKeys.SPLIT_BY_CHAPTERS] ?: false,
            writeMetadata   = prefs[PreferenceKeys.WRITE_METADATA]    ?: true,
            embedThumbnail  = prefs[PreferenceKeys.EMBED_THUMBNAIL]   ?: true,

            // SponsorBlock
            sponsorBlockEnabled    = prefs[PreferenceKeys.SPONSORBLOCK_ENABLED]    ?: false,
            sponsorBlockCategories = prefs[PreferenceKeys.SPONSORBLOCK_CATEGORIES] ?: "sponsor",
            sponsorBlockAction     = prefs[PreferenceKeys.SPONSORBLOCK_ACTION]     ?: "Skip",

            // Network & Cookies
            cookieSource    = prefs[PreferenceKeys.COOKIE_SOURCE]  ?: "None",
            speedLimit      = prefs[PreferenceKeys.SPEED_LIMIT]    ?: "",
            proxy           = prefs[PreferenceKeys.PROXY]          ?: "",
            maxFragments    = prefs[PreferenceKeys.MAX_FRAGMENTS]  ?: 16,
            wifiOnly        = prefs[PreferenceKeys.WIFI_ONLY]      ?: false,
            concurrentDownloads = prefs[PreferenceKeys.CONCURRENT_DL] ?: 2,

            // Output
            downloadPath    = prefs[PreferenceKeys.DOWNLOAD_PATH]   ?: "Downloads/Omni",
            outputTemplate  = prefs[PreferenceKeys.OUTPUT_TEMPLATE] ?: "%(title)s.%(ext)s",

            // Appearance
            themeMode      = prefs[PreferenceKeys.THEME_MODE]      ?: "System default",
            accentColor    = prefs[PreferenceKeys.ACCENT_COLOR]    ?: "Blue",
            uiStyle        = prefs[PreferenceKeys.UI_STYLE]        ?: "Comfort",
            fontFamily     = validatedFont,
            dynamicColor   = prefs[PreferenceKeys.DYNAMIC_COLOR]   ?: false,

            // Misc
            showDetailedInfo = prefs[PreferenceKeys.SHOW_DETAILED_INFO] ?: false,
            advancedMode   = prefs[PreferenceKeys.ADVANCED_MODE]   ?: false,
            reduceAnimations = prefs[PreferenceKeys.REDUCE_ANIM]   ?: false,
            lowPerfMode    = prefs[PreferenceKeys.LOW_PERF_MODE]   ?: false,
            onboardingCompleted = prefs[PreferenceKeys.ONBOARDING_COMPLETED] ?: false
        )
    }

    // Video
    suspend fun setVideoQuality(v: String)  = save(PreferenceKeys.VIDEO_QUALITY, v)
    suspend fun setVideoFormat(v: String)   = save(PreferenceKeys.VIDEO_FORMAT, v)
    suspend fun setPrefer60fps(v: Boolean)  = save(PreferenceKeys.PREFER_60FPS, v)
    suspend fun setMaxQuality(v: Boolean)   = save(PreferenceKeys.MAX_QUALITY, v)

    // Audio
    suspend fun setAudioFormat(v: String)   = save(PreferenceKeys.AUDIO_FORMAT, v)
    suspend fun setAudioQuality(v: String)  = save(PreferenceKeys.AUDIO_QUALITY, v)
    suspend fun setNormalizeAudio(v: Boolean) = save(PreferenceKeys.NORMALIZE_AUDIO, v)
    suspend fun setTrimSilence(v: Boolean)  = save(PreferenceKeys.TRIM_SILENCE, v)

    // Subtitles
    suspend fun setEmbedSubtitles(v: Boolean)   = save(PreferenceKeys.EMBED_SUBTITLES, v)
    suspend fun setSubtitleLanguage(v: String)  = save(PreferenceKeys.SUBTITLE_LANGUAGE, v)
    suspend fun setSubtitleFormat(v: String)    = save(PreferenceKeys.SUBTITLE_FORMAT, v)
    suspend fun setAutoSubtitles(v: Boolean)    = save(PreferenceKeys.AUTO_SUBTITLES, v)

    // Chapters & Metadata
    suspend fun setEmbedChapters(v: Boolean)    = save(PreferenceKeys.EMBED_CHAPTERS, v)
    suspend fun setSplitByChapters(v: Boolean)  = save(PreferenceKeys.SPLIT_BY_CHAPTERS, v)
    suspend fun setWriteMetadata(v: Boolean)    = save(PreferenceKeys.WRITE_METADATA, v)
    suspend fun setEmbedThumbnail(v: Boolean)   = save(PreferenceKeys.EMBED_THUMBNAIL, v)

    // SponsorBlock
    suspend fun setSponsorBlockEnabled(v: Boolean)    = save(PreferenceKeys.SPONSORBLOCK_ENABLED, v)
    suspend fun setSponsorBlockCategories(v: String)  = save(PreferenceKeys.SPONSORBLOCK_CATEGORIES, v)
    suspend fun setSponsorBlockAction(v: String)      = save(PreferenceKeys.SPONSORBLOCK_ACTION, v)

    // Network & Cookies
    suspend fun setCookieSource(v: String)      = save(PreferenceKeys.COOKIE_SOURCE, v)
    suspend fun setSpeedLimit(v: String)        = save(PreferenceKeys.SPEED_LIMIT, v)
    suspend fun setProxy(v: String)             = save(PreferenceKeys.PROXY, v)
    suspend fun setMaxFragments(v: Int)         = save(PreferenceKeys.MAX_FRAGMENTS, v)
    suspend fun setWifiOnly(v: Boolean)         = save(PreferenceKeys.WIFI_ONLY, v)
    suspend fun setConcurrentDownloads(v: Int)  = save(PreferenceKeys.CONCURRENT_DL, v)

    // Output
    suspend fun setDownloadPath(v: String)      = save(PreferenceKeys.DOWNLOAD_PATH, v)
    suspend fun setOutputTemplate(v: String)    = save(PreferenceKeys.OUTPUT_TEMPLATE, v)

    // Appearance
    suspend fun setThemeMode(v: String)         = save(PreferenceKeys.THEME_MODE, v)
    suspend fun setAccentColor(v: String)       = save(PreferenceKeys.ACCENT_COLOR, v)
    suspend fun setUiStyle(v: String)           = save(PreferenceKeys.UI_STYLE, v)
    suspend fun setFontFamily(v: String)        = save(PreferenceKeys.FONT_FAMILY, v)
    suspend fun setDynamicColor(v: Boolean)     = save(PreferenceKeys.DYNAMIC_COLOR, v)

    // Misc
    suspend fun setShowDetailedInfo(v: Boolean) = save(PreferenceKeys.SHOW_DETAILED_INFO, v)
    suspend fun setAdvancedMode(v: Boolean)     = save(PreferenceKeys.ADVANCED_MODE, v)
    suspend fun setReduceAnimations(v: Boolean) = save(PreferenceKeys.REDUCE_ANIM, v)
    suspend fun setLowPerfMode(v: Boolean)      = save(PreferenceKeys.LOW_PERF_MODE, v)
    suspend fun setOnboardingCompleted(v: Boolean) = save(PreferenceKeys.ONBOARDING_COMPLETED, v)

    private suspend fun <T> save(key: Preferences.Key<T>, value: T) {
        context.dataStore.edit { it[key] = value }
    }
}
