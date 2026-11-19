package com.soundflow.core.exportify

/**
 * Morceau extrait d'un fichier CSV d'exportation Exportify (Spotify).
 */
data class ExportifyTrack(
    val title: String,
    val artist: String,
    val durationMs: Long,
) {
    val durationSeconds: Long
        get() = if (durationMs > 0) durationMs / 1000L else 0L
}
