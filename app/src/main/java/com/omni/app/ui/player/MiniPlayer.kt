package com.omni.app.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.omni.app.data.prefs.OmniPreferences
import com.omni.app.data.prefs.UserPreferences
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
fun MiniPlayer(
    viewModel: OmniPlayerViewModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentMediaItem by viewModel.currentMediaItem.collectAsStateWithLifecycle()
    val mediaMetadata by viewModel.mediaMetadata.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val currentPosition by viewModel.currentPosition.collectAsStateWithLifecycle()
    val duration by viewModel.duration.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val prefs = remember { UserPreferences(context) }
    val settings by prefs.preferences.collectAsStateWithLifecycle(initialValue = OmniPreferences())
    val showDetailedInfo = settings.showDetailedInfo
    val isLowPerf = settings.lowPerfMode

    if (currentMediaItem == null) return

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(68.dp),
        color = if (isLowPerf) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
        tonalElevation = if (isLowPerf) 0.dp else 8.dp,
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(
            0.5.dp, 
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
        )
    ) {
        Column {
            if (duration > 0) {
                LinearProgressIndicator(
                    progress = { currentPosition.toFloat() / duration.toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.Transparent,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(onClick = onClick)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = currentMediaItem?.mediaMetadata?.artworkUri,
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )

                Spacer(Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = (currentMediaItem?.mediaMetadata?.title?.toString() ?: "Unknown Title").let { title ->
                            if (showDetailedInfo) title else title.substringBeforeLast('.')
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee()
                    )

                    val displayArtist = remember(mediaMetadata) {
                        val artist = mediaMetadata.artist?.toString() ?: mediaMetadata.albumArtist?.toString()
                        val omniAuthor = mediaMetadata.extras?.getString("omni_author")
                        
                        when {
                            !artist.isNullOrBlank() && artist != "Audio" && artist != "Video" -> artist
                            !omniAuthor.isNullOrBlank() && omniAuthor != "Audio" && omniAuthor != "Video" && omniAuthor != "Local File" && omniAuthor != "Unknown" -> omniAuthor
                            else -> "Unknown"
                        }
                    }

                    Text(
                        text = displayArtist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                val scale by animateFloatAsState(
                    targetValue = if (isPressed && !isLowPerf) 0.85f else 1f,
                    animationSpec = spring(stiffness = Spring.StiffnessLow),
                    label = "scale"
                )

                IconButton(
                    onClick = { viewModel.playPause() },
                    interactionSource = interactionSource,
                    modifier = Modifier.graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play"
                    )
                }
            }
        }
    }
}
