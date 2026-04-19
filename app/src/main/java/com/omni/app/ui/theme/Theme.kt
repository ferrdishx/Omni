package com.omni.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.omni.app.data.prefs.OmniPreferences
import com.omni.app.data.prefs.UserPreferences

data class OmniDimensions(
    val gridSpacing: Dp = 12.dp,
    val itemPadding: Dp = 12.dp,
    val cornerRadius: Dp = 12.dp,
    val iconSize: Dp = 24.dp,
    val titleSize: Float = 1f
)

val LocalOmniDimensions = staticCompositionLocalOf { OmniDimensions() }
val LocalOmniPreferences = staticCompositionLocalOf { OmniPreferences() }

@Composable
fun OmniTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { UserPreferences(context) }
    val state by prefs.preferences.collectAsState(initial = OmniPreferences())

    val darkTheme = when (state.themeMode) {
        "Light" -> false
        "Dark" -> true
        else -> isSystemInDarkTheme()
    }

    val useDynamicColor = state.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colorScheme = when {
        useDynamicColor -> {
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        state.accentColor == "Blue" -> if (darkTheme) darkColorScheme(primary = BluePrimaryDark) else lightColorScheme(primary = BluePrimary)
        state.accentColor == "Green" -> if (darkTheme) darkColorScheme(primary = GreenPrimaryDark) else lightColorScheme(primary = GreenPrimary)
        state.accentColor == "Red" -> if (darkTheme) darkColorScheme(primary = RedPrimaryDark) else lightColorScheme(primary = RedPrimary)
        state.accentColor == "Purple" -> if (darkTheme) darkColorScheme(primary = Purple80) else lightColorScheme(primary = Purple40)
        darkTheme -> darkColorScheme(primary = Purple80, secondary = PurpleGrey80, tertiary = Pink80)
        else -> lightColorScheme(primary = Purple40, secondary = PurpleGrey40, tertiary = Pink40)
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    val fontFamily = remember(state.fontFamily) {
        try {
            when (state.fontFamily) {
                "Inter" -> InterFont
                "Roboto" -> RobotoFont
                "Poppins" -> PoppinsFont
                "Serif" -> FontFamily.Serif
                "Monospace" -> FontFamily.Monospace
                else -> FontFamily.Default
            }
        } catch (e: Exception) {
            FontFamily.Default
        }
    }

    val typography = remember(fontFamily) {
        getTypography(fontFamily)
    }

    val dimensions = when (state.uiStyle) {
        "Compact" -> OmniDimensions(
            gridSpacing = 8.dp,
            itemPadding = 8.dp,
            cornerRadius = 8.dp,
            iconSize = 20.dp,
            titleSize = 0.9f
        )
        "Expanded" -> OmniDimensions(
            gridSpacing = 16.dp,
            itemPadding = 16.dp,
            cornerRadius = 16.dp,
            iconSize = 28.dp,
            titleSize = 1.1f
        )
        else -> OmniDimensions() // Comfort
    }

    CompositionLocalProvider(
        LocalOmniDimensions provides dimensions,
        LocalOmniPreferences provides state
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography
        ) {
            ProvideTextStyle(value = MaterialTheme.typography.bodyLarge) {
                content()
            }
        }
    }
}
