package com.soundflow.mobile.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import com.soundflow.mobile.R
import com.soundflow.mobile.downloads.DownloadsViewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.soundflow.mobile.MainActivity
import com.soundflow.mobile.exportify.ExportifyViewModel
import com.soundflow.mobile.player.PlayerViewModel
import com.soundflow.mobile.playlists.Playlist
import com.soundflow.mobile.playlists.PlaylistType
import com.soundflow.mobile.playlists.PlaylistsViewModel
import com.soundflow.mobile.ui.components.NumberedTrackRow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
fun PlaylistsScreen(
    playerViewModel: PlayerViewModel,
    viewModel: PlaylistsViewModel,
    downloadsViewModel: DownloadsViewModel? = null,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()

    var selectedPlaylistId by remember { mutableStateOf<String?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showExportifyImportDialog by remember { mutableStateOf(false) }
    var playlistToRename by remember { mutableStateOf<Playlist?>(null) }

    val exportifyViewModel: ExportifyViewModel = viewModel()
    val activity = context as? MainActivity

    LaunchedEffect(Unit) {
        val pendingCsv = activity?.consumePendingCsvUri()
        if (pendingCsv != null) {
            showExportifyImportDialog = true
            exportifyViewModel.importCsv(
                contentResolver = context.contentResolver,
                uri = pendingCsv,
                rawCsvText = null,
                customPlaylistName = null,
                playlistsViewModel = viewModel,
            )
        }
    }

    val pendingOpenId by viewModel.pendingOpenPlaylistId.collectAsStateWithLifecycle()
    LaunchedEffect(pendingOpenId) {
        pendingOpenId?.let {
            selectedPlaylistId = it
            viewModel.consumeOpenPlaylistRequest()
        }
    }

    val sortedPlaylists = remember(playlists) {
        playlists.sortedBy { if (it.id == PlaylistsViewModel.FAVORITES_PLAYLIST_ID) 0 else 1 }
    }
    val selectedPlaylist = remember(playlists, selectedPlaylistId) {
        playlists.firstOrNull { it.id == selectedPlaylistId }
    }

    fun sharePlaylist(playlist: Playlist) {
        val shareUrl = viewModel.generateShareUrl(playlist)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, playlist.name)
            putExtra(Intent.EXTRA_TEXT, "${playlist.name} - SoundFlow: $shareUrl")
        }
        context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.playlist_share)))
    }

    if (selectedPlaylist != null) {
        val playerState by playerViewModel.state.collectAsStateWithLifecycle()
        PlaylistDetailScreen(
            playlist = selectedPlaylist,
            currentTrackId = playerState.currentTrack?.id,
            isCurrentPlaying = playerState.isPlaying,
            isBuffering = playerState.isBuffering,
            downloadsViewModel = downloadsViewModel,
            playlistsViewModel = viewModel,
            snackbarHostState = snackbarHostState,
            onBack = { selectedPlaylistId = null },
            onRename = { playlistToRename = selectedPlaylist },
            onShare = { sharePlaylist(selectedPlaylist) },
            onRemoveTrack = { trackId -> viewModel.removeTrack(selectedPlaylist.id, trackId) },
            onPlayTrack = { index -> playerViewModel.playQueue(selectedPlaylist.tracks, index) },
            onTogglePlayPause = { playerViewModel.togglePlayPause() },
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.playlists_title)) },
                actions = {
                    TextButton(onClick = { showExportifyImportDialog = true }) {
                        Text("Exportify", color = Color(0xFF1DB954))
                    }
                    IconButton(onClick = { showImportDialog = true }) {
                        Icon(Icons.Filled.Link, contentDescription = stringResource(R.string.playlist_import_url))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.playlist_create))
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (playlists.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.playlists_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                items(sortedPlaylists, key = { it.id }) { playlist ->
                    PlaylistRow(
                        playlist = playlist,
                        onClick = { selectedPlaylistId = playlist.id },
                        onRename = { playlistToRename = playlist },
                        onShare = { sharePlaylist(playlist) },
                        onDelete = { viewModel.deletePlaylist(playlist.id) },
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, type ->
                val created = viewModel.createPlaylist(name, type)
                if (created) showCreateDialog = false
                created
            },
        )
    }

    if (showImportDialog) {
        ImportPlaylistDialog(
            onDismiss = { showImportDialog = false },
            onImport = { urlOrData ->
                showImportDialog = false
                if (downloadsViewModel != null) {
                    viewModel.importPlaylistFromUrl(context, urlOrData, downloadsViewModel) { success, message ->
                        coroutineScope.launch { snackbarHostState.showSnackbar(message) }
                    }
                }
            },
        )
    }

    if (showExportifyImportDialog) {
        ExportifyImportDialog(
            playlistsViewModel = viewModel,
            onDismiss = { showExportifyImportDialog = false },
            exportifyViewModel = exportifyViewModel,
        )
    }

    playlistToRename?.let { playlist ->
        RenamePlaylistDialog(
            currentName = playlist.name,
            onDismiss = { playlistToRename = null },
            onRename = { newName ->
                val renamed = viewModel.renamePlaylist(playlist.id, newName)
                if (renamed) playlistToRename = null
                renamed
            },
        )
    }
}

@UnstableApi
@Composable
private fun PlaylistRow(
    playlist: Playlist,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    val isFavorites = playlist.id == PlaylistsViewModel.FAVORITES_PLAYLIST_ID
    val displayName = if (isFavorites) stringResource(R.string.playlist_favorites) else playlist.name
    val iconBackground = if (playlist.type == PlaylistType.OFFLINE) {
        Color(0xFF9C6ADE).copy(alpha = 0.25f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val iconTint = if (playlist.type == PlaylistType.OFFLINE) Color(0xFF9C6ADE) else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(44.dp).background(iconBackground, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.QueueMusic, contentDescription = null, tint = iconTint)
        }
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(displayName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = "${playlist.tracks.size} morceau(x) • " +
                    if (playlist.type == PlaylistType.OFFLINE) {
                        stringResource(R.string.playlist_type_offline)
                    } else {
                        stringResource(R.string.playlist_type_online)
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        IconButton(onClick = onShare) {
            Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.playlist_share))
        }

        if (playlist.isDeletable) {
            IconButton(onClick = onRename) {
                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.playlist_rename))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = null)
            }
        }
    }
}

@Composable
private fun CreatePlaylistDialog(onDismiss: () -> Unit, onCreate: (String, PlaylistType) -> Boolean) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(PlaylistType.ONLINE) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val duplicateErrorText = stringResource(R.string.playlist_name_already_exists)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.playlist_create)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        errorMessage = null
                    },
                    label = { Text(stringResource(R.string.playlist_name_label)) },
                    singleLine = true,
                    isError = errorMessage != null,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (errorMessage != null) {
                    Text(
                        text = errorMessage.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp).clickable { type = PlaylistType.ONLINE },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = type == PlaylistType.ONLINE, onClick = { type = PlaylistType.ONLINE })
                    Text(stringResource(R.string.playlist_type_online))
                }
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { type = PlaylistType.OFFLINE },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = type == PlaylistType.OFFLINE, onClick = { type = PlaylistType.OFFLINE })
                    Text(stringResource(R.string.playlist_type_offline))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) {
                    val created = onCreate(name.trim(), type)
                    if (!created) errorMessage = duplicateErrorText
                }
            }) {
                Text(stringResource(R.string.playlist_create_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.playlist_cancel)) }
        },
    )
}

@Composable
private fun RenamePlaylistDialog(currentName: String, onDismiss: () -> Unit, onRename: (String) -> Boolean) {
    var name by remember { mutableStateOf(currentName) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val duplicateErrorText = stringResource(R.string.playlist_name_already_exists)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.playlist_rename)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        errorMessage = null
                    },
                    label = { Text(stringResource(R.string.playlist_name_label)) },
                    singleLine = true,
                    isError = errorMessage != null,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (errorMessage != null) {
                    Text(
                        text = errorMessage.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) {
                    val renamed = onRename(name.trim())
                    if (!renamed) errorMessage = duplicateErrorText
                }
            }) {
                Text(stringResource(R.string.playlist_create_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.playlist_cancel)) }
        },
    )
}

@Composable
private fun ImportPlaylistDialog(onDismiss: () -> Unit, onImport: (String) -> Unit) {
    var urlOrData by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.playlist_import_url)) },
        text = {
            OutlinedTextField(
                value = urlOrData,
                onValueChange = { urlOrData = it },
                label = { Text(stringResource(R.string.playlist_import_hint)) },
                singleLine = false,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                if (urlOrData.isNotBlank()) {
                    onImport(urlOrData)
                }
            }) {
                Text(stringResource(R.string.import_button_label))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.playlist_cancel)) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
private fun PlaylistDetailScreen(
    playlist: Playlist,
    currentTrackId: String?,
    isCurrentPlaying: Boolean,
    isBuffering: Boolean,
    downloadsViewModel: DownloadsViewModel?,
    playlistsViewModel: PlaylistsViewModel,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit,
    onRemoveTrack: (String) -> Unit,
    onPlayTrack: (Int) -> Unit,
    onTogglePlayPause: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val numbered = playlist.tracks.mapIndexed { index, track -> index to track }
    val visible = if (query.isBlank()) {
        numbered
    } else {
        numbered.filter { (index, track) ->
            (index + 1).toString() == query.trim() ||
                track.title.contains(query, ignoreCase = true) ||
                track.artist.contains(query, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (playlist.type == PlaylistType.ONLINE && downloadsViewModel != null) {
                        IconButton(onClick = {
                            playlistsViewModel.convertToOffline(playlist.id, context, downloadsViewModel)
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Playlist passée en mode hors ligne, téléchargement en cours...")
                            }
                        }) {
                            Icon(Icons.Filled.Download, contentDescription = "Rendre hors ligne")
                        }
                    }
                    IconButton(onClick = onShare) {
                        Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.playlist_share))
                    }
                    if (playlist.isDeletable) {
                        IconButton(onClick = onRename) {
                            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.playlist_rename))
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (playlist.tracks.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.playlist_empty_tracks),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.search_hint)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 12.dp)) {
                    itemsIndexed(visible, key = { _, (_, track) -> track.id }) { _, (index, track) ->
                        val isCurrentTrack = track.id == currentTrackId
                        NumberedTrackRow(
                            number = index + 1,
                            title = track.title,
                            artist = track.artist,
                            artworkUrl = track.artworkUrl,
                            isCurrentTrack = isCurrentTrack,
                            isPlaying = isCurrentTrack && isCurrentPlaying,
                            isBuffering = isCurrentTrack && isBuffering,
                            onPlayClick = { if (isCurrentTrack) onTogglePlayPause() else onPlayTrack(index) },
                            trailingContent = {
                                IconButton(onClick = { onRemoveTrack(track.id) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = null)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
private fun ExportifyImportDialog(
    playlistsViewModel: PlaylistsViewModel,
    onDismiss: () -> Unit,
    exportifyViewModel: ExportifyViewModel = viewModel(),
) {
    val context = LocalContext.current
    val state by exportifyViewModel.state.collectAsStateWithLifecycle()
    var spotifyUrlInput by remember { mutableStateOf("") }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            exportifyViewModel.importCsv(
                contentResolver = context.contentResolver,
                uri = uri,
                rawCsvText = null,
                customPlaylistName = null,
                playlistsViewModel = playlistsViewModel,
            )
        }
    }

    Dialog(
        onDismissRequest = {
            if (!state.isImporting) {
                exportifyViewModel.clearMessages()
                onDismiss()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
            ) {
                // Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (state.isImporting) "Importation Exportify en cours..." else "Importation Exportify (Spotify)",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        if (!state.isImporting) {
                            Text(
                                text = "Importez vos playlists Spotify en fichier CSV",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (!state.isImporting) {
                        IconButton(onClick = {
                            exportifyViewModel.clearMessages()
                            onDismiss()
                        }) {
                            Icon(Icons.Filled.Close, contentDescription = "Fermer")
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                if (state.isImporting) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = "Recherche des morceaux sur YouTube...",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            if (state.currentImportingPlaylistName.isNotBlank()) {
                                Text(
                                    text = "Playlist : ${state.currentImportingPlaylistName}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = state.currentImportingTrackName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(20.dp))
                            val progress = if (state.importTotal > 0) state.importProgress.toFloat() / state.importTotal else 0f
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "${state.importProgress} / ${state.importTotal} morceau(x)",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        // Card Étape 1
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Étape 1 : Obtenir le fichier CSV",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Ouvrez Exportify dans votre navigateur, connectez-vous à Spotify et téléchargez le fichier CSV de votre playlist.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        runCatching {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://exportify.net/"))
                                            context.startActivity(intent)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DB954)),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                        contentDescription = null,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                    Text("Ouvrir Exportify (navigateur)", color = Color.White)
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Card Étape 2
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Étape 2 : Importer le fichier CSV",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Sélectionnez le fichier CSV que vous venez de télécharger sur votre appareil.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        filePickerLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "application/csv", "*/*"))
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Download,
                                        contentDescription = null,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                    Text("Sélectionner le fichier CSV")
                                }
                            }
                        }

                        Spacer(Modifier.height(20.dp))

                        // Section Alternative : Lien Spotify direct
                        Text(
                            text = "Alternative : Import par lien Spotify direct",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = spotifyUrlInput,
                            onValueChange = { spotifyUrlInput = it },
                            label = { Text("Lien Spotify (open.spotify.com/playlist/...)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (spotifyUrlInput.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    exportifyViewModel.importSpotifyUrl(
                                        url = spotifyUrlInput.trim(),
                                        playlistsViewModel = playlistsViewModel,
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DB954)),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Importer la playlist par lien", color = Color.White)
                            }
                        }
                    }
                }

                state.errorMessage?.let { msg ->
                    Text(
                        text = msg,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                state.importSuccessMessage?.let { msg ->
                    Text(
                        text = msg,
                        color = Color(0xFF1DB954),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}
