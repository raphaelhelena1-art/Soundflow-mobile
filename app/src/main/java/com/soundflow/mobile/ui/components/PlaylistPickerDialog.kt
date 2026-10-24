package com.soundflow.mobile.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.soundflow.mobile.R
import com.soundflow.mobile.playlists.Playlist
import com.soundflow.mobile.playlists.PlaylistType
import com.soundflow.mobile.playlists.PlaylistsViewModel

private val OfflinePurple = Color(0xFF9C6ADE)

@Composable
fun PlaylistPickerDialog(
    playlists: List<Playlist>,
    isDownloaded: Boolean,
    onDismiss: () -> Unit,
    onPlaylistChosen: (Playlist) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.playlist_add_title)) },
        text = {
            Column {
                if (!isDownloaded) {
                    Text(
                        text = stringResource(R.string.playlist_add_blocked),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                playlists.forEach { playlist ->
                    val name = if (playlist.id == PlaylistsViewModel.FAVORITES_PLAYLIST_ID) {
                        stringResource(R.string.playlist_favorites)
                    } else {
                        playlist.name
                    }
                    val color = if (playlist.type == PlaylistType.OFFLINE) OfflinePurple else MaterialTheme.colorScheme.onSurface
                    Text(
                        text = name,
                        color = color,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlaylistChosen(playlist) }
                            .padding(vertical = 10.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.playlist_cancel)) }
        },
    )
}
