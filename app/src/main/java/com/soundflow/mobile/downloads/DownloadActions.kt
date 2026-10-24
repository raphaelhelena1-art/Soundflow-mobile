package com.soundflow.mobile.downloads

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.soundflow.core.extraction.DownloadStreams
import com.soundflow.mobile.R
import com.soundflow.mobile.YoutubeDownloadWorker
import com.soundflow.mobile.playlists.PlaylistsViewModel

/**
 * Centralise le lancement d'un téléchargement (audio ou vidéo) via le moteur yt-dlp / FFmpeg
 * de YoutubeDownloadWorker pour garantir la fiabilité, le muxing audio/vidéo et l'extraction.
 */
@OptIn(UnstableApi::class)
suspend fun performDownload(
    context: Context,
    streams: DownloadStreams,
    id: String,
    title: String,
    artist: String,
    artworkUrl: String?,
    downloadVideo: Boolean,
    downloadsViewModel: DownloadsViewModel,
    playlistsViewModel: PlaylistsViewModel,
    onMessage: suspend (Int) -> Unit,
) {
    // 1. Préparation des données complètes pour YoutubeDownloadWorker (yt-dlp + FFmpeg)
    val inputData = Data.Builder()
        .putString("TRACK_ID", id)
        .putString("TITLE", title)
        .putString("ARTIST", artist)
        .putString("ARTWORK_URL", artworkUrl)
        .putBoolean("DOWNLOAD_VIDEO", downloadVideo)
        .build()

    // 2. Création et envoi de la tâche WorkManager accélérée
    val downloadWorkRequest = OneTimeWorkRequest.Builder(YoutubeDownloadWorker::class.java)
        .setInputData(inputData)
        .addTag("download_$id")
        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        .build()

    // 3. Enregistrement initial dans le ViewModel & marqueur de playlist
    downloadsViewModel.recordDownload(
        id = id,
        title = title,
        artist = artist,
        artworkUrl = artworkUrl,
        isAudio = !downloadVideo,
        downloadManagerId = -1L,
        needsAudioExtraction = false,
    )

    playlistsViewModel.markDownloaded(id)

    // 4. Lancement de la tâche
    WorkManager.getInstance(context).enqueue(downloadWorkRequest)

    // 5. Notification
    onMessage(R.string.download_started)
}
