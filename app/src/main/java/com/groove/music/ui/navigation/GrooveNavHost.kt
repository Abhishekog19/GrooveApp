package com.groove.music.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.groove.music.core.network.ServiceStatusChecker
import com.groove.music.ui.screens.library.LibraryScreen
import com.groove.music.ui.screens.player.MiniPlayerBar
import com.groove.music.ui.screens.player.NowPlayingScreen
import com.groove.music.ui.screens.player.PlayerViewModel
import com.groove.music.ui.screens.playlists.PlaylistsScreen
import com.groove.music.ui.screens.search.SearchScreen
import com.groove.music.ui.screens.setup.SetupScreen
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import dagger.hilt.android.EntryPointAccessors

sealed class Screen(val route: String) {
    object Library   : Screen("library")
    object Search    : Screen("search")
    object Playlists : Screen("playlists")
    object Import    : Screen("import")
    object NowPlaying: Screen("now_playing")
    object Setup     : Screen("setup")
}

data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val bottomNavItems = listOf(
    BottomNavItem(Screen.Library,   "Library",   Icons.Filled.LibraryMusic,  Icons.Outlined.LibraryMusic),
    BottomNavItem(Screen.Search,    "Search",    Icons.Filled.Search,        Icons.Outlined.Search),
    BottomNavItem(Screen.Playlists, "Playlists", Icons.Filled.QueueMusic,    Icons.Outlined.QueueMusic),
    BottomNavItem(Screen.Import,    "Import",    Icons.Filled.FileDownload,  Icons.Outlined.FileDownload),
)

@Composable
fun GrooveNavHost(serviceStatusChecker: ServiceStatusChecker) {
    val navController     = rememberNavController()
    val playerViewModel   = hiltViewModel<PlayerViewModel>()
    val playerState       by playerViewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        bottomBar = {
            Column {
                // Mini player bar sits above the bottom nav — mirrors fixed bottom bar in web
                if (playerState.currentSong != null) {
                    MiniPlayerBar(
                        state      = playerState,
                        onTogglePlay = { playerViewModel.togglePlay() },
                        onClick    = { navController.navigate(Screen.NowPlaying.route) }
                    )
                }

                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDest = navBackStackEntry?.destination

                // Hide bottom nav on NowPlaying screen (mirrors full-screen Player.jsx)
                if (currentDest?.route != Screen.NowPlaying.route) {
                    NavigationBar(
                        containerColor = com.groove.music.ui.theme.GrooveSurface,
                        tonalElevation = androidx.compose.ui.unit.Dp(0f)
                    ) {
                        bottomNavItems.forEach { item ->
                            val selected = currentDest?.hierarchy
                                ?.any { it.route == item.screen.route } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick  = {
                                    navController.navigate(item.screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon
                                                      else item.unselectedIcon,
                                        contentDescription = item.label
                                    )
                                },
                                label = { Text(item.label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController    = navController,
            startDestination = Screen.Library.route,
            modifier         = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Library.route) {
                LibraryScreen(
                    playerViewModel = playerViewModel,
                    onNavigateToPlayer = { navController.navigate(Screen.NowPlaying.route) }
                )
            }
            composable(Screen.Search.route) {
                SearchScreen(
                    playerViewModel = playerViewModel,
                    onNavigateToPlayer = { navController.navigate(Screen.NowPlaying.route) },
                    serviceStatusChecker = serviceStatusChecker
                )
            }
            composable(Screen.Playlists.route) {
                PlaylistsScreen(playerViewModel = playerViewModel)
            }
            composable(Screen.Import.route) {
                com.groove.music.ui.screens.imports.ImportScreen(
                    onPlaySong = { song ->
                        playerViewModel.playSong(song)
                        navController.navigate(Screen.NowPlaying.route)
                    },
                    serviceStatusChecker = serviceStatusChecker
                )
            }
            composable(Screen.NowPlaying.route) {
                NowPlayingScreen(
                    playerViewModel = playerViewModel,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Setup.route) {
                SetupScreen(onSetupComplete = { navController.popBackStack() })
            }
        }
    }
}
