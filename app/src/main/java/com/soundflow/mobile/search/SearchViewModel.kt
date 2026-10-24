package com.soundflow.mobile.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.soundflow.core.extraction.DownloadStreams
import com.soundflow.core.extraction.SearchResult
import com.soundflow.core.extraction.YouTubeRepository
import com.soundflow.core.player.Track
import com.soundflow.mobile.data.LocalStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val suggestions: List<String> = emptyList(),
    val history: List<String> = emptyList(),
    val results: List<SearchResult> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = false,
    val errorMessage: String? = null,
)

class SearchViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = YouTubeRepository()
    private val store = LocalStore(application)

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var suggestionsJob: Job? = null
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            store.loadSearchHistory()?.let { history -> _state.update { it.copy(history = history) } }
        }
    }

    fun onQueryChange(newQuery: String) {
        _state.update { it.copy(query = newQuery) }

        suggestionsJob?.cancel()
        searchJob?.cancel()

        if (newQuery.isBlank()) {
            _state.update {
                it.copy(
                    suggestions = emptyList(),
                    results = emptyList(),
                    isLoading = false,
                    canLoadMore = false,
                    errorMessage = null,
                )
            }
            return
        }

        suggestionsJob = viewModelScope.launch {
            delay(150)
            try {
                val suggestions = repository.getSuggestions(newQuery)
                _state.update { it.copy(suggestions = suggestions) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("SoundFlow", "Suggestions indisponibles pour '$newQuery'", e)
            }
        }

        searchJob = viewModelScope.launch {
            delay(400)
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val results = repository.search(newQuery)
                _state.update {
                    it.copy(results = results, isLoading = false, canLoadMore = repository.hasMoreResults)
                }
                recordHistory(newQuery)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(isLoading = false, errorMessage = "Recherche impossible : ${e.message}")
                }
            }
        }
    }

    fun loadMoreResults() {
        if (_state.value.isLoadingMore || !_state.value.canLoadMore) return
        viewModelScope.launch {
            _state.update { it.copy(isLoadingMore = true) }
            try {
                val more = repository.loadMoreResults()
                _state.update { current ->
                    val existingIds = current.results.map { it.id }.toSet()
                    val deduped = more.filterNot { it.id in existingIds }
                    current.copy(
                        results = current.results + deduped,
                        isLoadingMore = false,
                        canLoadMore = repository.hasMoreResults,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    fun onSuggestionSelected(suggestion: String) {
        suggestionsJob?.cancel()
        _state.update { it.copy(suggestions = emptyList()) }
        onQueryChange(suggestion)
    }

    /** Appelé quand l'utilisateur valide la recherche (touche Entrée) : referme les suggestions. */
    fun onSearchSubmitted() {
        suggestionsJob?.cancel()
        _state.update { it.copy(suggestions = emptyList()) }
    }

    fun onHistoryEntrySelected(query: String) {
        onQueryChange(query)
    }

    fun removeHistoryEntry(query: String) {
        _state.update { it.copy(history = it.history.filterNot { entry -> entry == query }) }
        viewModelScope.launch { store.saveSearchHistory(_state.value.history) }
    }

    private fun recordHistory(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        _state.update {
            it.copy(history = (listOf(trimmed) + it.history.filterNot { entry -> entry == trimmed }).take(20))
        }
        viewModelScope.launch { store.saveSearchHistory(_state.value.history) }
    }

    /** Résout un résultat de recherche en Track lisible (audio, ou vidéo+audio si disponible). */
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
