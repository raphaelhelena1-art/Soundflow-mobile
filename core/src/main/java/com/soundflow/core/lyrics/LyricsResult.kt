package com.soundflow.core.lyrics

import kotlinx.serialization.Serializable

@Serializable
data class LyricsResult(
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null,
    val instrumental: Boolean = false,
)

/** Une ligne de paroles synchronisées, avec son horodatage en millisecondes. */
data class LyricLine(val timeMs: Long, val text: String)

/** Parse le format LRC ("[mm:ss.xx]texte") utilisé par LRCLIB pour les paroles synchronisées. */
fun parseSyncedLyrics(raw: String): List<LyricLine> {
    val regex = Regex("""\[(\d{2}):(\d{2})\.(\d{2,3})](.*)""")
    return raw.lines().mapNotNull { line ->
        val match = regex.find(line) ?: return@mapNotNull null
        val (min, sec, ms, text) = match.destructured
        val millis = ms.padEnd(3, '0').take(3).toLongOrNull() ?: 0L
        val timeMs = (min.toLongOrNull() ?: 0L) * 60_000 + (sec.toLongOrNull() ?: 0L) * 1000 + millis
        LyricLine(timeMs, text.trim())
    }
}
