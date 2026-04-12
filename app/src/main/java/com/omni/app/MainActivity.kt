package com.omni.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import android.app.PictureInPictureParams
import android.util.Rational
import com.omni.app.ui.player.MiniPlayer
import com.omni.app.ui.player.OmniPlayerViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.animation.*
import androidx.compose.foundation.layout.Column
import com.omni.app.ui.theme.LocalOmniPreferences
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.omni.app.navigation.OmniNavHost
import com.omni.app.navigation.Screen
import com.omni.app.ui.theme.OmniTheme
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            val playerViewModel: OmniPlayerViewModel by lazy { 
                androidx.lifecycle.ViewModelProvider(this)[OmniPlayerViewModel::class.java]
            }
            val currentMedia = playerViewModel.currentMediaItem.value
            val isVideo = currentMedia?.mediaMetadata?.artist?.toString() == "Video"
            
            if (isVideo && playerViewModel.isPlaying.value) {
                enterPictureInPictureMode(PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build())
            }
        }
    }

    fun updatePiPParams(isVideo: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .setAutoEnterEnabled(isVideo)
                .build()
            setPictureInPictureParams(params)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                YoutubeDL.getInstance().init(application)
                FFmpeg.getInstance().init(application)
                YoutubeDL.getInstance().updateYoutubeDL(application)
                Log.d("Omni", "Engine initialized successfully")
            } catch (e: Exception) {
                Log.e("Omni", "Initialization error: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(applicationContext, "Engine error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        enableEdgeToEdge()
        val sharedUrl = intent?.getStringExtra(Intent.EXTRA_TEXT)
        setContent { OmniTheme { OmniApp(sharedUrl = sharedUrl) } }
    }
}

@Composable
fun OmniApp(sharedUrl: String? = null) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Request permissions
    val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS
        )
    } else {
        arrayOf(
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        results.forEach { (permission, granted) ->
            Log.d("OmniApp", "Permission $permission granted: $granted")
        }
    }

    LaunchedEffect(Unit) {
        val needsRequest = permissionsToRequest.any {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needsRequest) {
            launcher.launch(permissionsToRequest)
        }
    }

    val navItems = listOf(
        Triple(Screen.Home,      "Home",      Icons.Rounded.Home),
        Triple(Screen.Downloads, "Downloads", Icons.Rounded.Download),
        Triple(Screen.Library,   "Library",   Icons.Rounded.VideoLibrary),
    )

    val isPlayerScreen = currentDestination?.route == Screen.Player.route
    val showBottomBar = !isPlayerScreen && currentDestination?.route != Screen.Settings.route

    val settings = LocalOmniPreferences.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed && !settings.reduceAnimations) 0.92f else 1f,
        animationSpec = tween(100),
        label = "scale"
    )

    val playerViewModel: OmniPlayerViewModel = viewModel()

    val currentMedia by playerViewModel.currentMediaItem.collectAsState()
    LaunchedEffect(currentMedia) {
        val isVideo = currentMedia?.mediaMetadata?.artist?.toString() == "Video"
        (context as? MainActivity)?.updatePiPParams(isVideo)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            Column {
                if (!isPlayerScreen) {
                    MiniPlayer(
                        viewModel = playerViewModel,
                        onClick = {
                            navController.navigate(Screen.Player.route) {
                                launchSingleTop = true
                            }
                        }
                    )
                }
                if (showBottomBar) {
                    NavigationBar {
                        navItems.forEach { (screen, label, icon) ->
                            val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                            
                            val itemScale by animateFloatAsState(
                                targetValue = if (selected && !settings.reduceAnimations) 1.15f else 1f,
                                animationSpec = tween(200),
                                label = "item_scale"
                            )

                            NavigationBarItem(
                                selected = selected,
                                interactionSource = interactionSource,
                                onClick = {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { 
                                    Icon(
                                        icon, 
                                        contentDescription = label,
                                        modifier = Modifier.scale(if (selected) itemScale else 1f)
                                    ) 
                                },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .scale(scale)
        ) {
            OmniNavHost(
                navController = navController,
                playerViewModel = playerViewModel
            )
        }
    }
}
