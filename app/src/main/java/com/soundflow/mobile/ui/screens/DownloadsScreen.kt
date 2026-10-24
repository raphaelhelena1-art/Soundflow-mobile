package com.soundflow.mobile.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.soundflow.core.player.Track
import com.soundflow.mobile.R
import com.soundflow.mobile.downloads.DownloadRecord
import com.soundflow.mobile.downloads.DownloadsViewModel
import com.soundflow.mobile.player.PlayerViewModel
import com.soundflow.mobile.playlists.PlaylistsViewModel
import com.soundflow.mobile.ui.components.NumberedTrackRow
import com.soundflow.mobile.ui.components.PlaylistPickerDialog
import kotlinx.coroutines.launch

/** Onglet Téléchargements — même présentation/fonctionnalités qu'une playlist :
 * numéro de position, image + play/pause, recherche par numéro ou par texte, et import local. */
@UnstableApi
@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel,
    playlistsViewModel: PlaylistsViewModel,
    playerViewModel: PlayerViewModel,
) {
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val playlists by playlistsViewModel.playlists.collectAsStateWithLifecycle()
    val playerState by playerViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingAdd by remember { mutableStateOf<DownloadRecord?>(null) }
    var pendingDelete by remember { mutableStateOf<DownloadRecord?>(null) }
    var query by remember { mutableStateOf("") }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importLocalFiles(uris, playlistsViewModel)
            coroutineScope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.import_success))
            }
        }
    }

    // Les numéros affichés reflètent toujours la position dans la liste complète (non filtrée).
    val numbered = downloads.mapIndexed { index, record -> (index + 1) to record }
    val visible = if (query.isBlank()) {
        numbered
    } else {
        numbered.filter { (number, record) ->
            number.toString() == query.trim() ||
                record.title.contains(query, ignoreCase = true) ||
                record.artist.contains(query, ignoreCase = true)
        }
    }

    fun trackFor(record: DownloadRecord): Track? {
        val uri = record.localUri ?: return null
        return Track(
            id = record.id,
            title = record.title,
            artist = record.artist,
            mediaUrl = uri,
            artworkUrl = record.artworkUrl,
            hasVideo = !record.isAudio,
            isDownloaded = true,
        )
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.search_hint)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )

                IconButton(
                    onClick = { importLauncher.launch(arrayOf("audio/*", "video/*")) },
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.UploadFile,
                        contentDescription = stringResource(R.string.import_button_label),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (downloads.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.downloads_placeholder),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 12.dp)) {
                    itemsIndexed(visible, key = { index, (_, record) -> "${record.id}_${record.isAudio}_${record.timestampMs}_$index" }) { _, (number, record) ->
                        val isCurrentTrack = playerState.currentTrack?.id == record.id
                        NumberedTrackRow(
                            number = number,
                            title = record.title,
                            artist = record.artist,
                            artworkUrl = record.artworkUrl,
                            isCurrentTrack = isCurrentTrack,
                            isPlaying = isCurrentTrack && playerState.isPlaying,
                            isBuffering = isCurrentTrack && playerState.isBuffering,
                            onPlayClick = {
                                if (isCurrentTrack) {
                                    playerViewModel.togglePlayPause()
                                } else if (record.localUri != null) {
                                    val queueTracks = downloads.mapNotNull(::trackFor)
                                    val startIndex = queueTracks.indexOfFirst { it.id == record.id }
                                    if (startIndex >= 0) playerViewModel.playQueue(queueTracks, startIndex)
                                }
                            },
                            trailingContent = {
                                if (record.localUri == null) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp).padding(end = 8.dp))
                                } else {
                                    IconButton(onClick = { pendingAdd = record }) {
                                        Icon(Icons.Filled.Add, contentDescription = "Ajouter à une playlist")
                                    }
                                }
                                IconButton(onClick = { pendingDelete = record }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Supprimer", tint = MaterialTheme.colorScheme.error)
                                }
                            },
                        )
                    }
                }
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).padding(16.dp)) {
            SnackbarHost(snackbarHostState)
        }
    }

    pendingAdd?.let { record ->
        PlaylistPickerDialog(
            playlists = playlists,
            isDownloaded = true,
            onDismiss = { pendingAdd = null },
            onPlaylistChosen = { playlist ->
                pendingAdd = null
                val track = trackFor(record)
                coroutineScope.launch {
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

    pendingDelete?.let { record ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.delete_confirm_title)) },
            text = { Text(stringResource(R.string.delete_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    viewModel.deleteDownload(record.id, playlistsViewModel)
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
