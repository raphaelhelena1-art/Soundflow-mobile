package com.soundflow.mobile

import android.app.Application
import android.util.Log
import com.soundflow.core.extraction.NewPipeInitializer
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL

class SoundFlowApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // 1. Initialisation de NewPipeExtractor (recherche & flux YouTube)
        runCatching {
            NewPipeInitializer.init()
            Log.d("SoundFlow", "NewPipeExtractor initialisé avec succès !")
        }.onFailure { e ->
            Log.e("SoundFlow", "Échec de l'initialisation de NewPipeExtractor", e)
        }

        // 2. Initialisation de YoutubeDL (moteur yt-dlp)
        runCatching {
            YoutubeDL.getInstance().init(this)
            Log.d("SoundFlow", "Moteur yt-dlp initialisé avec succès !")
        }.onFailure { e ->
            Log.e("SoundFlow", "Échec de l'initialisation de YoutubeDL (yt-dlp)", e)
        }

        // 3. Initialisation de FFmpeg
        runCatching {
            FFmpeg.getInstance().init(this)
            Log.d("SoundFlow", "Extension FFmpeg initialisée avec succès !")
        }.onFailure { e ->
            Log.e("SoundFlow", "Échec de l'initialisation de FFmpeg", e)
        }
    }
}
