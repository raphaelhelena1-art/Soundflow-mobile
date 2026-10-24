package com.soundflow.core.download

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Extrait la piste audio d'un fichier vidéo (utile pour "télécharger en audio" quand YouTube ne
 * fournit pas de flux audio seul téléchargable directement : on télécharge la vidéo, puis on
 * retire l'image ici). Utilise Media3 Transformer, qui s'appuie sur MediaCodec (aucun binaire
 * externe requis) — FFmpeg pour Android a été abandonné début 2025.
 */
@UnstableApi
class AudioExtractor(private val context: Context) {

    /** [sourceUri] : fichier vidéo source (content:// ou file://). [outputFilePath] : chemin de
     * sortie pour le fichier audio (.m4a). Renvoie true en cas de succès. */
    suspend fun extractAudio(sourceUri: String, outputFilePath: String): Boolean =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val transformer = Transformer.Builder(context)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            if (continuation.isActive) continuation.resume(true)
                        }

                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException,
                        ) {
                            if (continuation.isActive) continuation.resume(false)
                        }
                    })
                    .build()

                val mediaItem = MediaItem.fromUri(Uri.parse(sourceUri))
                val editedMediaItem = EditedMediaItem.Builder(mediaItem)
                    .setRemoveVideo(true)
                    .build()

                runCatching { transformer.start(editedMediaItem, outputFilePath) }
                    .onFailure { if (continuation.isActive) continuation.resume(false) }

                continuation.invokeOnCancellation { runCatching { transformer.cancel() } }
            }
        }
}
