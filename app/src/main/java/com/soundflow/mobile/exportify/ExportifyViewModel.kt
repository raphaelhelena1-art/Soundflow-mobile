package com.soundflow.mobile.exportify

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soundflow.core.exportify.ExportifyCsvParser
import com.soundflow.core.extraction.YouTubeRepository
import com.soundflow.core.player.Track
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

import androidx.media3.common.util.UnstableApi

data class ExportifyUiState(
    val isImporting: Boolean = false,
    val importProgress: Int = 0,
    val importTotal: Int = 0,
    val currentImportingPlaylistName: String = "",
    val currentImportingTrackName: String = "",
    val errorMessage: String? = null,
    val importSuccessMessage: String? = null,
)

class ExportifyViewModel(
    private val youtubeRepository: YouTubeRepository = YouTubeRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow(ExportifyUiState())
    val state: StateFlow<ExportifyUiState> = _state.asStateFlow()

    /**
     * Importe une playlist à partir d'un texte CSV brut intercepté.
     */
    @UnstableApi
    fun importCsvText(
        csvText: String,
        customPlaylistName: String?,
        playlistsViewModel: PlaylistsViewModel,
    ) {
        importCsv(
            contentResolver = null,
            uri = null,
            rawCsvText = csvText,
            customPlaylistName = customPlaylistName,
            playlistsViewModel = playlistsViewModel,
        )
    }

    /**
     * Importe une playlist directement à partir de son URL Spotify (open.spotify.com/playlist/...)
     */
    @UnstableApi
    fun importSpotifyUrl(
        url: String,
        playlistsViewModel: PlaylistsViewModel,
    ) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isImporting = true,
                    importProgress = 0,
                    importTotal = 0,
                    currentImportingPlaylistName = "Analyse de la playlist...",
                    currentImportingTrackName = "",
                    errorMessage = null,
                    importSuccessMessage = null,
                )
            }

            try {
                val parsedPlaylist = com.soundflow.core.exportify.SpotifyUrlParser.parseSpotifyUrl(url)
                if (parsedPlaylist == null || parsedPlaylist.tracks.isEmpty()) {
                    _state.update {
                        it.copy(
                            isImporting = false,
                            errorMessage = "Impossible de lire la playlist Spotify. Vérifiez que le lien est valide.",
                        )
                    }
                    return@launch
                }

                _state.update {
                    it.copy(
                        currentImportingPlaylistName = parsedPlaylist.name,
                        importProgress = 0,
                        importTotal = parsedPlaylist.tracks.size,
                    )
                }

                val semaphore = Semaphore(4)
                val progressCounter = AtomicInteger(0)

                val resolvedTracks = coroutineScope {
                    val deferredList = parsedPlaylist.tracks.map { eTrack ->
                        async {
                            semaphore.withPermit {
                                val queryDisplay = if (eTrack.artist.isNotBlank()) "${eTrack.artist} - ${eTrack.title}" else eTrack.title
                                val currentProgress = progressCounter.incrementAndGet()

                                _state.update {
                                    it.copy(
                                        importProgress = currentProgress,
                                        currentImportingTrackName = queryDisplay,
                                    )
                                }

                                val resolved = runCatching {
                                    val bestMatch = youtubeRepository.searchBestMatch(
                                        title = eTrack.title,
                                        artist = eTrack.artist,
                                        durationMs = eTrack.durationMs,
                                    )
                                    if (bestMatch != null) {
                                        Track(
                                            id = bestMatch.id,
                                            title = eTrack.title,
                                            artist = eTrack.artist.ifEmpty { bestMatch.uploaderName },
                                            mediaUrl = bestMatch.id,
                                            artworkUrl = bestMatch.thumbnailUrl,
                                            hasVideo = false,
                                            isDownloaded = false,
                                        )
                                    } else null
                                }.getOrNull()

                                delay(40)
                                resolved
                            }
                        }
                    }
                    deferredList.awaitAll().filterNotNull()
                }

                if (resolvedTracks.isNotEmpty()) {
                    playlistsViewModel.createPlaylistWithTracks(parsedPlaylist.name, resolvedTracks)
                    _state.update {
                        it.copy(
                            isImporting = false,
                            importSuccessMessage = "Playlist \"${parsedPlaylist.name}\" importée avec succès (${resolvedTracks.size} morceau(x) récupéré(s)) !",
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            isImporting = false,
                            errorMessage = "Aucun morceau n'a pu être résolu sur YouTube à partir de cette playlist.",
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isImporting = false,
                        errorMessage = "Erreur lors de l'import Spotify : ${e.message}",
                    )
                }
            }
        }
    }

    /**
     * Importe une playlist à partir du contenu CSV (texte brut ou Uri d'un fichier).
     */
    @UnstableApi
    fun importCsv(
        contentResolver: ContentResolver?,
        uri: Uri?,
        rawCsvText: String?,
        customPlaylistName: String?,
        playlistsViewModel: PlaylistsViewModel,
    ) {
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

            try {
                val parsedPlaylist = when {
                    uri != null && contentResolver != null -> {
                        val fileName = getFileNameFromUri(contentResolver, uri) ?: customPlaylistName
                        val stream = contentResolver.openInputStream(uri)
                        if (stream != null) {
                            ExportifyCsvParser.parse(stream, fileName)
                        } else {
                            throw IllegalArgumentException("Impossible d'ouvrir le fichier CSV sélectionné.")
                        }
                    }
                    !rawCsvText.isNullOrBlank() -> {
                        ExportifyCsvParser.parseText(rawCsvText, customPlaylistName ?: "Playlist Exportify")
                    }
                    else -> throw IllegalArgumentException("Aucun fichier CSV ni contenu fourni.")
                }

                val tracksToImport = parsedPlaylist.tracks
                if (tracksToImport.isEmpty()) {
                    _state.update {
                        it.copy(
                            isImporting = false,
                            errorMessage = "Le fichier CSV est vide ou n'a pu être interprété.",
                        )
                    }
                    return@launch
                }

                _state.update {
                    it.copy(
                        currentImportingPlaylistName = parsedPlaylist.name,
                        importProgress = 0,
                        importTotal = tracksToImport.size,
                    )
                }

                val semaphore = Semaphore(4)
                val progressCounter = AtomicInteger(0)

                val resolvedTracks = coroutineScope {
                    val deferredList = tracksToImport.map { eTrack ->
                        async {
                            semaphore.withPermit {
                                val queryDisplay = if (eTrack.artist.isNotBlank()) "${eTrack.artist} - ${eTrack.title}" else eTrack.title
                                val currentProgress = progressCounter.incrementAndGet()

                                _state.update {
                                    it.copy(
                                        importProgress = currentProgress,
                                        currentImportingTrackName = queryDisplay,
                                    )
                                }

                                val resolved = runCatching {
                                    val bestMatch = youtubeRepository.searchBestMatch(
                                        title = eTrack.title,
                                        artist = eTrack.artist,
                                        durationMs = eTrack.durationMs,
                                    )
                                    if (bestMatch != null) {
                                        Track(
                                            id = bestMatch.id,
                                            title = eTrack.title,
                                            artist = eTrack.artist.ifEmpty { bestMatch.uploaderName },
                                            mediaUrl = bestMatch.id,
                                            artworkUrl = bestMatch.thumbnailUrl,
                                            hasVideo = false,
                                            isDownloaded = false,
                                        )
                                    } else null
                                }.getOrNull()

                                delay(40)
                                resolved
                            }
                        }
                    }
                    deferredList.awaitAll().filterNotNull()
                }

                if (resolvedTracks.isNotEmpty()) {
                    playlistsViewModel.createPlaylistWithTracks(parsedPlaylist.name, resolvedTracks)
                    _state.update {
                        it.copy(
                            isImporting = false,
                            importSuccessMessage = "Playlist \"${parsedPlaylist.name}\" importée avec succès (${resolvedTracks.size} morceau(x) récupéré(s)) !",
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            isImporting = false,
                            errorMessage = "Aucun morceau n'a pu être résolu sur YouTube à partir de cette playlist.",
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isImporting = false,
                        errorMessage = "Erreur lors de l'import Exportify : ${e.message}",
                    )
                }
            }
        }
    }

    private fun getFileNameFromUri(contentResolver: ContentResolver, uri: Uri): String? {
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val displayNameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (displayNameIndex != -1) {
                    return it.getString(displayNameIndex)
                }
            }
        }
        return uri.lastPathSegment
    }

    fun clearMessages() {
        _state.update { it.copy(errorMessage = null, importSuccessMessage = null) }
    }
}
