package com.soundflow.mobile.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import com.soundflow.core.extraction.DownloadStreams
import com.soundflow.core.extraction.SearchResult
import com.soundflow.mobile.R
import com.soundflow.mobile.downloads.DownloadsViewModel
import com.soundflow.mobile.downloads.performDownload
import com.soundflow.mobile.player.PlayerViewModel
import com.soundflow.mobile.playlists.PlaylistType
import com.soundflow.mobile.ui.components.PlaylistPickerDialog
import com.soundflow.mobile.playlists.PlaylistsViewModel
import com.soundflow.mobile.search.SearchViewModel
import kotlinx.coroutines.launch

@UnstableApi
@Composable
fun SearchScreen(
    playerViewModel: PlayerViewModel,
    playlistsViewModel: PlaylistsViewModel,
    downloadsViewModel: DownloadsViewModel,
    viewModel: SearchViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playerState by playerViewModel.state.collectAsStateWithLifecycle()
    val playlists by playlistsViewModel.playlists.collectAsStateWithLifecycle()
    val downloadedIds by playlistsViewModel.downloadedTrackIds.collectAsStateWithLifecycle()
    val downloads by downloadsViewModel.downloads.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    var loadingPlayId by remember { mutableStateOf<String?>(null) }
    var loadingFavoriteId by remember { mutableStateOf<String?>(null) }
    var loadingAddId by remember { mutableStateOf<String?>(null) }
    var pendingDownload by remember { mutableStateOf<SearchResult?>(null) }
    var pendingPlaylistPicker by remember { mutableStateOf<SearchResult?>(null) }
    var pendingDelete by remember { mutableStateOf<SearchResult?>(null) }

    val playbackErrorPrefix = stringResource(R.string.playback_error_prefix)

    LaunchedEffect(playerState.errorMessage) {
        playerState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar("$playbackErrorPrefix$message")
            playerViewModel.clearError()
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo }.collect { layoutInfo ->
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            if (totalItems > 0 && lastVisibleIndex >= totalItems - 3) {
                viewModel.loadMoreResults()
            }
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { scaffoldPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(scaffoldPadding).padding(16.dp)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                label = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        viewModel.onSearchSubmitted()
                        keyboardController?.hide()
                    },
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            // Historique de recherche : visible tant que le champ est vide, disparaît dès qu'on tape.
            if (state.query.isBlank() && state.history.isNotEmpty()) {
                Column {
                    state.history.forEach { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.onHistoryEntrySelected(entry) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(onClick = { viewModel.removeHistoryEntry(entry) }, modifier = Modifier.size(28.dp)) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                            Icon(
                                Icons.Filled.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp).padding(start = 4.dp),
                            )
                            Text(
                                text = entry,
                                modifier = Modifier.padding(start = 12.dp),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                    HorizontalDivider()
                }
            }

            if (state.suggestions.isNotEmpty()) {
                Column {
                    state.suggestions.take(6).forEach { suggestion ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.onSuggestionSelected(suggestion) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = suggestion,
                                modifier = Modifier.padding(start = 12.dp),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                    HorizontalDivider()
                }
            }

            Spacer(Modifier.height(8.dp))

            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.errorMessage != null -> Text(
                    text = state.errorMessage.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                )
                else -> LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(state.results, key = { it.id }) { result ->
                        val isCurrentTrack = playerState.currentTrack?.id == result.id
                        SearchResultRow(
                            result = result,
                            isCurrentTrack = isCurrentTrack,
                            isPlaying = isCurrentTrack && playerState.isPlaying,
                            isLoadingPlay = loadingPlayId == result.id,
                            isFavorite = playlistsViewModel.isFavorite(result.id),
                            isLoadingFavorite = loadingFavoriteId == result.id,
                            isLoadingAdd = loadingAddId == result.id,
                            isDownloaded = downloads.any { it.id == result.id && it.localUri != null },
                            isDownloadInProgress = downloads.any { it.id == result.id && it.localUri == null },
                            onPlayClick = {
                                if (isCurrentTrack) {
                                    playerViewModel.togglePlayPause()
                                } else {
                                    loadingPlayId = result.id
                                    coroutineScope.launch {
                                        try {
                                            val track = viewModel.resolveTrack(result)
                                            if (track != null) {
                                                playerViewModel.playTrack(track)
                                            } else {
                                                snackbarHostState.showSnackbar(context.getString(R.string.no_stream_found))
                                            }
                                        } catch (e: Exception) {
                                            snackbarHostState.showSnackbar(
                                                "${context.getString(R.string.playback_error_prefix)}${e.message ?: e.javaClass.simpleName}",
                                            )
                                        } finally {
                                            loadingPlayId = null
                                        }
                                    }
                                }
                            },
                            onFavoriteClick = {
                                loadingFavoriteId = result.id
                                coroutineScope.launch {
                                    try {
                                        val track = viewModel.resolveTrack(result)
                                        if (track != null) {
                                            playlistsViewModel.toggleFavorite(
                                                track.copy(isDownloaded = result.id in downloadedIds),
                                            )
                                        } else {
                                            snackbarHostState.showSnackbar(context.getString(R.string.no_stream_found))
                                        }
                                    } catch (e: Exception) {
                                        snackbarHostState.showSnackbar(e.message ?: e.javaClass.simpleName)
                                    } finally {
                                        loadingFavoriteId = null
                                    }
                                }
                            },
                            onDownloadClick = { pendingDownload = result },
                            onDeleteDownloadClick = { pendingDelete = result },
                            onAddClick = { pendingPlaylistPicker = result },
                        )
                    }
                    if (state.isLoadingMore) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDownload?.let { result ->
        DownloadChoiceDialog(
            onDismiss = { pendingDownload = null },
            onChoiceAudio = {
                pendingDownload = null
                coroutineScope.launch {
                    startDownload(context, viewModel, playlistsViewModel, downloadsViewModel, result, downloadVideo = false, snackbarHostState)
                }
            },
            onChoiceVideo = {
                pendingDownload = null
                coroutineScope.launch {
                    startDownload(context, viewModel, playlistsViewModel, downloadsViewModel, result, downloadVideo = true, snackbarHostState)
                }
            },
        )
    }

    pendingPlaylistPicker?.let { result ->
        val isDownloaded = result.id in downloadedIds
        val eligiblePlaylists = playlists.filter { isDownloaded || it.type == PlaylistType.ONLINE }
        PlaylistPickerDialog(
            playlists = eligiblePlaylists,
            isDownloaded = isDownloaded,
            onDismiss = { pendingPlaylistPicker = null },
            onPlaylistChosen = { playlist ->
                pendingPlaylistPicker = null
                loadingAddId = result.id
                coroutineScope.launch {
                    try {
                        // Préfère le fichier local déjà téléchargé (vraiment utilisable hors-ligne,
                        // sans dépendre d'une URL de streaming qui finit par expirer).
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
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar(e.message ?: e.javaClass.simpleName)
                    } finally {
                        loadingAddId = null
                    }
                }
            },
        )
    }

    pendingDelete?.let { result ->
        DeleteConfirmDialog(
            onDismiss = { pendingDelete = null },
            onConfirm = {
                pendingDelete = null
                downloadsViewModel.deleteDownload(result.id, playlistsViewModel)
                coroutineScope.launch { snackbarHostState.showSnackbar(context.getString(R.string.download_deleted)) }
            },
        )
    }
}

@Composable
private fun DeleteConfirmDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_confirm_title)) },
        text = { Text(stringResource(R.string.delete_confirm_text)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.delete_action), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel_action)) }
        },
    )
}

@Composable
private fun DownloadChoiceDialog(onDismiss: () -> Unit, onChoiceAudio: () -> Unit, onChoiceVideo: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.download_choice_title)) },
        text = {},
        confirmButton = {
            TextButton(onClick = onChoiceAudio) { Text(stringResource(R.string.download_audio)) }
        },
        dismissButton = {
            TextButton(onClick = onChoiceVideo) { Text(stringResource(R.string.download_video)) }
        },
    )
}

private suspend fun startDownload(
    context: Context,
    viewModel: SearchViewModel,
    playlistsViewModel: PlaylistsViewModel,
    downloadsViewModel: DownloadsViewModel,
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
private fun SearchResultRow(
    result: SearchResult,
    isCurrentTrack: Boolean,
    isPlaying: Boolean,
    isLoadingPlay: Boolean,
    isFavorite: Boolean,
    isLoadingFavorite: Boolean,
    isLoadingAdd: Boolean,
    isDownloaded: Boolean,
    isDownloadInProgress: Boolean,
    onPlayClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onDeleteDownloadClick: () -> Unit,
    onAddClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isLoadingPlay, onClick = onPlayClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
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
                Icon(Icons.Filled.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (isLoadingPlay || isCurrentTrack) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        isLoadingPlay -> CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.White)
                        isPlaying -> Icon(Icons.Filled.Pause, contentDescription = "Pause", tint = Color.White)
                        else -> Icon(Icons.Filled.PlayArrow, contentDescription = "Lecture", tint = Color.White)
                    }
                }
            }
        }

        Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            if (isLoadingFavorite) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp))
            } else {
                IconButton(onClick = onFavoriteClick) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = null,
                        tint = if (isFavorite) Color(0xFF1DB954) else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
            Text(result.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "${result.uploaderName} • ${formatDuration(result.durationSeconds)}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (isDownloadInProgress) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp).padding(8.dp))
        } else {
            IconButton(onClick = if (isDownloaded) onDeleteDownloadClick else onDownloadClick) {
                Icon(
                    imageVector = if (isDownloaded) Icons.Filled.Delete else Icons.Filled.Download,
                    contentDescription = if (isDownloaded) "Supprimer le téléchargement" else "Télécharger",
                    tint = if (isDownloaded) MaterialTheme.colorScheme.error else LocalContentColor.current,
                )
            }
        }

        Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            if (isLoadingAdd) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp))
            } else {
                IconButton(onClick = onAddClick) {
                    Icon(Icons.Filled.Add, contentDescription = "Ajouter à une playlist")
                }
            }
        }
    }
}

private fun formatDuration(totalSeconds: Long): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
