package com.soundflow.core.player

data class PlayerUiState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    /** Vrai pendant le chargement/mise en mémoire tampon — évite de confondre avec "en pause". */
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    /** Message d'erreur de lecture (ex: flux inaccessible), affiché puis effacé côté UI. */
    val errorMessage: String? = null,
)
