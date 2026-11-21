package com.soundflow.core.player

import android.content.ComponentName
import android.content.Context
import android.media.audiofx.Equalizer
import android.net.Uri
import java.io.File
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.soundflow.core.extraction.YouTubeRepository
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
import kotlinx.coroutines.withContext

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

    private val youtubeRepository = YouTubeRepository()
    private var mediaController: MediaController? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var positionUpdateJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var playJob: Job? = null
    private var preloadJob: Job? = null
    private var equalizer: Equalizer? = null

    private val preloadedTracks = java.util.concurrent.ConcurrentHashMap<String, Track>()

    var localResolver: (suspend (String) -> String?)? = null

    private var queue: List<Track> = emptyList()
    private var currentIndex: Int = 0
    private var shuffleOrder: List<Int> = emptyList()

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val _player = MutableStateFlow<Player?>(null)
    val player: StateFlow<Player?> = _player.asStateFlow()

    private val _playbackMode = MutableStateFlow(PlaybackMode.SHUFFLE)
    val playbackMode: StateFlow<PlaybackMode> = _playbackMode.asStateFlow()

    private var isCrossfading = false

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

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // Dans SoundFlow, la navigation dans la file (skipToNext, skipToPrevious, handleTrackEnded)
            // est gérée dans l'état Kotlin (queue, currentIndex). Les MediaItems étant résolus et envoyés
            // un par un à ExoPlayer, on ne réinitialise pas currentIndex ici.
        }

        override fun onPlayerError(error: PlaybackException) {
            val message = error.cause?.message ?: error.message ?: error.errorCodeName
            _state.update { it.copy(isPlaying = false, isBuffering = false, errorMessage = message) }
            mediaController?.stop()
            mediaController?.clearMediaItems()
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
        preloadedTracks.clear()
        queue = listOf(track)
        currentIndex = 0
        shuffleOrder = emptyList()
        playCurrentQueueItem()
    }

    fun playQueue(tracks: List<Track>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        preloadedTracks.clear()
        queue = tracks
        currentIndex = startIndex.coerceIn(tracks.indices)
        if (_playbackMode.value == PlaybackMode.SHUFFLE) regenerateShuffleOrder()
        playCurrentQueueItem()
    }

    private val _crossfadeSeconds = MutableStateFlow(0f)
    val crossfadeSeconds: StateFlow<Float> = _crossfadeSeconds.asStateFlow()

    fun setCrossfadeSeconds(seconds: Float) {
        _crossfadeSeconds.value = seconds
    }

    private fun performCrossfadeAndSwitch(switchAction: () -> Unit) {
        val controller = mediaController ?: run { switchAction(); return }
        val fadeSec = _crossfadeSeconds.value
        if (fadeSec <= 0f || isCrossfading) {
            switchAction()
            return
        }
        isCrossfading = true
        scope.launch {
            val fadeHalfMs = ((fadeSec * 1000f) / 2f).toLong().coerceAtLeast(100L)
            val steps = 25
            val stepDelay = (fadeHalfMs / steps).coerceAtLeast(15L)
            for (i in steps downTo 0) {
                controller.volume = i.toFloat() / steps
                delay(stepDelay)
            }
            switchAction()
            controller.volume = 0f
            for (i in 1..steps) {
                controller.volume = i.toFloat() / steps
                delay(stepDelay)
            }
            controller.volume = 1f
            isCrossfading = false
        }
    }

    fun skipToNext() {
        if (queue.size <= 1) return
        performCrossfadeAndSwitch {
            currentIndex = nextIndex()
            playCurrentQueueItem()
        }
    }

    fun skipToPrevious() {
        if (queue.size <= 1) return
        performCrossfadeAndSwitch {
            currentIndex = previousIndex()
            playCurrentQueueItem()
        }
    }

    private fun playCurrentQueueItem() {
        val track = queue.getOrNull(currentIndex) ?: return
        val controller = mediaController ?: return

        playJob?.cancel()

        if (controller.isPlaying) {
            controller.pause()
        }

        controller.repeatMode = if (_playbackMode.value == PlaybackMode.REPEAT_ONE) {
            Player.REPEAT_MODE_ONE
        } else {
            Player.REPEAT_MODE_OFF
        }

        _state.update {
            it.copy(
                currentTrack = track,
                isBuffering = true,
                errorMessage = null,
            )
        }

        playJob = scope.launch {
            val preloaded = preloadedTracks[track.id] ?: preloadedTracks[track.mediaUrl]
            val resolvedTrack = preloaded ?: resolvePlayableTrack(track)

            if (resolvedTrack == null) {
                controller.stop()
                controller.clearMediaItems()
                _state.update {
                    it.copy(
                        isBuffering = false,
                        isPlaying = false,
                        errorMessage = "Impossible de lire le morceau : extraction du flux audio échouée.",
                    )
                }
                return@launch
            }

            _state.update { it.copy(currentTrack = resolvedTrack, isBuffering = false) }

            val item = buildMediaItem(resolvedTrack)
            controller.setMediaItem(item)
            controller.prepare()
            controller.play()

            preloadNextTrack()
        }
    }

    private fun preloadNextTrack() {
        preloadJob?.cancel()
        if (queue.size <= 1) return

        val nextIdx = nextIndex()
        val nextTrack = queue.getOrNull(nextIdx) ?: return

        if (preloadedTracks.containsKey(nextTrack.id) ||
            nextTrack.isDownloaded ||
            nextTrack.mediaUrl.startsWith("file:", ignoreCase = true) ||
            nextTrack.mediaUrl.startsWith("content:", ignoreCase = true)
        ) {
            return
        }

        preloadJob = scope.launch {
            val resolved = resolvePlayableTrack(nextTrack)
            if (resolved != null) {
                preloadedTracks[nextTrack.id] = resolved
                preloadedTracks[nextTrack.mediaUrl] = resolved
            }
        }
    }

    private suspend fun resolvePlayableTrack(track: Track): Track? = withContext(Dispatchers.IO) {
        val isFileScheme = track.mediaUrl.startsWith("file:", ignoreCase = true)
        val isContentScheme = track.mediaUrl.startsWith("content:", ignoreCase = true)

        if (isFileScheme) {
            val filePath = Uri.parse(track.mediaUrl).path
            if (filePath != null && File(filePath).exists() && File(filePath).length() > 0) {
                return@withContext track
            }
        } else if (isContentScheme) {
            return@withContext track
        } else if (track.isDownloaded) {
            val filePath = Uri.parse(track.mediaUrl).path
            if (filePath != null && File(filePath).exists() && File(filePath).length() > 0) {
                return@withContext track
            }
        }

        // Tente la résolution via le résolveur local (si téléchargé sur le téléphone)
        val resolvedLocalUri = localResolver?.invoke(track.id) ?: localResolver?.invoke(track.mediaUrl)
        if (!resolvedLocalUri.isNullOrBlank()) {
            val localPath = Uri.parse(resolvedLocalUri).path
            if (localPath != null && File(localPath).exists() && File(localPath).length() > 0) {
                return@withContext track.copy(
                    mediaUrl = resolvedLocalUri,
                    isDownloaded = true,
                )
            }
        }

        val videoUrl = when {
            track.mediaUrl.contains("youtube.com") || track.mediaUrl.contains("youtu.be") -> track.mediaUrl
            track.id.contains("youtube.com") || track.id.contains("youtu.be") -> track.id
            track.id.startsWith("http") -> track.id
            else -> "https://www.youtube.com/watch?v=${track.id}"
        }

        val resolved = runCatching {
            val streams = youtubeRepository.resolvePlaybackStreams(videoUrl)
            val playableUrl = streams.combinedVideoUrl ?: streams.audioUrl
            if (playableUrl != null) {
                track.copy(
                    mediaUrl = playableUrl,
                    hasVideo = streams.combinedVideoUrl != null,
                )
            } else null
        }.getOrNull()

        if (resolved != null) return@withContext resolved

        runCatching {
            val bestMatch = youtubeRepository.searchBestMatch(
                title = track.title,
                artist = track.artist,
                durationMs = 0L,
            )
            if (bestMatch != null) {
                val streams = youtubeRepository.resolvePlaybackStreams(bestMatch.id)
                val playableUrl = streams.combinedVideoUrl ?: streams.audioUrl
                if (playableUrl != null) {
                    track.copy(
                        id = bestMatch.id,
                        mediaUrl = playableUrl,
                        hasVideo = streams.combinedVideoUrl != null,
                    )
                } else null
            } else null
        }.getOrNull()
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
            mediaController?.repeatMode = Player.REPEAT_MODE_OFF
        } else {
            regenerateShuffleOrder()
            _playbackMode.update { PlaybackMode.SHUFFLE }
            mediaController?.repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    /** Cycle Désactivé/Aléatoire → Toutes → Une seule → Désactivé. */
    fun cycleRepeatMode() {
        _playbackMode.update { current ->
            val nextMode = when (current) {
                PlaybackMode.REPEAT_ALL -> PlaybackMode.REPEAT_ONE
                PlaybackMode.REPEAT_ONE -> PlaybackMode.SEQUENTIAL
                PlaybackMode.SEQUENTIAL -> PlaybackMode.REPEAT_ALL
                PlaybackMode.SHUFFLE -> PlaybackMode.REPEAT_ALL
            }
            val targetRepeatMode = if (nextMode == PlaybackMode.REPEAT_ONE) {
                Player.REPEAT_MODE_ONE
            } else {
                Player.REPEAT_MODE_OFF
            }
            mediaController?.repeatMode = targetRepeatMode
            nextMode
        }
    }

    private fun regenerateShuffleOrder() {
        if (queue.isEmpty()) {
            shuffleOrder = emptyList()
            return
        }
        if (currentIndex in queue.indices) {
            val remaining = queue.indices.filterNot { it == currentIndex }.shuffled()
            shuffleOrder = listOf(currentIndex) + remaining
        } else {
            shuffleOrder = queue.indices.shuffled()
        }
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
                performCrossfadeAndSwitch {
                    currentIndex = nextIndex()
                    playCurrentQueueItem()
                }
            }
            PlaybackMode.SHUFFLE -> {
                performCrossfadeAndSwitch {
                    currentIndex = nextIndex()
                    playCurrentQueueItem()
                }
            }
            PlaybackMode.SEQUENTIAL -> {
                val isLast = currentIndex >= queue.size - 1
                if (!isLast) {
                    performCrossfadeAndSwitch {
                        currentIndex = nextIndex()
                        playCurrentQueueItem()
                    }
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
                val controller = mediaController
                val pos = controller?.currentPosition ?: 0L
                val dur = controller?.duration ?: 0L
                _state.update { it.copy(positionMs = pos) }

                val fadeSec = _crossfadeSeconds.value
                if (fadeSec > 0f && !isCrossfading && controller?.isPlaying == true && dur > 5000L && _playbackMode.value != PlaybackMode.REPEAT_ONE) {
                    val fadeMs = (fadeSec * 1000f).toLong()
                    val remainingMs = dur - pos
                    if (remainingMs in 100L..fadeMs) {
                        skipToNext()
                    }
                }

                delay(200)
            }
        }
    }

    fun release() {
        playJob?.cancel()
        preloadJob?.cancel()
        preloadedTracks.clear()
        positionUpdateJob?.cancel()
        sleepTimerJob?.cancel()
        equalizer?.release()
        mediaController?.release()
        mediaController = null
    }
}
