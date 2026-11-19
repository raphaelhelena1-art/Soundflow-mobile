package com.soundflow.core.exportify

import java.io.InputStream
import java.io.InputStreamReader

data class ParsedExportifyPlaylist(
    val name: String,
    val tracks: List<ExportifyTrack>,
)

object ExportifyCsvParser {

    /**
     * Analyse un flux d'entrée CSV généré par Exportify.
     */
    fun parse(inputStream: InputStream, filename: String? = null): ParsedExportifyPlaylist {
        val text = InputStreamReader(inputStream, Charsets.UTF_8).use { it.readText() }
        val playlistName = derivePlaylistName(filename)
        return parseText(text, playlistName)
    }

    /**
     * Analyse une chaîne au format CSV Exportify.
     */
    fun parseText(csvText: String, defaultName: String = "Playlist Exportify"): ParsedExportifyPlaylist {
        val rawLines = csvText.split(Regex("\r?\n")).filter { it.isNotBlank() }
        if (rawLines.isEmpty()) {
            return ParsedExportifyPlaylist(defaultName, emptyList())
        }

        val rows = rawLines.map { parseCsvLine(it) }
        val firstRow = rows.firstOrNull() ?: return ParsedExportifyPlaylist(defaultName, emptyList())

        var titleIndex = -1
        var artistIndex = -1
        var durationIndex = -1

        // Recherche des index de colonnes d'après l'en-tête CSV d'Exportify
        for ((idx, colName) in firstRow.withIndex()) {
            val normalized = colName.lowercase().trim()
            when {
                titleIndex == -1 && (normalized == "track name" || normalized == "title" || normalized == "name" || normalized == "track_name") -> titleIndex = idx
                artistIndex == -1 && (normalized.contains("artist name") || normalized == "artist" || normalized == "artists" || normalized == "artist_name") -> artistIndex = idx
                durationIndex == -1 && (normalized.contains("duration") || normalized == "length" || normalized == "duration_ms") -> durationIndex = idx
            }
        }

        val dataRows: List<List<String>>
        if (titleIndex != -1 || artistIndex != -1 || durationIndex != -1) {
            // Première ligne identifiée comme en-tête
            dataRows = rows.drop(1)
        } else {
            // repli sur les positions Exportify standards par défaut :
            // Track URI(0), Track Name(1), Artist Name(s)(2), ..., Track Duration (ms)(8)
            titleIndex = 1
            artistIndex = 2
            durationIndex = 8
            dataRows = rows
        }

        val tracks = mutableListOf<ExportifyTrack>()
        for (row in dataRows) {
            val title = row.getOrNull(titleIndex)?.trim().orEmpty()
            val artist = row.getOrNull(artistIndex)?.trim().orEmpty()
            val durationRaw = row.getOrNull(durationIndex)?.trim().orEmpty()
            val durationMs = parseDurationMs(durationRaw)

            if (title.isNotBlank()) {
                tracks.add(
                    ExportifyTrack(
                        title = title,
                        artist = artist,
                        durationMs = durationMs,
                    )
                )
            }
        }

        return ParsedExportifyPlaylist(defaultName, tracks)
    }

    /**
     * Analyse une ligne CSV en tenant compte des guillemets et virgules internes.
     */
    fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '"') {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                    sb.append('"')
                    i++
                } else {
                    inQuotes = !inQuotes
                }
            } else if (c == ',' && !inQuotes) {
                result.add(sb.toString().trim())
                sb.setLength(0)
            } else {
                sb.append(c)
            }
            i++
        }
        result.add(sb.toString().trim())
        return result
    }

    private fun parseDurationMs(raw: String): Long {
        if (raw.isBlank()) return 0L
        raw.toLongOrNull()?.let { return it }

        // Si la durée est sous la forme MM:SS ou HH:MM:SS
        if (raw.contains(":")) {
            val parts = raw.split(":")
            val seconds = when (parts.size) {
                2 -> (parts[0].toLongOrNull() ?: 0L) * 60 + (parts[1].toLongOrNull() ?: 0L)
                3 -> (parts[0].toLongOrNull() ?: 0L) * 3600 + (parts[1].toLongOrNull() ?: 0L) * 60 + (parts[2].toLongOrNull() ?: 0L)
                else -> 0L
            }
            return seconds * 1000L
        }

        return 0L
    }

    private fun derivePlaylistName(filename: String?): String {
        if (filename.isNullOrBlank()) return "Playlist Exportify"
        val cleanName = filename.substringAfterLast("/").substringAfterLast("\\").trim()
        val nameWithoutExt = if (cleanName.endsWith(".csv", ignoreCase = true)) {
            cleanName.substring(0, cleanName.length - 4)
        } else {
            cleanName
        }
        return nameWithoutExt.ifBlank { "Playlist Exportify" }
    }
}
