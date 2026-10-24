package com.soundflow.mobile.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import com.soundflow.core.extraction.DownloadStreams
import com.soundflow.core.extraction.SearchResult
import com.soundflow.mobile.R
import com.soundflow.mobile.downloads.DownloadsViewModel
import com.soundflow.mobile.downloads.performDownload
import com.soundflow.mobile.home.ArtistSuggestion
import com.soundflow.mobile.home.HomeViewModel
import com.soundflow.mobile.player.PlayerViewModel
import com.soundflow.mobile.playlists.PlaylistType
import com.soundflow.mobile.playlists.PlaylistsViewModel
import com.soundflow.mobile.ui.components.PlaylistPickerDialog
import kotlinx.coroutines.launch

// Taille uniforme des cartes sur toutes les rangées, pour une cohérence visuelle façon Spotify.
private val CardWidth = 130.dp

@UnstableApi
@Composable
fun HomeScreen(
    playerViewModel: PlayerViewModel,
    playlistsViewModel: PlaylistsViewModel,
    downloadsViewModel: DownloadsViewModel,
    onArtistClick: (String) -> Unit,
    onOpenPlaylists: () -> Unit,
) {
    val viewModel: HomeViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playerState by playerViewModel.state.collectAsStateWithLifecycle()
    val playlists by playlistsViewModel.playlists.collectAsStateWithLifecycle()
    val downloads by downloadsViewModel.downloads.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var loadingPlayId by remember { mutableStateOf<String?>(null) }
    var pendingDownload by remember { mutableStateOf<SearchResult?>(null) }
    var pendingAdd by remember { mutableStateOf<SearchResult?>(null) }
    var pendingDelete by remember { mutableStateOf<SearchResult?>(null) }

    LaunchedEffect(playerState.errorMessage) {
        playerState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            playerViewModel.clearError()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.errorMessage != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Impossible de charger les tendances : ${state.errorMessage}",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(24.dp),
                    )
                }
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        SectionTitle(stringResource(R.string.home_section_trending_hits))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(state.items, key = { it.id }) { result ->
                                val isCurrentTrack = playerState.currentTrack?.id == result.id
                                val isDownloaded = downloads.any { it.id == result.id && it.localUri != null }
                                val isDownloadInProgress = downloads.any { it.id == result.id && it.localUri == null }
                                TrendingCard(
                                    result = result,
                                    isCurrentTrack = isCurrentTrack,
                                    isPlaying = isCurrentTrack && playerState.isPlaying,
                                    isLoadingPlay = loadingPlayId == result.id,
                                    isDownloaded = isDownloaded,
                                    isDownloadInProgress = isDownloadInProgress,
                                    onPlayClick = {
                                        if (isCurrentTrack) {
                                            playerViewModel.togglePlayPause()
                                        } else {
                                            loadingPlayId = result.id
                                            coroutineScope.launch {
                                                try {
                                                    val track = viewModel.resolveTrack(result)
                                                    if (track != null) playerViewModel.playTrack(track)
                                                    else snackbarHostState.showSnackbar(context.getString(R.string.no_stream_found))
                                                } catch (e: Exception) {
                                                    snackbarHostState.showSnackbar(e.message ?: e.javaClass.simpleName)
                                                } finally {
                                                    loadingPlayId = null
                                                }
                                            }
                                        }
                                    },
                                    onDownloadClick = { pendingDownload = result },
                                    onDeleteDownloadClick = { pendingDelete = result },
                                    onAddClick = { pendingAdd = result },
                                )
                            }
                        }
                    }

                    if (state.artists.isNotEmpty()) {
                        item {
                            SectionTitle(stringResource(R.string.home_section_trending_artists))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(state.artists, key = { it.name }) { artist ->
                                    ArtistCard(artist = artist, onClick = { onArtistClick(artist.name) })
                                }
                            }
                        }
                    }

                    if (playlists.isNotEmpty()) {
                        item {
                            SectionTitle(stringResource(R.string.home_section_your_playlists))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(playlists, key = { it.id }) { playlist ->
                                    val displayName = if (playlist.id == PlaylistsViewModel.FAVORITES_PLAYLIST_ID) {
                                        stringResource(R.string.playlist_favorites)
                                    } else {
                                        playlist.name
                                    }
                                    PlaylistCard(
                                        name = displayName,
                                        artworkUrl = playlist.tracks.firstOrNull()?.artworkUrl,
                                        onClick = {
                                            playlistsViewModel.requestOpenPlaylist(playlist.id)
                                            onOpenPlaylists()
                                        },
                                    )
                                }
                            }
                        }
                    }

                    item { androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 16.dp)) }
                }
            }
        }
    }

    pendingDownload?.let { result ->
        AlertDialog(
            onDismissRequest = { pendingDownload = null },
            title = { Text(stringResource(R.string.download_choice_title)) },
            text = {},
            confirmButton = {
                TextButton(onClick = {
                    pendingDownload = null
                    coroutineScope.launch {
                        downloadFromHome(context, viewModel, downloadsViewModel, playlistsViewModel, result, false, snackbarHostState)
                    }
                }) { Text(stringResource(R.string.download_audio)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingDownload = null
                    coroutineScope.launch {
                        downloadFromHome(context, viewModel, downloadsViewModel, playlistsViewModel, result, true, snackbarHostState)
                    }
                }) { Text(stringResource(R.string.download_video)) }
            },
        )
    }

    pendingAdd?.let { result ->
        val isDownloaded = downloads.any { it.id == result.id && it.localUri != null }
        val eligiblePlaylists = playlists.filter { isDownloaded || it.type == PlaylistType.ONLINE }
        PlaylistPickerDialog(
            playlists = eligiblePlaylists,
            isDownloaded = isDownloaded,
            onDismiss = { pendingAdd = null },
            onPlaylistChosen = { playlist ->
                pendingAdd = null
                coroutineScope.launch {
                    val localRecord = downloads.firstOrNull { it.id == result.id && it.localUri != null }
                    val track = if (localRecord != null) {
                        com.soundflow.core.player.Track(
                            id = result.id,
                            title = result.title,
                            artist = result.uploaderName,
                            mediaUrl = localRecord.localUri!!,
                            artworkUrl = result.thumbnailUrl,
                            hasVideo = !localRecord.isAudio,
                            isDownloaded = true,
                        )
                    } else {
                        viewModel.resolveTrack(result)?.copy(isDownloaded = isDownloaded)
                    }
                    if (track != null) {
                        val added = playlistsViewModel.addTrack(playlist.id, track)
                        snackbarHostState.showSnackbar(
                            context.getString(if (added) R.string.playlist_added else R.string.playlist_add_blocked),
                        )
                    } else {
                        snackbarHostState.showSnackbar(context.getString(R.string.no_stream_found))
                    }
                }
            },
        )
    }

    pendingDelete?.let { result ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.delete_confirm_title)) },
            text = { Text(stringResource(R.string.delete_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    downloadsViewModel.deleteDownload(result.id, playlistsViewModel)
                    coroutineScope.launch { snackbarHostState.showSnackbar(context.getString(R.string.download_deleted)) }
                }) {
                    Text(stringResource(R.string.delete_action), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.cancel_action)) }
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@androidx.annotation.OptIn(UnstableApi::class)
private suspend fun downloadFromHome(
    context: Context,
    viewModel: HomeViewModel,
    downloadsViewModel: DownloadsViewModel,
    playlistsViewModel: PlaylistsViewModel,
    result: SearchResult,
    downloadVideo: Boolean,
    snackbarHostState: SnackbarHostState,
) {
    try {
        val streams: DownloadStreams = viewModel.getDownloadStreams(result)
        performDownload(
            context = context,
            streams = streams,
            id = result.id,
            title = result.title,
            artist = result.uploaderName,
            artworkUrl = result.thumbnailUrl,
            downloadVideo = downloadVideo,
            downloadsViewModel = downloadsViewModel,
            playlistsViewModel = playlistsViewModel,
            onMessage = { resId -> snackbarHostState.showSnackbar(context.getString(resId)) },
        )
    } catch (e: Exception) {
        snackbarHostState.showSnackbar("${context.getString(R.string.playback_error_prefix)}${e.message ?: e.javaClass.simpleName}")
    }
}

@Composable
private fun TrendingCard(
    result: SearchResult,
    isCurrentTrack: Boolean,
    isPlaying: Boolean,
    isLoadingPlay: Boolean,
    isDownloaded: Boolean,
    isDownloadInProgress: Boolean,
    onPlayClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onDeleteDownloadClick: () -> Unit,
    onAddClick: () -> Unit,
) {
    Column(modifier = Modifier.width(CardWidth)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(enabled = !isLoadingPlay, onClick = onPlayClick),
            contentAlignment = Alignment.Center,
        ) {
            if (result.thumbnailUrl != null) {
                AsyncImage(
                    model = result.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    Icons.Filled.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isLoadingPlay || isCurrentTrack) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        isLoadingPlay -> CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                        isPlaying -> Icon(Icons.Filled.Pause, contentDescription = null, modifier = Modifier.size(32.dp), tint = Color.White)
                        else -> Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(32.dp), tint = Color.White)
                    }
                }
            }
        }

        Text(
            result.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            result.uploaderName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isDownloadInProgress) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp).padding(8.dp))
            } else {
                IconButton(onClick = if (isDownloaded) onDeleteDownloadClick else onDownloadClick, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = if (isDownloaded) Icons.Filled.Delete else Icons.Filled.Download,
                        contentDescription = null,
                        tint = if (isDownloaded) MaterialTheme.colorScheme.error else LocalContentColor.current,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            IconButton(onClick = onAddClick, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun ArtistCard(artist: ArtistSuggestion, onClick: () -> Unit) {
    Column(
        modifier = Modifier.width(CardWidth).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(CardWidth)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (artist.thumbnailUrl != null) {
                AsyncImage(
                    model = artist.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            artist.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp).fillMaxWidth(),
        )
    }
}

@Composable
private fun PlaylistCard(name: String, artworkUrl: String?, onClick: () -> Unit) {
    Column(modifier = Modifier.width(CardWidth).clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (artworkUrl != null) {
                AsyncImage(
                    model = artworkUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    Icons.Filled.QueueMusic,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
