package com.soundflow.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import com.soundflow.core.player.PlaybackMode
import com.soundflow.core.player.Track
import com.soundflow.mobile.downloads.DownloadsViewModel
import com.soundflow.mobile.player.PlayerViewModel
import com.soundflow.mobile.playlists.PlaylistsViewModel
import com.soundflow.mobile.ui.screens.NowPlayingScreen

private val AccentGreen = Color(0xFF1DB954)

/**
 * Mini-lecteur fixe, affiché au-dessus de la barre de navigation sur tous les onglets
 * dès qu'un morceau est chargé (voir SoundFlowNavHost). Taper sur la vignette ouvre
 * l'écran "Lecture en cours" en plein écran.
 */
@UnstableApi
@Composable
fun MiniPlayerBar(
    track: Track,
    isPlaying: Boolean,
    isBuffering: Boolean,
    positionMs: Long,
    durationMs: Long,
    player: Player?,
    playbackMode: PlaybackMode,
    playerViewModel: PlayerViewModel,
    playlistsViewModel: PlaylistsViewModel,
    downloadsViewModel: DownloadsViewModel,
) {
    var showNowPlaying by remember { mutableStateOf(false) }
    val showVideo = track.hasVideo && player != null
    val playlists by playlistsViewModel.playlists.collectAsStateWithLifecycle()
    val isFavorite = playlists.any { it.id == PlaylistsViewModel.FAVORITES_PLAYLIST_ID && it.tracks.any { t -> t.id == track.id } }

    Surface(tonalElevation = 3.dp, shadowElevation = 4.dp) {
        Column {
            val progress = if (durationMs > 0) positionMs.toFloat() / durationMs.toFloat() else 0f
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(2.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { showNowPlaying = true },
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        showVideo -> VideoSurface(player = player, useController = false, modifier = Modifier.fillMaxSize())
                        track.artworkUrl != null -> key(track.id) {
                            AsyncImage(
                                model = track.artworkUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        else -> Icon(Icons.Filled.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Cœur des favoris, à droite du titre.
                IconButton(
                    onClick = { playlistsViewModel.toggleFavorite(track) },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = null,
                        tint = if (isFavorite) AccentGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                ModeButton(
                    icon = Icons.Filled.Shuffle,
                    active = playbackMode == PlaybackMode.SHUFFLE,
                    onClick = playerViewModel::toggleShuffle,
                )

                if (isBuffering) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(4.dp))
                } else {
                    IconButton(onClick = playerViewModel::togglePlayPause, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Lecture",
                        )
                    }
                }

                // Un seul bouton répétition, façon Spotify : Désactivé → Toutes → Une seule (icône avec "1").
                ModeButton(
                    icon = if (playbackMode == PlaybackMode.REPEAT_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    active = playbackMode == PlaybackMode.REPEAT_ALL || playbackMode == PlaybackMode.REPEAT_ONE,
                    onClick = playerViewModel::cycleRepeatMode,
                )
            }
        }
    }

    if (showNowPlaying) {
        NowPlayingScreen(
            playerViewModel = playerViewModel,
            playlistsViewModel = playlistsViewModel,
            downloadsViewModel = downloadsViewModel,
            onDismiss = { showNowPlaying = false },
        )
    }
}

/** Bouton de mode de lecture — surligné en vert quand actif. */
@Composable
private fun ModeButton(icon: ImageVector, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .then(if (active) Modifier.background(AccentGreen.copy(alpha = 0.2f)) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (active) AccentGreen else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}
