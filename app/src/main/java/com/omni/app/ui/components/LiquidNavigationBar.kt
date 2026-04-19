package com.omni.app.ui.components

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.lerp
import com.omni.app.ui.theme.LocalOmniPreferences
import kotlin.math.abs

data class LiquidNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

@Composable
fun LiquidNavigationBar(
    items: List<LiquidNavItem>,
    selectedRoute: String?,
    onItemClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val settings = LocalOmniPreferences.current
    val isDark = when (settings.themeMode) {
        "Light" -> false
        "Dark" -> true
        else -> isSystemInDarkTheme()
    }
    val accentColor   = MaterialTheme.colorScheme.primary
    val selectedIndex = items.indexOfFirst { it.route == selectedRoute }.coerceAtLeast(0)
    val isLowPerf = settings.lowPerfMode

    val animIndex = remember { Animatable(selectedIndex.toFloat()) }
    LaunchedEffect(selectedIndex) {
        if (isLowPerf) {
            animIndex.snapTo(selectedIndex.toFloat())
        } else {
            animIndex.animateTo(
                selectedIndex.toFloat(),
                spring(dampingRatio = 0.72f, stiffness = 400f)
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        if (!isLowPerf) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .graphicsLayer {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            renderEffect = RenderEffect
                                .createBlurEffect(40f, 40f, Shader.TileMode.CLAMP)
                                .asComposeRenderEffect()
                        }
                        clip = true
                        shape = RoundedCornerShape(32.dp)
                    }
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(if (isDark) Color(0xFF1A1B1F) else Color(0xFFF5F5F7))
                .border(
                    width = 0.8.dp,
                    color = if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(32.dp)
                )
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
        ) {
            val density    = LocalDensity.current
            val tabWidthPx = constraints.maxWidth.toFloat() / items.size

            val pillScaleX by remember(isLowPerf) {
                derivedStateOf {
                    if (isLowPerf) 1f else {
                        val v = animIndex.velocity / 20f
                        1f / (1f - (v * 0.8f).coerceAtMost(0.25f).coerceAtLeast(-0.25f))
                    }
                }
            }
            val pillScaleY by remember(isLowPerf) {
                derivedStateOf {
                    if (isLowPerf) 1f else {
                        1f - (abs(animIndex.velocity / 20f) * 0.18f).coerceAtMost(0.13f).coerceAtLeast(0f)
                    }
                }
            }

            Box(
                modifier = Modifier
                    .graphicsLayer {
                        translationX = animIndex.value * tabWidthPx
                        scaleX = pillScaleX
                        scaleY = pillScaleY
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                    }
                    .width(with(density) { tabWidthPx.toDp() })
                    .fillMaxHeight()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                accentColor.copy(alpha = if (isDark) 0.30f else 0.18f),
                                accentColor.copy(alpha = if (isDark) 0.12f else 0.07f)
                            )
                        )
                    )
                    .border(
                        width = 0.8.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                accentColor.copy(alpha = if (isDark) 0.55f else 0.38f),
                                accentColor.copy(alpha = if (isDark) 0.15f else 0.10f)
                            )
                        ),
                        shape = RoundedCornerShape(22.dp)
                    )
            )

            Row(modifier = Modifier.fillMaxSize()) {
                items.forEachIndexed { index, item ->
                    val progress = if (isLowPerf) {
                        if (index == selectedIndex) 1f else 0f
                    } else {
                        (1f - abs(animIndex.value - index)).coerceAtMost(1f).coerceAtLeast(0f)
                    }

                    val unselected = if (isDark)
                        Color.White.copy(alpha = 0.50f)
                    else
                        Color.Black.copy(alpha = 0.45f)

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onItemClick(item.route) },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .offset(y = (progress * 2).dp)
                                .graphicsLayer {
                                    if (!isLowPerf) {
                                        val scale = lerp(1f, 1.12f, progress)
                                        scaleX = scale
                                        scaleY = scale
                                    }
                                }
                        ) {
                            Icon(
                                imageVector       = item.icon,
                                contentDescription = item.label,
                                tint              = lerp(unselected, accentColor, progress),
                                modifier          = Modifier.size(24.dp)
                            )
                            if (progress > 0.5f) {
                                Text(
                                    text          = item.label,
                                    color         = accentColor,
                                    fontSize      = 10.sp,
                                    fontWeight    = FontWeight.ExtraBold,
                                    letterSpacing = 0.02.sp,
                                    maxLines      = 1,
                                    modifier      = Modifier.padding(top = 1.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}