package com.soundflow.mobile.downloads

import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.soundflow.core.download.AudioExtractor
import com.soundflow.mobile.data.LocalStore
import com.soundflow.mobile.playlists.PlaylistsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@UnstableApi
class DownloadsViewModel(application: Application) : AndroidViewModel(application) {

    private val store = LocalStore(application)
    private val audioExtractor = AudioExtractor(application)

    private val _downloads = MutableStateFlow<List<DownloadRecord>>(emptyList())
    val downloads: StateFlow<List<DownloadRecord>> = _downloads.asStateFlow()

    init {
        // Ecoute réactive des mises à jour du DataStore en temps réel
        viewModelScope.launch {
            store.downloadsFlow.collect { list ->
                _downloads.value = list
            }
        }
        viewModelScope.launch { pollPendingDownloads() }
    }

    private suspend fun pollPendingDownloads() {
        while (true) {
            delay(1_500)
            runCatching {
                val pending = _downloads.value.filter { it.localUri == null && it.downloadManagerId != -1L }
                if (pending.isNotEmpty()) {
                    for (record in pending) {
                        val status = queryStatus(record.downloadManagerId) ?: continue
                        when (status) {
                            DownloadManager.STATUS_SUCCESSFUL -> handleDownloadSuccess(record)
                            DownloadManager.STATUS_FAILED -> {
                                _downloads.update { list -> list.filterNot { it.downloadManagerId == record.downloadManagerId } }
                                persist()
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun queryStatus(downloadManagerId: Long): Int? = withContext(Dispatchers.IO) {
        runCatching {
            val manager = getApplication<Application>().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val query = DownloadManager.Query().setFilterById(downloadManagerId)
            manager.query(query)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    if (statusIndex >= 0) return@use cursor.getInt(statusIndex)
                }
                null
            }
        }.getOrNull()
    }

    private suspend fun handleDownloadSuccess(record: DownloadRecord) {
        val manager = getApplication<Application>().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadedUri = withContext(Dispatchers.IO) {
            runCatching { manager.getUriForDownloadedFile(record.downloadManagerId) }.getOrNull()
        } ?: return

        if (!record.needsAudioExtraction) {
            updateRecord(record.downloadManagerId) { it.copy(localUri = downloadedUri.toString()) }
            return
        }

        val safeName = record.title.replace(Regex("[^A-Za-z0-9._ -]"), "_").take(80)
        val outputDir = File(
            getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_MUSIC),
            "SoundFlow",
        ).apply { mkdirs() }
        val outputFile = File(outputDir, "$safeName-${record.downloadManagerId}.m4a")

        val success = audioExtractor.extractAudio(downloadedUri.toString(), outputFile.absolutePath)

        if (success) {
            withContext(Dispatchers.IO) { runCatching { manager.remove(record.downloadManagerId) } }
            updateRecord(record.downloadManagerId) {
                it.copy(localUri = Uri.fromFile(outputFile).toString(), needsAudioExtraction = false)
            }
        } else {
            updateRecord(record.downloadManagerId) {
                it.copy(localUri = downloadedUri.toString(), needsAudioExtraction = false)
            }
        }
    }

    private fun updateRecord(downloadManagerId: Long, transform: (DownloadRecord) -> DownloadRecord) {
        _downloads.update { list -> list.map { if (it.downloadManagerId == downloadManagerId) transform(it) else it } }
        persist()
    }

    fun recordDownload(
        id: String,
        title: String,
        artist: String,
        artworkUrl: String?,
        isAudio: Boolean,
        downloadManagerId: Long,
        needsAudioExtraction: Boolean = false,
    ) {
        _downloads.update { current ->
            val existing = current.filterNot { it.id == id }
            listOf(
                DownloadRecord(
                    id = id,
                    title = title,
                    artist = artist,
                    artworkUrl = artworkUrl,
                    isAudio = isAudio,
                    timestampMs = System.currentTimeMillis(),
                    downloadManagerId = downloadManagerId,
                    needsAudioExtraction = needsAudioExtraction,
                ),
            ) + existing
        }
        persist()
    }

    /** Marque manuellement un téléchargement comme terminé (ex: depuis YoutubeDownloadWorker). */
    fun onDownloadCompleted(trackId: String, localUri: String) {
        _downloads.update { list ->
            list.map { record ->
                if (record.id == trackId) {
                    record.copy(localUri = localUri, needsAudioExtraction = false)
                } else {
                    record
                }
            }
        }
        persist()
    }

    /** Importe des fichiers audio ou vidéo locaux depuis l'appareil dans l'application. */
    fun importLocalFiles(uris: List<Uri>, playlistsViewModel: PlaylistsViewModel) {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            val contentResolver = app.contentResolver

            for ((index, uri) in uris.withIndex()) {
                runCatching {
                    var fileName = "local_file_${System.currentTimeMillis()}"
                    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (nameIndex >= 0) {
                                cursor.getString(nameIndex)?.let { fileName = it }
                            }
                        }
                    }

                    val mimeType = contentResolver.getType(uri).orEmpty()
                    val isAudio = mimeType.startsWith("audio/") ||
                        fileName.endsWith(".mp3", ignoreCase = true) ||
                        fileName.endsWith(".m4a", ignoreCase = true) ||
                        fileName.endsWith(".flac", ignoreCase = true) ||
                        fileName.endsWith(".wav", ignoreCase = true) ||
                        fileName.endsWith(".aac", ignoreCase = true) ||
                        fileName.endsWith(".ogg", ignoreCase = true)

                    val subDir = if (isAudio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES
                    val targetDir = File(app.getExternalFilesDir(subDir), "SoundFlow").apply { mkdirs() }

                    val safeTitle = fileName.substringBeforeLast(".").replace(Regex("[^A-Za-z0-9._ -]"), "_").take(80)
                    val rawExt = fileName.substringAfterLast(".", if (isAudio) "m4a" else "mp4")
                    val timestamp = System.currentTimeMillis()
                    val targetFile = File(targetDir, "imported_${timestamp}_$index.$rawExt")

                    contentResolver.openInputStream(uri)?.use { input ->
                        targetFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }

                    if (targetFile.exists() && targetFile.length() > 0) {
                        val trackId = "local_${timestamp}_$index"
                        val localUriString = Uri.fromFile(targetFile).toString()

                        recordDownload(
                            id = trackId,
                            title = safeTitle,
                            artist = "Fichier local",
                            artworkUrl = null,
                            isAudio = isAudio,
                            downloadManagerId = -1L,
                            needsAudioExtraction = false,
                        )
                        onDownloadCompleted(trackId, localUriString)
                        playlistsViewModel.markDownloaded(trackId)
                    }
                }
            }
        }
    }

    /** Annule un téléchargement en cours. */
    fun cancelDownload(record: DownloadRecord) {
        val manager = getApplication<Application>().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        runCatching { manager.remove(record.downloadManagerId) }
        _downloads.update { it.filterNot { r -> r.downloadManagerId == record.downloadManagerId } }
        persist()
    }

    /** Supprime un téléchargement et retire sa copie de travail du stockage de l'app si présente. */
    fun deleteDownload(trackId: String, playlistsViewModel: PlaylistsViewModel) {
        val recordToDelete = _downloads.value.firstOrNull { it.id == trackId }
        val localUriString = recordToDelete?.localUri

        _downloads.update { it.filterNot { record -> record.id == trackId } }
        persist()
        playlistsViewModel.unmarkDownloaded(trackId)
        playlistsViewModel.removeFromOfflinePlaylists(trackId)

        if (localUriString != null) {
            viewModelScope.launch(Dispatchers.IO) {
                runCatching {
                    val fileUri = Uri.parse(localUriString)
                    if (fileUri.scheme == "file") {
                        val file = File(fileUri.path.orEmpty())
                        val appFilesDir = getApplication<Application>().getExternalFilesDir(null)
                        if (appFilesDir != null && file.canonicalPath.startsWith(appFilesDir.canonicalPath) && file.exists()) {
                            file.delete()
                        }
                    }
                }
            }
        }
    }

    private fun persist() {
        viewModelScope.launch { store.saveDownloads(_downloads.value) }
    }
}
