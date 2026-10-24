package com.soundflow.mobile.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.soundflow.core.player.EqualizerPreset
import com.soundflow.core.player.PlayerController
import com.soundflow.core.player.Track
import com.soundflow.mobile.data.ListeningStats
import com.soundflow.mobile.data.LocalStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Scopé à l'Activity (voir SoundFlowNavHost) pour que le lecteur et son état
 * (morceau en cours, position, historique...) survivent à la navigation entre onglets.
 */
@UnstableApi
class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val controller = PlayerController(application)
    private val store = LocalStore(application)

    val state = controller.state
    val player = controller.player
    val playbackMode = controller.playbackMode

    val listeningStats: StateFlow<ListeningStats> = store.listeningStatsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ListeningStats(),
    )

    // Historique d'écoute — session uniquement pour l'instant.
    private val _history = MutableStateFlow<List<Track>>(emptyList())
    val history: StateFlow<List<Track>> = _history.asStateFlow()

    init {
        controller.connect()

        // Enregistre en continu le temps d'écoute effectif quand la musique tourne
        viewModelScope.launch {
            while (true) {
                delay(5_000)
                if (state.value.isPlaying) {
                    store.recordListeningSeconds(5L)
                }
            }
        }
    }

    fun playTrack(track: Track) {
        controller.playTrack(track)
        recordHistory(track)
    }

    fun playQueue(tracks: List<Track>, startIndex: Int = 0) {
        controller.playQueue(tracks, startIndex)
        tracks.getOrNull(startIndex)?.let { recordHistory(it) }
    }

    private fun recordHistory(track: Track) {
        _history.update { current -> (listOf(track) + current.filterNot { it.id == track.id }).take(30) }
    }

    fun togglePlayPause() = controller.togglePlayPause()

    fun seekTo(positionMs: Long) = controller.seekTo(positionMs)

    fun clearError() = controller.clearError()

    fun setSleepTimer(durationMs: Long?) = controller.setSleepTimer(durationMs)

    fun applyEqualizerPreset(preset: EqualizerPreset) = controller.applyEqualizerPreset(preset)

    fun toggleShuffle() = controller.toggleShuffle()

    fun cycleRepeatMode() = controller.cycleRepeatMode()

    fun skipToNext() = controller.skipToNext()

    fun skipToPrevious() = controller.skipToPrevious()

    override fun onCleared() {
        controller.release()
        super.onCleared()
    }
}
