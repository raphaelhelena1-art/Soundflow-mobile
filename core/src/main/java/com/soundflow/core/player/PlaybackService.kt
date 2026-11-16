package com.soundflow.core.player

import android.media.audiofx.LoudnessEnhancer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Service Android qui garde le lecteur (ExoPlayer) vivant en arrière-plan,
 * avec une notification système (play/pause/next) et gère le cycle de vie
 * de la MediaSession — indispensable pour que la musique continue de jouer
 * écran éteint ou app en arrière-plan.
 */
@UnstableApi
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

    override fun onCreate() {
        super.onCreate()

        // Les URLs de flux YouTube (googlevideo.com) sont sensibles au User-Agent utilisé
        // pour les requêtes : si ExoPlayer utilise un User-Agent différent de celui qui a
        // servi à extraire le flux, le CDN peut refuser silencieusement la requête
        // (buffering infini sans erreur claire). On aligne les deux.
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)
            // Le CDN vidéo de YouTube (googlevideo.com) refuse souvent les requêtes sans ces
            // en-têtes, ce qui provoquait l'erreur générique "Source error" côté ExoPlayer.
            .setDefaultRequestProperties(
                mapOf(
                    "Referer" to "https://www.youtube.com/",
                    "Origin" to "https://www.youtube.com",
                ),
            )
        val dataSourceFactory = DefaultDataSource.Factory(this, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(this).setDataSourceFactory(dataSourceFactory)

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .build().apply {
                repeatMode = Player.REPEAT_MODE_ALL
            }

        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (audioSessionId != 0) {
                    runCatching {
                        loudnessEnhancer?.release()
                        loudnessEnhancer = LoudnessEnhancer(audioSessionId).apply {
                            setTargetGain(500) // Gain de normalisation pour égaliser le volume sonore entre les morceaux
                            enabled = true
                        }
                    }
                }
            }
        })

        val sessionCallback = object : MediaSession.Callback {
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
            ): MediaSession.ConnectionResult {
                val connectionResult = super.onConnect(session, controller)
                val sessionCommands = connectionResult.availableSessionCommands
                val playerCommands = connectionResult.availablePlayerCommands
                    .buildUpon()
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .build()
                return MediaSession.ConnectionResult.accept(sessionCommands, playerCommands)
            }
        }

        mediaSession = MediaSession.Builder(this, player)
            .setCallback(sessionCallback)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        loudnessEnhancer?.run {
            runCatching { release() }
            loudnessEnhancer = null
        }
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
