package com.omni.app.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var storageGranted by remember { mutableStateOf(checkStoragePermission(context)) }
    var musicGranted by remember { mutableStateOf(checkMusicPermission(context)) }
    var videoGranted by remember { mutableStateOf(checkVideoPermission(context)) }
    var notificationGranted by remember { mutableStateOf(checkNotificationPermission(context)) }

    val storageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        storageGranted = checkStoragePermission(context)
    }

    val musicLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        musicGranted = it
    }

    val videoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        videoGranted = it
    }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationGranted = it
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F17))
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(scrollState)
            .padding(24.dp)
    ) {
        Text(text = "Hello!", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Medium)
        Text(
            text = buildAnnotatedString {
                append("Welcome to ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Omni") }
            },
            color = Color.White,
            fontSize = 28.sp
        )

        Spacer(Modifier.height(48.dp))

        PermissionItem(
            index = 1,
            title = "Storage Access",
            description = "The app needs permission to manage files and folders for your downloads",
            icon = Icons.Rounded.Folder,
            isGranted = storageGranted,
            onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (!Environment.isExternalStorageManager()) {
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    } else {
                        storageGranted = true
                    }
                } else {
                    storageLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
                }
            }
        )

        PermissionItem(
            index = 2,
            title = "Music & Audio",
            description = "Needed to scan and play your audio files",
            icon = Icons.Rounded.MusicNote,
            isGranted = musicGranted,
            onClick = {
                if (!musicGranted) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        musicLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO)
                    } else {
                        storageLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
                    }
                }
            }
        )

        PermissionItem(
            index = 3,
            title = "Photos & Videos",
            description = "Needed to scan and play your video files",
            icon = Icons.Rounded.Movie,
            isGranted = videoGranted,
            onClick = {
                if (!videoGranted) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        videoLauncher.launch(Manifest.permission.READ_MEDIA_VIDEO)
                    } else {
                        storageLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
                    }
                }
            }
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PermissionItem(
                index = 4,
                title = "Notifications",
                description = "Show the media player in your notification center",
                icon = Icons.Rounded.Notifications,
                isGranted = notificationGranted,
                onClick = {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            )
        }

        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onComplete,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White.copy(alpha = 0.1f),
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(28.dp),
            enabled = storageGranted && musicGranted && videoGranted && notificationGranted
        ) {
            Text("Let's Go", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }

    LaunchedEffect(Unit) {
        while(true) {
            kotlinx.coroutines.delay(1000)
            storageGranted = checkStoragePermission(context)
            musicGranted = checkMusicPermission(context)
            videoGranted = checkVideoPermission(context)
            notificationGranted = checkNotificationPermission(context)
        }
    }
}

@Composable
private fun PermissionItem(
    index: Int,
    title: String,
    description: String,
    icon: ImageVector,
    isGranted: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier.size(32.dp).background(Color.White.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(index.toString(), color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
        }

        Spacer(Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(description, color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            Surface(
                onClick = onClick,
                color = Color.Transparent,
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Grant access", color = Color.White, fontWeight = FontWeight.Medium)
                    }
                    if (isGranted) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = Color(0xFF81C784), modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

private fun checkStoragePermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }
}

private fun checkMusicPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }
}

private fun checkVideoPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }
}

private fun checkNotificationPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else true
}
