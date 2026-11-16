package com.soundflow.mobile.spotify

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soundflow.core.extraction.YouTubeRepository
import com.soundflow.core.player.Track
import com.soundflow.core.spotify.SpotifyPlaylist
import com.soundflow.core.spotify.SpotifyRepository
import com.soundflow.mobile.playlists.PlaylistsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SpotifyUiState(
    val clientId: String = SpotifyRepository.DEFAULT_CLIENT_ID,
    val isAuthenticated: Boolean = false,
    val isLoadingPlaylists: Boolean = false,
    val playlists: List<SpotifyPlaylist> = emptyList(),
    val isImporting: Boolean = false,
    val importProgress: Int = 0,
    val importTotal: Int = 0,
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
                _state.update { it.copy(isLoadingPlaylists = false, playlists = playlists) }
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

    fun importPlaylist(playlist: SpotifyPlaylist, playlistsViewModel: PlaylistsViewModel) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isImporting = true,
                    importProgress = 0,
                    importTotal = 0,
                    currentImportingTrackName = "",
                    errorMessage = null,
                    importSuccessMessage = null,
                )
            }

            try {
                val spotifyTracks = spotifyRepository.getPlaylistTracks(playlist.id)
                if (spotifyTracks.isEmpty()) {
                    _state.update {
                        it.copy(
                            isImporting = false,
                            errorMessage = "Cette playlist Spotify est vide.",
                        )
                    }
                    return@launch
                }

                _state.update { it.copy(importTotal = spotifyTracks.size) }
                val resolvedTracks = mutableListOf<Track>()

                spotifyTracks.forEachIndexed { index, sTrack ->
                    val artistsStr = sTrack.artists.joinToString(", ") { it.name }
                    val query = "$artistsStr - ${sTrack.name}".trim(' ', '-')
                    _state.update {
                        it.copy(
                            importProgress = index + 1,
                            currentImportingTrackName = query,
                        )
                    }

                    runCatching {
                        val results = youtubeRepository.search(query)
                        val topMatch = results.firstOrNull()
                        if (topMatch != null) {
                            resolvedTracks.add(
                                Track(
                                    id = topMatch.id,
                                    title = sTrack.name,
                                    artist = artistsStr.ifEmpty { topMatch.uploaderName },
                                    mediaUrl = topMatch.id,
                                    artworkUrl = sTrack.album?.images?.firstOrNull()?.url ?: topMatch.thumbnailUrl,
                                    hasVideo = false,
                                    isDownloaded = false,
                                ),
                            )
                        }
                    }

                    delay(100) // Delai pour éviter tout rate-limiting sur le réseau
                }

                if (resolvedTracks.isNotEmpty()) {
                    playlistsViewModel.createPlaylistWithTracks(playlist.name, resolvedTracks)
                    _state.update {
                        it.copy(
                            isImporting = false,
                            importSuccessMessage = "Playlist \"${playlist.name}\" importée avec succès (${resolvedTracks.size}/${spotifyTracks.size} morceaux) !",
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            isImporting = false,
                            errorMessage = "Aucun morceau n'a pu être trouvé sur YouTube pour cette playlist.",
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isImporting = false,
                        errorMessage = "Erreur lors de l'import de la playlist : ${e.message}",
                    )
                }
            }
        }
    }

    fun clearMessages() {
        _state.update { it.copy(errorMessage = null, importSuccessMessage = null) }
    }
}
