package com.soundflow.mobile

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.soundflow.core.extraction.YouTubeRepository
import com.soundflow.mobile.data.LocalStore
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request as OkHttpRequest
import java.io.File
import java.util.concurrent.TimeUnit

class YoutubeDownloadWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val trackId = inputData.getString("TRACK_ID") ?: return Result.failure()
        val title = inputData.getString("TITLE") ?: "Unknown"
        val downloadVideo = inputData.getBoolean("DOWNLOAD_VIDEO", false)

        val videoUrl = when {
            trackId.startsWith("http://") || trackId.startsWith("https://") -> trackId
            trackId.startsWith("/") -> "https://www.youtube.com$trackId"
            else -> "https://www.youtube.com/watch?v=$trackId"
        }

        val safeName = title.replace(Regex("[^A-Za-z0-9._ -]"), "_").take(80)
        val extension = if (downloadVideo) "mp4" else "m4a"

        val subDir = if (downloadVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_MUSIC
        val soundFlowDir = File(applicationContext.getExternalFilesDir(subDir), "SoundFlow").apply { mkdirs() }
        val outputFile = File(soundFlowDir, "$safeName.$extension")

        val store = LocalStore(applicationContext)

        return withContext(Dispatchers.IO) {
            // Tentative 1 : yt-dlp
            runCatching {
                val request = YoutubeDLRequest(videoUrl)
                request.addOption("--user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36")
                request.addOption("--no-mtime")
                request.addOption("--no-playlist")
                request.addOption("--no-check-certificates")

                if (downloadVideo) {
                    request.addOption("-f", "b[ext=mp4]/best[ext=mp4]/b/best")
                } else {
                    request.addOption("-f", "ba[ext=m4a]/ba[ext=aac]/ba/best")
                }
                request.addOption("-o", outputFile.absolutePath)

                Log.d("SoundFlow", "Début du téléchargement yt-dlp pour : $title ($videoUrl)")
                YoutubeDL.getInstance().execute(request) { progress, etaInSeconds, _ ->
                    Log.d("SoundFlow", "Progression yt-dlp $title : $progress% (ETA: ${etaInSeconds}s)")
                }
            }

            var downloadedFile = findDownloadedFile(soundFlowDir, safeName, extension)

            if (downloadedFile != null) {
                Log.d("SoundFlow", "Téléchargement yt-dlp réussi pour : $title -> ${downloadedFile.absolutePath}")
                val fileUri = Uri.fromFile(downloadedFile).toString()
                store.markDownloadCompleted(trackId, fileUri)
                return@withContext Result.success()
            }

            // Tentative 2 : Repli vers OkHttp direct stream
            Log.w("SoundFlow", "yt-dlp n'a pas produit de fichier valide, bascule vers OkHttp pour : $title")
            val okHttpSuccess = downloadWithOkHttpFallback(videoUrl, downloadVideo, outputFile)
            downloadedFile = findDownloadedFile(soundFlowDir, safeName, extension)

            if (okHttpSuccess && downloadedFile != null) {
                Log.d("SoundFlow", "Téléchargement OkHttp réussi pour : $title -> ${downloadedFile.absolutePath}")
                val fileUri = Uri.fromFile(downloadedFile).toString()
                store.markDownloadCompleted(trackId, fileUri)
                Result.success()
            } else {
                Log.e("SoundFlow", "Tous les modes de téléchargement ont échoué pour : $title")
                store.removeDownload(trackId)
                Result.failure()
            }
        }
    }

    private fun findDownloadedFile(directory: File, safeName: String, extension: String): File? {
        val exact = File(directory, "$safeName.$extension")
        if (exact.exists() && exact.length() > 0) return exact

        return directory.listFiles()?.firstOrNull { file ->
            file.isFile &&
                file.length() > 0 &&
                !file.name.endsWith(".part") &&
                (file.name.startsWith(safeName) || file.name.contains(safeName))
        }
    }

    private suspend fun downloadWithOkHttpFallback(
        videoUrl: String,
        downloadVideo: Boolean,
        outputFile: File,
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val repository = YouTubeRepository()
            val streams = repository.getDownloadStreams(videoUrl)
            val streamUrl = if (downloadVideo) {
                streams.videoUrl ?: streams.audioUrl
            } else {
                streams.audioUrl ?: streams.videoUrl
            } ?: return@runCatching false

            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()

            val request = OkHttpRequest.Builder()
                .url(streamUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching false
                val body = response.body ?: return@runCatching false
                body.byteStream().use { input ->
                    outputFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
            outputFile.exists() && outputFile.length() > 0
        }.getOrDefault(false)
    }
}
