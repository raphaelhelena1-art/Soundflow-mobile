package com.soundflow.mobile.spotify

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.soundflow.core.extraction.YouTubeRepository
import com.soundflow.core.player.Track
import com.soundflow.core.spotify.SpotifyPlaylist
import com.soundflow.core.spotify.SpotifyRepository
import com.soundflow.mobile.playlists.PlaylistsViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicInteger

data class SpotifyUiState(
    val clientId: String = SpotifyRepository.DEFAULT_CLIENT_ID,
    val isAuthenticated: Boolean = false,
    val isLoadingPlaylists: Boolean = false,
    val playlists: List<SpotifyPlaylist> = emptyList(),
    val selectedPlaylistIds: Set<String> = emptySet(),
    val isImporting: Boolean = false,
    val importProgress: Int = 0,
    val importTotal: Int = 0,
    val currentImportingPlaylistName: String = "",
    val currentImportingTrackName: String = "",
    val errorMessage: String? = null,
    val importSuccessMessage: String? = null,
)

class SpotifyViewModel(
    private val spotifyRepository: SpotifyRepository = SpotifyRepository(),
    private val youtubeRepository: YouTubeRepository = YouTubeRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow(SpotifyUiState())
    val state: StateFlow<SpotifyUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            SpotifyAuthChannel.codeFlow.collect { code ->
                onAuthCodeReceived(code)
            }
        }
    }

    fun setClientId(clientId: String) {
        _state.update { it.copy(clientId = clientId) }
    }

    fun getAuthorizationUrl(customClientId: String? = null): String {
        val cid = customClientId?.ifEmpty { null } ?: _state.value.clientId
        return spotifyRepository.generateAuthorizationUrl(cid)
    }

    private fun onAuthCodeReceived(code: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingPlaylists = true, errorMessage = null) }
            val success = spotifyRepository.exchangeCodeForToken(code, _state.value.clientId)
            if (success) {
                _state.update { it.copy(isAuthenticated = true) }
                loadPlaylists()
            } else {
                _state.update {
                    it.copy(
                        isLoadingPlaylists = false,
                        errorMessage = "Échec de l'authentification Spotify. Vérifiez votre Client ID.",
                    )
                }
            }
        }
    }

    fun loadPlaylists() {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingPlaylists = true, errorMessage = null) }
            try {
                val playlists = spotifyRepository.getUserPlaylists()
                _state.update {
                    it.copy(
                        isLoadingPlaylists = false,
                        playlists = playlists,
                        selectedPlaylistIds = emptySet(),
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoadingPlaylists = false,
                        errorMessage = "Impossible de charger vos playlists Spotify : ${e.message}",
                    )
                }
            }
        }
    }

    fun togglePlaylistSelection(playlistId: String) {
        _state.update { current ->
            val newSelection = if (playlistId in current.selectedPlaylistIds) {
                current.selectedPlaylistIds - playlistId
            } else {
                current.selectedPlaylistIds + playlistId
            }
            current.copy(selectedPlaylistIds = newSelection)
        }
    }

    fun selectAllPlaylists() {
        _state.update { current ->
            current.copy(selectedPlaylistIds = current.playlists.map { it.id }.toSet())
        }
    }

    fun deselectAllPlaylists() {
        _state.update { current ->
            current.copy(selectedPlaylistIds = emptySet())
        }
    }

    @UnstableApi
    fun importSelectedPlaylists(playlistsViewModel: PlaylistsViewModel) {
        val selectedPlaylists = _state.value.playlists.filter { it.id in _state.value.selectedPlaylistIds }
        if (selectedPlaylists.isEmpty()) return

        viewModelScope.launch {
            _state.update {
                it.copy(
                    isImporting = true,
                    importProgress = 0,
                    importTotal = 0,
                    currentImportingPlaylistName = "",
                    currentImportingTrackName = "",
                    errorMessage = null,
                    importSuccessMessage = null,
                )
            }

            var importedCount = 0
            var totalTracksResolved = 0

            try {
                for ((pIndex, playlist) in selectedPlaylists.withIndex()) {
                    _state.update {
                        it.copy(
                            currentImportingPlaylistName = "${playlist.name} (${pIndex + 1}/${selectedPlaylists.size})",
                        )
                    }

                    val spotifyTracks = spotifyRepository.getPlaylistTracks(playlist.id)
                    if (spotifyTracks.isEmpty()) continue

                    _state.update {
                        it.copy(
                            importProgress = 0,
                            importTotal = spotifyTracks.size,
                        )
                    }

                    val semaphore = Semaphore(4)
                    val progressCounter = AtomicInteger(0)

                    val resolvedTracks = coroutineScope {
                        val deferredList = spotifyTracks.map { sTrack ->
                            async {
                                semaphore.withPermit {
                                    val artistsStr = sTrack.artists.joinToString(", ") { it.name }
                                    val query = "$artistsStr - ${sTrack.name}".trim(' ', '-')
                                    val currentProgress = progressCounter.incrementAndGet()

                                    _state.update {
                                        it.copy(
                                            importProgress = currentProgress,
                                            currentImportingTrackName = query,
                                        )
                                    }

                                    val track = runCatching {
                                        val topMatch = youtubeRepository.searchBestMatch(
                                            title = sTrack.name,
                                            artist = artistsStr,
                                            durationMs = sTrack.durationMs,
                                        )
                                        if (topMatch != null) {
                                            Track(
                                                id = topMatch.id,
                                                title = sTrack.name,
                                                artist = artistsStr.ifEmpty { topMatch.uploaderName },
                                                mediaUrl = topMatch.id,
                                                artworkUrl = sTrack.album?.images?.firstOrNull()?.url ?: topMatch.thumbnailUrl,
                                                hasVideo = false,
                                                isDownloaded = false,
                                            )
                                        } else null
                                    }.getOrNull()

                                    delay(40) // Délai léger d'espacement des requêtes
                                    track
                                }
                            }
                        }
                        deferredList.awaitAll().filterNotNull()
                    }

                    if (resolvedTracks.isNotEmpty()) {
                        playlistsViewModel.createPlaylistWithTracks(playlist.name, resolvedTracks)
                        importedCount++
                        totalTracksResolved += resolvedTracks.size
                    }
                }

                if (importedCount > 0) {
                    _state.update {
                        it.copy(
                            isImporting = false,
                            selectedPlaylistIds = emptySet(),
                            importSuccessMessage = "$importedCount playlist(s) importée(s) avec succès ($totalTracksResolved morceaux récupérés) !",
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            isImporting = false,
                            errorMessage = "Aucun morceau n'a pu être importé des playlists sélectionnées.",
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isImporting = false,
                        errorMessage = "Erreur lors de l'import des playlists : ${e.message}",
                    )
                }
            }
        }
    }

    @UnstableApi
    fun importPlaylist(playlist: SpotifyPlaylist, playlistsViewModel: PlaylistsViewModel) {
        _state.update { it.copy(selectedPlaylistIds = setOf(playlist.id)) }
        importSelectedPlaylists(playlistsViewModel)
    }

    fun clearMessages() {
        _state.update { it.copy(errorMessage = null, importSuccessMessage = null) }
    }
}
