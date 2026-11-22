package com.soundflow.mobile.playlists

import android.app.Application
import android.content.Context
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.soundflow.core.player.Track
import com.soundflow.mobile.data.LocalStore
import com.soundflow.mobile.downloads.DownloadsViewModel
import com.soundflow.mobile.downloads.performDownload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

@Serializable
enum class PlaylistType { ONLINE, OFFLINE }

@Serializable
data class Playlist(
    val id: String = "",
    val name: String,
    val type: PlaylistType,
    val tracks: List<Track> = emptyList(),
    val isDeletable: Boolean = true,
)

@Serializable
private data class SharedPlaylistPayload(
    val name: String,
    val tracks: List<Track>,
)

/** Playlists persistées localement (survit au redémarrage de l'app). */
@UnstableApi
class PlaylistsViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        const val FAVORITES_PLAYLIST_ID = "favorites"
        private val DEFAULT_PLAYLISTS = listOf(
            Playlist(id = FAVORITES_PLAYLIST_ID, name = "Favoris", type = PlaylistType.ONLINE, isDeletable = false),
        )
    }

    private val store = LocalStore(application)
    private val json = Json { ignoreUnknownKeys = true }

    private val _playlists = MutableStateFlow(DEFAULT_PLAYLISTS)
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _downloadedTrackIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadedTrackIds: StateFlow<Set<String>> = _downloadedTrackIds.asStateFlow()

    private val _pendingOpenPlaylistId = MutableStateFlow<String?>(null)
    val pendingOpenPlaylistId: StateFlow<String?> = _pendingOpenPlaylistId.asStateFlow()

    fun requestOpenPlaylist(playlistId: String) {
        _pendingOpenPlaylistId.value = playlistId
    }

    fun consumeOpenPlaylistRequest() {
        _pendingOpenPlaylistId.value = null
    }

    init {
        viewModelScope.launch {
            store.loadPlaylists()?.let { saved ->
                val hasFavorites = saved.any { it.id == FAVORITES_PLAYLIST_ID }
                _playlists.value = if (hasFavorites) saved else DEFAULT_PLAYLISTS + saved
            }
            store.loadDownloadedIds()?.let { _downloadedTrackIds.value = it }
        }

        viewModelScope.launch {
            store.downloadsFlow.collect { downloadsList ->
                val completedMap = downloadsList.filter { !it.localUri.isNullOrBlank() }
                    .associateBy { store.extractVideoId(it.id) }

                if (completedMap.isNotEmpty()) {
                    _downloadedTrackIds.update { current ->
                        current + completedMap.values.map { it.id }
                    }
                    persistDownloadedIds()

                    _playlists.update { playlistsList ->
                        playlistsList.map { playlist ->
                            val updatedTracks = playlist.tracks.map { track ->
                                val vId = store.extractVideoId(track.id)
                                val match = completedMap[vId]
                                if (match != null && match.localUri != null) {
                                    track.copy(mediaUrl = match.localUri, isDownloaded = true)
                                } else {
                                    track
                                }
                            }
                            playlist.copy(tracks = updatedTracks)
                        }
                    }
                    persistPlaylists()
                }
            }
        }
    }

    private fun persistPlaylists() {
        viewModelScope.launch(Dispatchers.IO) { store.savePlaylists(_playlists.value) }
    }

    private fun persistDownloadedIds() {
        viewModelScope.launch(Dispatchers.IO) { store.saveDownloadedIds(_downloadedTrackIds.value) }
    }

    fun markDownloaded(trackId: String) {
        _downloadedTrackIds.update { it + trackId }
        persistDownloadedIds()
    }

    fun unmarkDownloaded(trackId: String) {
        _downloadedTrackIds.update { it - trackId }
        persistDownloadedIds()
    }

    fun removeFromOfflinePlaylists(trackId: String) {
        _playlists.update { list ->
            list.map { playlist ->
                if (playlist.type == PlaylistType.OFFLINE) {
                    playlist.copy(tracks = playlist.tracks.filterNot { it.id == trackId })
                } else {
                    playlist
                }
            }
        }
        persistPlaylists()
    }

    @Suppress("unused")
    fun isDownloaded(trackId: String): Boolean = trackId in _downloadedTrackIds.value

    /** Renvoie false si une playlist du même nom existe déjà (insensible à la casse). */
    fun createPlaylist(name: String, type: PlaylistType): Boolean {
        val alreadyExists = _playlists.value.any { it.name.equals(name, ignoreCase = true) }
        if (alreadyExists) return false
        _playlists.update { it + Playlist(id = UUID.randomUUID().toString(), name = name, type = type) }
        persistPlaylists()
        return true
    }

    /** Crée une nouvelle playlist contenant directement des morceaux. */
    fun createPlaylistWithTracks(name: String, tracks: List<Track>, type: PlaylistType = PlaylistType.ONLINE): Boolean {
        var playlistName = name
        var counter = 1
        while (_playlists.value.any { it.name.equals(playlistName, ignoreCase = true) }) {
            playlistName = "$name ($counter)"
            counter++
        }
        val newPlaylist = Playlist(
            id = UUID.randomUUID().toString(),
            name = playlistName,
            type = type,
            tracks = tracks,
        )
        _playlists.update { it + newPlaylist }
        persistPlaylists()
        return true
    }

    /** Renomme une playlist existante. Renvoie false si le nom est vide ou déjà utilisé. */
    fun renamePlaylist(playlistId: String, newName: String): Boolean {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return false
        val alreadyExists = _playlists.value.any { it.id != playlistId && it.name.equals(trimmed, ignoreCase = true) }
        if (alreadyExists) return false

        _playlists.update { list ->
            list.map {
                if (it.id == playlistId) it.copy(name = trimmed) else it
            }
        }
        persistPlaylists()
        return true
    }

    fun deletePlaylist(playlistId: String) {
        _playlists.update { list -> list.filterNot { it.id == playlistId && it.isDeletable } }
        persistPlaylists()
    }

    fun addTrack(playlistId: String, track: Track): Boolean {
        val playlist = _playlists.value.firstOrNull { it.id == playlistId } ?: return false
        if (playlist.type == PlaylistType.OFFLINE && !track.isDownloaded) return false

        _playlists.update { list ->
            list.map {
                if (it.id == playlistId && it.tracks.none { existing -> existing.id == track.id }) {
                    it.copy(tracks = it.tracks + track)
                } else {
                    it
                }
            }
        }
        persistPlaylists()
        return true
    }

    fun removeTrack(playlistId: String, trackId: String) {
        _playlists.update { list ->
            list.map {
                if (it.id == playlistId) it.copy(tracks = it.tracks.filterNot { t -> t.id == trackId }) else it
            }
        }
        persistPlaylists()
    }

    @Suppress("unused")
    fun moveTrack(playlistId: String, fromIndex: Int, toIndex: Int) {
        _playlists.update { list ->
            list.map { playlist ->
                if (playlist.id != playlistId) return@map playlist
                val tracks = playlist.tracks.toMutableList()
                if (fromIndex !in tracks.indices || toIndex !in tracks.indices) return@map playlist
                val track = tracks.removeAt(fromIndex)
                tracks.add(toIndex, track)
                playlist.copy(tracks = tracks)
            }
        }
        persistPlaylists()
    }

    fun isFavorite(trackId: String): Boolean =
        _playlists.value.firstOrNull { it.id == FAVORITES_PLAYLIST_ID }?.tracks?.any { it.id == trackId } == true

    fun toggleFavorite(track: Track) {
        if (isFavorite(track.id)) {
            removeTrack(FAVORITES_PLAYLIST_ID, track.id)
        } else {
            addTrack(FAVORITES_PLAYLIST_ID, track)
        }
    }

    /** Génère une URL de partage contenant toutes les infos de la playlist. */
    fun generateShareUrl(playlist: Playlist): String {
        val payload = SharedPlaylistPayload(name = playlist.name, tracks = playlist.tracks)
        val jsonString = json.encodeToString(payload)
        val encoded = Base64.encodeToString(jsonString.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP)
        return "https://soundflow.app/playlist?data=$encoded"
    }

    /** Importe une playlist depuis une URL de partage et télécharge un par un les morceaux manquants. */
    fun importPlaylistFromUrl(
        context: Context,
        rawUrlOrData: String,
        downloadsViewModel: DownloadsViewModel,
        onResult: (Boolean, String) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val dataParam = when {
                    rawUrlOrData.contains("data=") -> rawUrlOrData.substringAfter("data=").substringBefore("&")
                    else -> rawUrlOrData.trim()
                }

                val jsonBytes = Base64.decode(dataParam, Base64.URL_SAFE or Base64.NO_WRAP)
                val jsonString = String(jsonBytes, Charsets.UTF_8)
                val payload = json.decodeFromString<SharedPlaylistPayload>(jsonString)

                val baseName = payload.name.ifBlank { "Playlist Partagée" }
                var playlistName = baseName
                var counter = 1
                while (_playlists.value.any { it.name.equals(playlistName, ignoreCase = true) }) {
                    playlistName = "$baseName ($counter)"
                    counter++
                }

                val newPlaylist = Playlist(id = UUID.randomUUID().toString(), name = playlistName, type = PlaylistType.ONLINE, tracks = payload.tracks)
                _playlists.update { it + newPlaylist }
                persistPlaylists()

                // Télécharge un par un tous les morceaux qui ne sont pas encore téléchargés localement
                for (track in payload.tracks) {
                    val isLocal = downloadsViewModel.downloads.value.any {
                        store.extractVideoId(it.id) == store.extractVideoId(track.id) && it.localUri != null
                    }
                    if (!isLocal) {
                        performDownload(
                            context = context,
                            id = track.id,
                            title = track.title,
                            artist = track.artist,
                            artworkUrl = track.artworkUrl,
                            downloadVideo = track.hasVideo,
                            downloadsViewModel = downloadsViewModel,
                            playlistsViewModel = this@PlaylistsViewModel,
                            onMessage = {},
                        )
                    }
                }

                requestOpenPlaylist(newPlaylist.id)
                onResult(true, "Playlist \"$playlistName\" importée avec succès !")
            }.onFailure {
                onResult(false, "Impossible d'importer la playlist : format invalide.")
            }
        }
    }

    /** Convertit une playlist en ligne en playlist hors ligne et télécharge chaque morceau un par un en version audio. */
    fun convertToOffline(
        playlistId: String,
        context: Context,
        downloadsViewModel: DownloadsViewModel,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val playlist = _playlists.value.firstOrNull { it.id == playlistId } ?: return@launch
            if (playlist.type == PlaylistType.OFFLINE) return@launch

            _playlists.update { list ->
                list.map { if (it.id == playlistId) it.copy(type = PlaylistType.OFFLINE) else it }
            }
            persistPlaylists()

            for (track in playlist.tracks) {
                val isLocal = downloadsViewModel.downloads.value.any {
                    store.extractVideoId(it.id) == store.extractVideoId(track.id) && it.localUri != null
                }
                if (!isLocal) {
                    performDownload(
                        context = context,
                        id = track.id,
                        title = track.title,
                        artist = track.artist,
                        artworkUrl = track.artworkUrl,
                        downloadVideo = false,
                        downloadsViewModel = downloadsViewModel,
                        playlistsViewModel = this@PlaylistsViewModel,
                        onMessage = {},
                    )
                    delay(600.milliseconds) // Espacement de 600 ms entre les requêtes pour éviter le blocage HTTP 429 de YouTube
                }
            }
        }
    }
}
