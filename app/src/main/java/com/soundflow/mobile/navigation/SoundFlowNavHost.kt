package com.soundflow.mobile.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.soundflow.mobile.R
import com.soundflow.mobile.connectivity.ConnectivityViewModel
import com.soundflow.mobile.downloads.DownloadsViewModel
import com.soundflow.mobile.player.PlayerViewModel
import com.soundflow.mobile.playlists.PlaylistsViewModel
import com.soundflow.mobile.search.SearchViewModel
import com.soundflow.mobile.ui.components.MiniPlayerBar
import com.soundflow.mobile.ui.screens.DownloadsScreen
import com.soundflow.mobile.ui.screens.HomeScreen
import com.soundflow.mobile.ui.screens.PlaylistsScreen
import com.soundflow.mobile.ui.screens.ProfileScreen
import com.soundflow.mobile.ui.screens.SearchScreen

@UnstableApi
@Composable
fun SoundFlowNavHost() {
    val navController = rememberNavController()
    // viewModel() ici est scopé à l'Activity (SoundFlowNavHost n'est pas une destination de nav),
    // donc l'état du lecteur, des playlists et des téléchargements survit à la navigation.
    val playerViewModel: PlayerViewModel = viewModel()
    val playlistsViewModel: PlaylistsViewModel = viewModel()
    val downloadsViewModel: DownloadsViewModel = viewModel()
    val searchViewModel: SearchViewModel = viewModel()
    val connectivityViewModel: ConnectivityViewModel = viewModel()
    val playerState by playerViewModel.state.collectAsStateWithLifecycle()
    val player by playerViewModel.player.collectAsStateWithLifecycle()
    val playbackMode by playerViewModel.playbackMode.collectAsStateWithLifecycle()
    val isOnline by connectivityViewModel.isOnline.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val connectionLostText = stringResource(R.string.connection_lost)
    val connectionRestoredText = stringResource(R.string.connection_restored)

    var previousIsOnline by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(isOnline) {
        val prev = previousIsOnline
        previousIsOnline = isOnline
        if (prev != null) {
            if (!isOnline) {
                snackbarHostState.showSnackbar(connectionLostText)
            } else {
                snackbarHostState.showSnackbar(connectionRestoredText)
            }
        }
    }

    fun goToLibrary() {
        navController.navigate(SoundFlowDestination.LIBRARY.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Column {
                if (!isOnline) {
                    Surface(color = MaterialTheme.colorScheme.errorContainer) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_no_connection),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                            )
                            Text(
                                text = stringResource(R.string.offline_indicator),
                                modifier = Modifier.padding(start = 8.dp),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
                playerState.currentTrack?.let { track ->
                    MiniPlayerBar(
                        track = track,
                        isPlaying = playerState.isPlaying,
                        isBuffering = playerState.isBuffering,
                        positionMs = playerState.positionMs,
                        durationMs = playerState.durationMs,
                        player = player,
                        playbackMode = playbackMode,
                        playerViewModel = playerViewModel,
                        playlistsViewModel = playlistsViewModel,
                        downloadsViewModel = downloadsViewModel,
                    )
                }
                NavigationBar {
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentDestination = navBackStackEntry?.destination

                    SoundFlowDestination.entries.forEach { destination ->
                        val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
                        val label = stringResource(destination.labelResId)
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = label) },
                            label = { Text(label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = SoundFlowDestination.HOME.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(SoundFlowDestination.HOME.route) {
                HomeScreen(
                    playerViewModel = playerViewModel,
                    playlistsViewModel = playlistsViewModel,
                    downloadsViewModel = downloadsViewModel,
                    onArtistClick = { artist ->
                        searchViewModel.onQueryChange(artist)
                        searchViewModel.onSearchSubmitted()
                        navController.navigate(SoundFlowDestination.SEARCH.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onOpenPlaylists = { goToLibrary() },
                )
            }
            composable(SoundFlowDestination.SEARCH.route) {
                SearchScreen(playerViewModel, playlistsViewModel, downloadsViewModel, searchViewModel)
            }
            composable(SoundFlowDestination.LIBRARY.route) { PlaylistsScreen(playerViewModel, playlistsViewModel, downloadsViewModel) }
            composable(SoundFlowDestination.DOWNLOADS.route) {
                DownloadsScreen(downloadsViewModel, playlistsViewModel, playerViewModel)
            }
            composable(SoundFlowDestination.PROFILE.route) { ProfileScreen(playerViewModel) }
        }
    }
}
