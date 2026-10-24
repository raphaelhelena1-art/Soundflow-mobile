package com.soundflow.mobile.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import com.soundflow.core.extraction.YouTubeRepository
import com.soundflow.core.lyrics.LyricsRepository
import com.soundflow.core.lyrics.LyricsResult
import com.soundflow.core.lyrics.parseSyncedLyrics
import com.soundflow.core.player.PlaybackMode
import com.soundflow.core.player.Track
import com.soundflow.mobile.R
import com.soundflow.mobile.downloads.DownloadsViewModel
import com.soundflow.mobile.downloads.performDownload
import com.soundflow.mobile.player.PlayerViewModel
import com.soundflow.mobile.playlists.PlaylistType
import com.soundflow.mobile.playlists.PlaylistsViewModel
import com.soundflow.mobile.ui.components.PlaylistPickerDialog
import com.soundflow.mobile.ui.components.VideoSurface
import kotlinx.coroutines.launch

private val AccentGreen = Color(0xFF1DB954)

@UnstableApi
@Composable
fun NowPlayingScreen(
    playerViewModel: PlayerViewModel,
    playlistsViewModel: PlaylistsViewModel,
    downloadsViewModel: DownloadsViewModel,
    onDismiss: () -> Unit,
) {
    val playerState by playerViewModel.state.collectAsStateWithLifecycle()
    val player by playerViewModel.player.collectAsStateWithLifecycle()
    val playbackMode by playerViewModel.playbackMode.collectAsStateWithLifecycle()
    val playlists by playlistsViewModel.playlists.collectAsStateWithLifecycle()
    val downloadedIds by playlistsViewModel.downloadedTrackIds.collectAsStateWithLifecycle()

    val track = playerState.currentTrack ?: run { onDismiss(); return }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var isAdding by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null)
                    }

                    Spacer(Modifier.height(8.dp))

                    // Grande image (ou vidéo si disponible), en haut.
                    // Réduite quand les paroles sont affichées : sinon il ne reste presque plus
                    // de place pour le contenu en dessous.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (showLyrics) Modifier.height(120.dp) else Modifier.aspectRatio(1f))
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        when {
                            track.hasVideo && player != null ->
                                VideoSurface(player = player, useController = false, modifier = Modifier.fillMaxSize())
                            track.artworkUrl != null -> key(track.id) {
                                AsyncImage(
                                    model = track.artworkUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            else -> Icon(
                                Icons.Filled.MusicNote,
                                contentDescription = null,
                                modifier = Modifier.size(96.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // Titre/auteur à gauche, téléchargement + ajout à droite, au même niveau.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(track.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                track.artist,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (isDownloading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp).padding(end = 8.dp))
                        } else {
                            IconButton(onClick = { showDownloadDialog = true }) {
                                Icon(Icons.Filled.Download, contentDescription = null)
                            }
                        }
                        if (isAdding) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        } else {
                            IconButton(onClick = { showAddDialog = true }) {
                                Icon(Icons.Filled.Add, contentDescription = null)
                            }
                        }
                        // Bouton dédié pour les paroles — plus fiable qu'un tap sur l'image,
                        // qui pouvait sembler ne pas répondre au premier essai.
                        IconButton(onClick = { showLyrics = !showLyrics }) {
                            Icon(
                                Icons.Filled.Lyrics,
                                contentDescription = "Paroles",
                                tint = if (showLyrics) AccentGreen else LocalContentColor.current,
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Barre de lecture avec les temps.
                    var isDragging by remember { mutableStateOf(false) }
                    var dragPositionMs by remember { mutableStateOf(0L) }
                    val displayedPositionMs = if (isDragging) dragPositionMs else playerState.positionMs
                    Slider(
                        value = displayedPositionMs.toFloat(),
                        onValueChange = {
                            isDragging = true
                            dragPositionMs = it.toLong()
                        },
                        onValueChangeFinished = {
                            playerViewModel.seekTo(dragPositionMs)
                            isDragging = false
                        },
                        valueRange = 0f..playerState.durationMs.coerceAtLeast(1L).toFloat(),
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatTime(displayedPositionMs), style = MaterialTheme.typography.bodySmall)
                        Text(formatTime(playerState.durationMs), style = MaterialTheme.typography.bodySmall)
                    }

                    Spacer(Modifier.height(12.dp))

                    // Modes à gauche (aléatoire, répétition), transport (précédent/lecture/suivant) au centre-droite.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        IconButton(onClick = playerViewModel::toggleShuffle) {
                            Icon(
                                Icons.Filled.Shuffle,
                                contentDescription = null,
                                tint = if (playbackMode == PlaybackMode.SHUFFLE) AccentGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = playerViewModel::skipToPrevious) {
                                Icon(Icons.Filled.SkipPrevious, contentDescription = null, modifier = Modifier.size(32.dp))
                            }
                            if (playerState.isBuffering) {
                                CircularProgressIndicator(modifier = Modifier.size(40.dp).padding(8.dp))
                            } else {
                                IconButton(onClick = playerViewModel::togglePlayPause, modifier = Modifier.size(56.dp)) {
                                    Icon(
                                        imageVector = if (playerState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(40.dp),
                                    )
                                }
                            }
                            IconButton(onClick = playerViewModel::skipToNext) {
                                Icon(Icons.Filled.SkipNext, contentDescription = null, modifier = Modifier.size(32.dp))
                            }
                        }
                        IconButton(onClick = playerViewModel::cycleRepeatMode) {
                            Icon(
                                imageVector = if (playbackMode == PlaybackMode.REPEAT_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                                contentDescription = null,
                                tint = if (playbackMode == PlaybackMode.REPEAT_ALL || playbackMode == PlaybackMode.REPEAT_ONE) {
                                    AccentGreen
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }

                    if (showLyrics) {
                        Spacer(Modifier.height(16.dp))
                        LyricsSection(
                            track = track,
                            positionMs = playerState.positionMs,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                Box(modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)) {
                    SnackbarHost(snackbarHostState)
                }
            }
        }
    }

    if (showDownloadDialog) {
        AlertDialog(
            onDismissRequest = { showDownloadDialog = false },
            title = { Text(stringResource(R.string.download_choice_title)) },
            text = {},
            confirmButton = {
                TextButton(onClick = {
                    showDownloadDialog = false
                    isDownloading = true
                    coroutineScope.launch {
                        downloadTrack(context, track, downloadVideo = false, downloadsViewModel, playlistsViewModel, snackbarHostState)
                        isDownloading = false
                    }
                }) { Text(stringResource(R.string.download_audio)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDownloadDialog = false
                    isDownloading = true
                    coroutineScope.launch {
                        downloadTrack(context, track, downloadVideo = true, downloadsViewModel, playlistsViewModel, snackbarHostState)
                        isDownloading = false
                    }
                }) { Text(stringResource(R.string.download_video)) }
            },
        )
    }

    if (showAddDialog) {
        val isDownloaded = track.id in downloadedIds
        val eligiblePlaylists = playlists.filter { isDownloaded || it.type == PlaylistType.ONLINE }
        PlaylistPickerDialog(
            playlists = eligiblePlaylists,
            isDownloaded = isDownloaded,
            onDismiss = { showAddDialog = false },
            onPlaylistChosen = { playlist ->
                showAddDialog = false
                isAdding = true
                coroutineScope.launch {
                    val added = playlistsViewModel.addTrack(playlist.id, track.copy(isDownloaded = isDownloaded))
                    snackbarHostState.showSnackbar(
                        context.getString(if (added) R.string.playlist_added else R.string.playlist_add_blocked),
                    )
                    isAdding = false
                }
            },
        )
    }
}

private suspend fun downloadTrack(
    context: Context,
    track: Track,
    downloadVideo: Boolean,
    downloadsViewModel: DownloadsViewModel,
    playlistsViewModel: PlaylistsViewModel,
    snackbarHostState: SnackbarHostState,
) {
    try {
        val streams = YouTubeRepository().getDownloadStreams(track.id)
        performDownload(
            context = context,
            streams = streams,
            id = track.id,
            title = track.title,
            artist = track.artist,
            artworkUrl = track.artworkUrl,
            downloadVideo = downloadVideo,
            downloadsViewModel = downloadsViewModel,
            playlistsViewModel = playlistsViewModel,
            onMessage = { resId -> snackbarHostState.showSnackbar(context.getString(resId)) },
        )
    } catch (e: Exception) {
        snackbarHostState.showSnackbar("${context.getString(R.string.playback_error_prefix)}${e.message ?: e.javaClass.simpleName}")
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

@Composable
private fun LyricsSection(track: Track, positionMs: Long, modifier: Modifier = Modifier) {
    var selectedTab by remember(track.id) { mutableStateOf(0) } // 0 = brut, 1 = karaoké
    var lyrics by remember(track.id) { mutableStateOf<LyricsResult?>(null) }
    var isLoading by remember(track.id) { mutableStateOf(true) }
    var loadFailed by remember(track.id) { mutableStateOf(false) }

    LaunchedEffect(track.id) {
        isLoading = true
        loadFailed = false
        val result = runCatching {
            LyricsRepository().getLyrics(track.title, track.artist)
        }.getOrNull()
        lyrics = result
        loadFailed = result == null
        isLoading = false
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // La "barre" : bascule entre paroles brutes (à gauche) et karaoké (à droite).
        Row(modifier = Modifier.fillMaxWidth()) {
            SegmentButton(
                text = "Paroles",
                selected = selectedTab == 0,
                modifier = Modifier.weight(1f),
                onClick = { selectedTab = 0 },
            )
            SegmentButton(
                text = "Karaoké",
                selected = selectedTab == 1,
                modifier = Modifier.weight(1f),
                onClick = { selectedTab = 1 },
            )
        }

        Spacer(Modifier.height(12.dp))

        when {
            isLoading -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            loadFailed || lyrics == null || (lyrics?.plainLyrics == null && lyrics?.syncedLyrics == null) -> {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        "Paroles non disponibles pour ce morceau.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            selectedTab == 0 -> PlainLyricsView(
                text = lyrics?.plainLyrics ?: lyrics?.syncedLyrics.orEmpty(),
                modifier = Modifier.weight(1f),
            )
            else -> KaraokeLyricsView(
                syncedLyrics = lyrics?.syncedLyrics,
                plainLyrics = lyrics?.plainLyrics,
                positionMs = positionMs,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SegmentButton(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = if (selected) AccentGreen.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(vertical = 10.dp),
            textAlign = TextAlign.Center,
            color = if (selected) AccentGreen else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun PlainLyricsView(text: String, modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        item {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun KaraokeLyricsView(syncedLyrics: String?, plainLyrics: String?, positionMs: Long, modifier: Modifier = Modifier) {
    if (syncedLyrics == null) {
        // Pas de version synchronisée disponible : repli sur les paroles brutes.
        PlainLyricsView(text = plainLyrics.orEmpty(), modifier = modifier)
        return
    }

    val lines = remember(syncedLyrics) { parseSyncedLyrics(syncedLyrics) }
    val currentIndex = remember(lines, positionMs) {
        lines.indexOfLast { it.timeMs <= positionMs }.coerceAtLeast(0)
    }
    val listState = rememberLazyListState()

    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0) {
            // On centre la ligne courante plutôt que de la coller en haut, pour qu'elle reste
            // toujours bien visible dans la zone (avant, ça pouvait la pousser hors de vue).
            listState.animateScrollToItem((currentIndex - 1).coerceAtLeast(0))
        }
    }

    LazyColumn(state = listState, modifier = modifier.fillMaxWidth()) {
        item { Spacer(Modifier.height(4.dp)) }
        itemsIndexed(lines) { index, line ->
            Text(
                text = line.text,
                style = if (index == currentIndex) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                color = if (index == currentIndex) AccentGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            )
        }
    }
}
