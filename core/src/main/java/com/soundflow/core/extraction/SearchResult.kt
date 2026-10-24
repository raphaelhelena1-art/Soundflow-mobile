package com.soundflow.core.extraction

data class SearchResult(
    /** URL NewPipe du morceau — sert d'identifiant unique et de référence pour résoudre le flux audio. */
    val id: String,
    val title: String,
    val uploaderName: String,
    val thumbnailUrl: String?,
    val durationSeconds: Long,
)
