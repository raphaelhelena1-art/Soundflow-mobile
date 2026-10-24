package com.soundflow.mobile.downloads

import kotlinx.serialization.Serializable

@Serializable
data class DownloadRecord(
    val id: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val isAudio: Boolean,
    val timestampMs: Long,
    /** URI locale (content://) du fichier téléchargé, renseignée une fois le téléchargement terminé. */
    val localUri: String? = null,
    /** ID renvoyé par Android DownloadManager, sert à faire le lien avec la notification de fin. */
    val downloadManagerId: Long = -1L,
    /** Vrai si ce téléchargement est en réalité une vidéo (avec image) dont il faut extraire
     * l'audio une fois terminée — cas où YouTube ne fournit pas de flux audio seul. */
    val needsAudioExtraction: Boolean = false,
)
