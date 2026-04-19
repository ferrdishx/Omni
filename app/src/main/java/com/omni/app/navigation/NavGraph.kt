package com.omni.app.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.omni.app.ui.downloads.DownloadsScreen
import com.omni.app.ui.favorites.FavoritesScreen
import com.omni.app.ui.home.HomeScreen
import com.omni.app.ui.library.LibraryScreen
import com.omni.app.ui.settings.SettingsScreen
import com.omni.app.ui.theme.LocalOmniPreferences
import com.omni.app.ui.player.OmniPlayerViewModel

sealed class Screen(val route: String) {
    object Home      : Screen("home")
    object Downloads : Screen("downloads")
    object Library   : Screen("library")
    object Favorites : Screen("favorites")
    object Player    : Screen("player")
    object Settings  : Screen("settings")
}

@Composable
fun OmniNavHost(
    navController: NavHostController,
    playerViewModel: OmniPlayerViewModel,
    onOpenPlayer: () -> Unit
) {
    val prefs by LocalOmniPreferences.current.let { androidx.compose.runtime.rememberUpdatedState(it) }
    val duration = if (prefs.reduceAnimations || prefs.lowPerfMode) 0 else 300

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        enterTransition = {
            fadeIn(animationSpec = tween(duration, easing = FastOutSlowInEasing), initialAlpha = 0.8f) +
            scaleIn(animationSpec = tween(duration, easing = FastOutSlowInEasing), initialScale = 0.95f)
        },
        exitTransition = {
            fadeOut(animationSpec = tween(duration, easing = FastOutSlowInEasing)) +
            scaleOut(animationSpec = tween(duration, easing = FastOutSlowInEasing), targetScale = 1.05f)
        },
        popEnterTransition = {
            fadeIn(animationSpec = tween(duration, easing = FastOutSlowInEasing), initialAlpha = 0.8f) +
            scaleIn(animationSpec = tween(duration, easing = FastOutSlowInEasing), initialScale = 1.05f)
        },
        popExitTransition = {
            fadeOut(animationSpec = tween(duration, easing = FastOutSlowInEasing)) +
            scaleOut(animationSpec = tween(duration, easing = FastOutSlowInEasing), targetScale = 0.95f)
        }
    ) {
        composable(Screen.Home.route) {
            HomeScreen(onSettingsClick = { navController.navigate(Screen.Settings.route) })
        }
        composable(Screen.Downloads.route) {
            DownloadsScreen(
                playerViewModel = playerViewModel,
                onNavigateToPlayer = { onOpenPlayer() }
            )
        }
        composable(Screen.Library.route) {
            LibraryScreen(
                playerViewModel = playerViewModel,
                onNavigateToPlayer = { onOpenPlayer() }
            )
        }
        composable(Screen.Favorites.route) {
            FavoritesScreen(
                playerViewModel = playerViewModel,
                onNavigateToPlayer = { onOpenPlayer() }
            )
        }
        /* 
        composable(
            route = Screen.Player.route,
            enterTransition = {
                slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(duration, easing = FastOutSlowInEasing)
                ) + fadeIn()
            },
            exitTransition = {
                slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(duration, easing = FastOutSlowInEasing)
                ) + fadeOut()
            }
        ) {
            com.omni.app.ui.player.OmniPlayerScreen(
                viewModel = playerViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        */

        composable(Screen.Settings.route) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
