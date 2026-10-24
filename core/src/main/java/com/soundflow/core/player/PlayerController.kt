package com.soundflow.core.player

import android.content.ComponentName
import android.content.Context
import android.media.audiofx.Equalizer
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class EqualizerPreset { FLAT, BASS_BOOST, TREBLE_BOOST, VOCAL }

/**
 * Point d'entrée unique pour l'UI : joue un morceau ou une file (playlist), met en pause,
 * gère le mode de lecture (aléatoire / répétition toutes / répétition une seule — mutuellement
 * exclusifs, activer l'un désactive l'autre) et précédent/suivant.
 *
 * La file et le mode sont gérés entièrement côté app (pas via shuffleModeEnabled/repeatMode
 * d'ExoPlayer), pour un contrôle total et prévisible du comportement.
 */
@UnstableApi
class PlayerController(private val context: Context) {

    private var mediaController: MediaController? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var positionUpdateJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var equalizer: Equalizer? = null

    private var queue: List<Track> = emptyList()
    private var currentIndex: Int = 0
    private var shuffleOrder: List<Int> = emptyList()

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val _player = MutableStateFlow<Player?>(null)
    val player: StateFlow<Player?> = _player.asStateFlow()

    private val _playbackMode = MutableStateFlow(PlaybackMode.SEQUENTIAL)
    val playbackMode: StateFlow<PlaybackMode> = _playbackMode.asStateFlow()

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(isPlaying = isPlaying) }
            if (isPlaying) {
                startPositionUpdates()
                setupEqualizerIfNeeded()
            } else {
                positionUpdateJob?.cancel()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val duration = mediaController?.duration ?: 0L
            _state.update {
                it.copy(
                    durationMs = duration.coerceAtLeast(0),
                    isBuffering = playbackState == Player.STATE_BUFFERING,
                )
            }
            if (playbackState == Player.STATE_ENDED) {
                handleTrackEnded()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val message = error.cause?.message ?: error.message ?: error.errorCodeName
            _state.update { it.copy(isPlaying = false, isBuffering = false, errorMessage = message) }
        }
    }

    fun connect() {
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture.addListener(
            {
                mediaController = controllerFuture.get().also { it.addListener(playerListener) }
                _player.update { mediaController }
            },
            MoreExecutors.directExecutor(),
        )
    }

    fun playTrack(track: Track) {
        queue = listOf(track)
        currentIndex = 0
        shuffleOrder = emptyList()
        playCurrentQueueItem()
    }

    fun playQueue(tracks: List<Track>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        queue = tracks
        currentIndex = startIndex.coerceIn(tracks.indices)
        if (_playbackMode.value == PlaybackMode.SHUFFLE) regenerateShuffleOrder()
        playCurrentQueueItem()
    }

    fun skipToNext() {
        if (queue.size <= 1) return
        currentIndex = nextIndex()
        playCurrentQueueItem()
    }

    fun skipToPrevious() {
        if (queue.size <= 1) return
        currentIndex = previousIndex()
        playCurrentQueueItem()
    }

    private fun playCurrentQueueItem() {
        val track = queue.getOrNull(currentIndex) ?: return
        val controller = mediaController ?: return
        controller.setMediaItem(buildMediaItem(track))
        controller.prepare()
        controller.play()
        _state.update { it.copy(currentTrack = track, errorMessage = null) }
    }

    private fun buildMediaItem(track: Track): MediaItem = MediaItem.Builder()
        .setUri(track.mediaUrl)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.artist)
                .apply { track.artworkUrl?.let { setArtworkUri(Uri.parse(it)) } }
                .build(),
        )
        .build()

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) controller.pause() else controller.play()
    }

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    /** Bascule aléatoire : l'activer désactive toute répétition en cours. Le réactiver l'annule. */
    fun toggleShuffle() {
        if (_playbackMode.value == PlaybackMode.SHUFFLE) {
            _playbackMode.update { PlaybackMode.SEQUENTIAL }
        } else {
            regenerateShuffleOrder()
            _playbackMode.update { PlaybackMode.SHUFFLE }
        }
    }

    /** Cycle Désactivé/Aléatoire → Toutes → Une seule → Désactivé — désactive l'aléatoire au passage. */
    fun cycleRepeatMode() {
        _playbackMode.update {
            when (it) {
                PlaybackMode.REPEAT_ALL -> PlaybackMode.REPEAT_ONE
                PlaybackMode.REPEAT_ONE -> PlaybackMode.SEQUENTIAL
                else -> PlaybackMode.REPEAT_ALL
            }
        }
    }

    private fun regenerateShuffleOrder() {
        shuffleOrder = queue.indices.shuffled()
    }

    private fun nextIndex(): Int {
        if (_playbackMode.value == PlaybackMode.SHUFFLE && shuffleOrder.isNotEmpty()) {
            val pos = shuffleOrder.indexOf(currentIndex)
            val nextPos = (pos + 1) % shuffleOrder.size
            return shuffleOrder[nextPos]
        }
        val next = currentIndex + 1
        return if (next >= queue.size) 0 else next
    }

    private fun previousIndex(): Int {
        if (_playbackMode.value == PlaybackMode.SHUFFLE && shuffleOrder.isNotEmpty()) {
            val pos = shuffleOrder.indexOf(currentIndex)
            val prevPos = if (pos - 1 < 0) shuffleOrder.size - 1 else pos - 1
            return shuffleOrder[prevPos]
        }
        val prev = currentIndex - 1
        return if (prev < 0) queue.size - 1 else prev
    }

    private fun handleTrackEnded() {
        if (queue.isEmpty()) return
        when (_playbackMode.value) {
            PlaybackMode.REPEAT_ONE -> playCurrentQueueItem()
            PlaybackMode.REPEAT_ALL -> {
                currentIndex = nextIndex()
                playCurrentQueueItem()
            }
            PlaybackMode.SEQUENTIAL, PlaybackMode.SHUFFLE -> {
                val isLast = if (_playbackMode.value == PlaybackMode.SHUFFLE && shuffleOrder.isNotEmpty()) {
                    shuffleOrder.indexOf(currentIndex) >= shuffleOrder.size - 1
                } else {
                    currentIndex >= queue.size - 1
                }
                if (!isLast) {
                    currentIndex = nextIndex()
                    playCurrentQueueItem()
                }
            }
        }
    }

    fun setSleepTimer(durationMs: Long?) {
        sleepTimerJob?.cancel()
        if (durationMs == null) return
        sleepTimerJob = scope.launch {
            delay(durationMs)
            mediaController?.pause()
        }
    }

    fun applyEqualizerPreset(preset: EqualizerPreset) {
        val eq = equalizer ?: return
        val levels = when (preset) {
            EqualizerPreset.FLAT -> shortArrayOf(0, 0, 0, 0, 0)
            EqualizerPreset.BASS_BOOST -> shortArrayOf(1200, 800, 0, 0, 0)
            EqualizerPreset.TREBLE_BOOST -> shortArrayOf(0, 0, 0, 800, 1200)
            EqualizerPreset.VOCAL -> shortArrayOf(-200, 200, 600, 200, -200)
        }
        val bandCount = eq.numberOfBands.toInt()
        for (band in 0 until bandCount) {
            eq.setBandLevel(band.toShort(), levels.getOrElse(band) { 0 })
        }
    }

    private fun setupEqualizerIfNeeded() {
        if (equalizer != null) return
        equalizer = runCatching { Equalizer(0, 0).apply { enabled = true } }.getOrNull()
    }

    private fun startPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = scope.launch {
            while (true) {
                _state.update { it.copy(positionMs = mediaController?.currentPosition ?: 0L) }
                delay(500)
            }
        }
    }

    fun release() {
        positionUpdateJob?.cancel()
        sleepTimerJob?.cancel()
        equalizer?.release()
        mediaController?.release()
        mediaController = null
    }
}
