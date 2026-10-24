package com.soundflow.core.player

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
            .build()
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
