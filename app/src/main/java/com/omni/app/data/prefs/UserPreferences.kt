package com.omni.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "omni_prefs")

object PreferenceKeys {
    val VIDEO_QUALITY   = stringPreferencesKey("video_quality")
    val VIDEO_FORMAT    = stringPreferencesKey("video_format")
    val PREFER_60FPS    = booleanPreferencesKey("prefer_60fps")
    val AUDIO_FORMAT    = stringPreferencesKey("audio_format")
    val AUDIO_QUALITY   = stringPreferencesKey("audio_quality")
    val MAX_QUALITY     = booleanPreferencesKey("max_quality")
    val DOWNLOAD_PATH   = stringPreferencesKey("download_path")
    val EMBED_THUMBNAIL = booleanPreferencesKey("embed_thumbnail")
    val WIFI_ONLY       = booleanPreferencesKey("wifi_only")
    val CONCURRENT_DL   = intPreferencesKey("concurrent_downloads")
    val THEME_MODE      = stringPreferencesKey("theme_mode")
    val ACCENT_COLOR    = stringPreferencesKey("accent_color")
    val UI_STYLE        = stringPreferencesKey("ui_style")
    val FONT_FAMILY     = stringPreferencesKey("font_family")
    val DYNAMIC_COLOR   = booleanPreferencesKey("dynamic_color")
    val SHOW_DETAILED_INFO = booleanPreferencesKey("show_detailed_info")
    val ADVANCED_MODE   = booleanPreferencesKey("advanced_mode")
    val REDUCE_ANIM     = booleanPreferencesKey("reduce_animations")
    val LOW_PERF_MODE   = booleanPreferencesKey("low_perf_mode")
}

data class OmniPreferences(
    val videoQuality: String = "Best available",
    val videoFormat: String = "MP4",
    val prefer60fps: Boolean = true,
    val audioFormat: String = "MP3",
    val audioQuality: String = "320kbps",
    val maxQuality: Boolean = true,
    val downloadPath: String = "Downloads/Omni",
    val embedThumbnail: Boolean = true,
    val wifiOnly: Boolean = false,
    val concurrentDownloads: Int = 2,
    val themeMode: String = "System default",
    val accentColor: String = "Blue",
    val uiStyle: String = "Comfort",
    val fontFamily: String = "System default",
    val dynamicColor: Boolean = false,
    val showDetailedInfo: Boolean = false,
    val advancedMode: Boolean = false,
    val reduceAnimations: Boolean = false,
    val lowPerfMode: Boolean = false
)

class UserPreferences(private val context: Context) {

    val preferences: Flow<OmniPreferences> = context.dataStore.data.map { prefs ->
        val savedFont = prefs[PreferenceKeys.FONT_FAMILY] ?: "System default"
        val validFonts = listOf("System default", "Inter", "Roboto", "Poppins", "Serif", "Monospace")
        val validatedFont = if (savedFont in validFonts) savedFont else "System default"

        OmniPreferences(
            videoQuality    = prefs[PreferenceKeys.VIDEO_QUALITY]   ?: "Best available",
            videoFormat     = prefs[PreferenceKeys.VIDEO_FORMAT]    ?: "MP4",
            prefer60fps     = prefs[PreferenceKeys.PREFER_60FPS]    ?: true,
            audioFormat     = prefs[PreferenceKeys.AUDIO_FORMAT]    ?: "MP3",
            audioQuality    = prefs[PreferenceKeys.AUDIO_QUALITY]   ?: "320kbps",
            maxQuality      = prefs[PreferenceKeys.MAX_QUALITY]     ?: true,
            downloadPath    = prefs[PreferenceKeys.DOWNLOAD_PATH]   ?: "Downloads/Omni",
            embedThumbnail  = prefs[PreferenceKeys.EMBED_THUMBNAIL] ?: true,
            wifiOnly        = prefs[PreferenceKeys.WIFI_ONLY]       ?: false,
            concurrentDownloads = prefs[PreferenceKeys.CONCURRENT_DL] ?: 2,
            themeMode      = prefs[PreferenceKeys.THEME_MODE]      ?: "System default",
            accentColor    = prefs[PreferenceKeys.ACCENT_COLOR]    ?: "Blue",
            uiStyle        = prefs[PreferenceKeys.UI_STYLE]        ?: "Comfort",
            fontFamily     = validatedFont,
            dynamicColor   = prefs[PreferenceKeys.DYNAMIC_COLOR]   ?: false,
            showDetailedInfo = prefs[PreferenceKeys.SHOW_DETAILED_INFO] ?: false,
            advancedMode   = prefs[PreferenceKeys.ADVANCED_MODE]   ?: false,
            reduceAnimations = prefs[PreferenceKeys.REDUCE_ANIM]     ?: false,
            lowPerfMode    = prefs[PreferenceKeys.LOW_PERF_MODE]    ?: false
        )
    }

    suspend fun setVideoQuality(value: String) = save(PreferenceKeys.VIDEO_QUALITY, value)
    suspend fun setVideoFormat(value: String)   = save(PreferenceKeys.VIDEO_FORMAT, value)
    suspend fun setPrefer60fps(value: Boolean)  = save(PreferenceKeys.PREFER_60FPS, value)
    suspend fun setAudioFormat(value: String)   = save(PreferenceKeys.AUDIO_FORMAT, value)
    suspend fun setAudioQuality(value: String)  = save(PreferenceKeys.AUDIO_QUALITY, value)
    suspend fun setMaxQuality(value: Boolean)   = save(PreferenceKeys.MAX_QUALITY, value)
    suspend fun setDownloadPath(value: String)  = save(PreferenceKeys.DOWNLOAD_PATH, value)
    suspend fun setEmbedThumbnail(value: Boolean) = save(PreferenceKeys.EMBED_THUMBNAIL, value)
    suspend fun setWifiOnly(value: Boolean)     = save(PreferenceKeys.WIFI_ONLY, value)
    suspend fun setConcurrentDownloads(value: Int) = save(PreferenceKeys.CONCURRENT_DL, value)
    suspend fun setThemeMode(value: String)      = save(PreferenceKeys.THEME_MODE, value)
    suspend fun setAccentColor(value: String)    = save(PreferenceKeys.ACCENT_COLOR, value)
    suspend fun setUiStyle(value: String)        = save(PreferenceKeys.UI_STYLE, value)
    suspend fun setFontFamily(value: String)     = save(PreferenceKeys.FONT_FAMILY, value)
    suspend fun setDynamicColor(value: Boolean)  = save(PreferenceKeys.DYNAMIC_COLOR, value)
    suspend fun setShowDetailedInfo(value: Boolean) = save(PreferenceKeys.SHOW_DETAILED_INFO, value)
    suspend fun setAdvancedMode(value: Boolean)   = save(PreferenceKeys.ADVANCED_MODE, value)
    suspend fun setReduceAnimations(value: Boolean) = save(PreferenceKeys.REDUCE_ANIM, value)
    suspend fun setLowPerfMode(value: Boolean)   = save(PreferenceKeys.LOW_PERF_MODE, value)

    private suspend fun <T> save(key: Preferences.Key<T>, value: T) {
        context.dataStore.edit { it[key] = value }
    }
}
