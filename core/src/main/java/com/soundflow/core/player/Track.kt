package com.soundflow.core.player

import kotlinx.serialization.Serializable

/**
 * Représente un morceau lisible (streaming ou fichier téléchargé/importé).
 * mediaUrl peut être une URL distante (streaming) ou un chemin local (file:// ou content://).
 */
@Serializable
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val mediaUrl: String,
    val artworkUrl: String? = null,
    /** Vrai si mediaUrl pointe vers un flux combiné vidéo+audio (permet l'affichage vidéo). */
    val hasVideo: Boolean = false,
    /** Vrai si ce morceau a été téléchargé sur l'appareil (conditionne l'ajout aux playlists hors-ligne). */
    val isDownloaded: Boolean = false,
)
