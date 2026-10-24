package com.soundflow.mobile.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soundflow.core.extraction.DownloadStreams
import com.soundflow.core.extraction.SearchResult
import com.soundflow.core.extraction.YouTubeRepository
import com.soundflow.core.player.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ArtistSuggestion(val name: String, val thumbnailUrl: String?)

data class HomeUiState(
    val items: List<SearchResult> = emptyList(),
    val artists: List<ArtistSuggestion> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

class HomeViewModel : ViewModel() {

    private val repository = YouTubeRepository()

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        loadTrending()
    }

    fun loadTrending() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val trending = repository.getTrending()
                _state.update { it.copy(items = trending, artists = deriveArtists(trending), isLoading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }

    /** Réutilise les résultats déjà récupérés (pas d'appel réseau en plus) : dédoublonne les
     * noms de chaîne/artiste et prend la miniature du premier morceau associé comme avatar. */
    private fun deriveArtists(results: List<SearchResult>): List<ArtistSuggestion> {
        val seen = LinkedHashMap<String, ArtistSuggestion>()
        for (result in results) {
            val cleanName = cleanArtistName(result.uploaderName)
            if (cleanName.isBlank() || seen.containsKey(cleanName)) continue
            seen[cleanName] = ArtistSuggestion(cleanName, result.thumbnailUrl)
        }
        return seen.values.take(15).toList()
    }

    private fun cleanArtistName(name: String): String = name
        .replace(Regex("""\s*-\s*Topic$""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""VEVO$""", RegexOption.IGNORE_CASE), "")
        .trim()

    suspend fun resolveTrack(result: SearchResult): Track? {
        val streams = repository.resolvePlaybackStreams(result.id)
        val url = streams.combinedVideoUrl ?: streams.audioUrl ?: return null
        return Track(
            id = result.id,
            title = result.title,
            artist = result.uploaderName,
            mediaUrl = url,
            artworkUrl = result.thumbnailUrl,
            hasVideo = streams.combinedVideoUrl != null,
        )
    }

    suspend fun getDownloadStreams(result: SearchResult): DownloadStreams =
        repository.getDownloadStreams(result.id)
}
