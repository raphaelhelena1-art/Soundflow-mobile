package com.soundflow.core.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

class LyricsRepository {

    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Cherche les paroles en ne se basant que sur le titre et l'artiste (la durée d'une vidéo
     * YouTube ne correspond pas forcément à celle du morceau — intro, silence, etc. — donc c'est
     * un mauvais critère). Teste plusieurs variantes du titre/artiste en cascade jusqu'à trouver
     * une réponse exploitable.
     */
    suspend fun getLyrics(title: String, artist: String): LyricsResult? = withContext(Dispatchers.IO) {
        val titleVariants = buildTitleVariants(title)
        val artistVariants = buildArtistVariants(artist)

        for (t in titleVariants) {
            for (a in artistVariants) {
                fetchSearch(t, a)?.let { return@withContext it }
            }
        }
        // Dernier recours : recherche par titre seul, sans artiste, si rien n'a matché.
        fetchSearch(titleVariants.first(), null)
            ?: fetchLyricsOvh(titleVariants.first(), artistVariants.first())
    }

    private fun fetchSearch(title: String, artist: String?): LyricsResult? {
        val url = buildString {
            append("https://lrclib.net/api/search?track_name=${encode(title)}")
            if (!artist.isNullOrBlank()) append("&artist_name=${encode(artist)}")
        }
        return runCatching {
            client.newCall(buildRequest(url)).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                json.decodeFromString<List<LyricsResult>>(body)
                    .firstOrNull { it.plainLyrics != null || it.syncedLyrics != null }
            }
        }.getOrNull()
    }

    /** Source de repli : lyrics.ovh — pas de version synchronisée, mais couverture différente
     * de LRCLIB, utile quand ce dernier n'a vraiment rien pour un morceau donné. */
    private fun fetchLyricsOvh(title: String, artist: String): LyricsResult? {
        val url = "https://api.lyrics.ovh/v1/${encode(artist)}/${encode(title)}"
        return runCatching {
            client.newCall(buildRequest(url)).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                json.decodeFromString<LyricsOvhResponse>(body).lyrics?.let { LyricsResult(plainLyrics = it) }
            }
        }.getOrNull()
    }

    private fun buildRequest(url: String) = Request.Builder()
        .url(url)
        .header("User-Agent", "SoundFlow/0.1 (https://github.com/raphaelhelena1-art/Soundflow)")
        .build()

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")

    /** Plusieurs variantes du titre, du plus nettoyé au plus brut, dans l'ordre à essayer. */
    private fun buildTitleVariants(title: String): List<String> {
        val noParenthesis = title
            .replace(Regex("""[(\[][^)\]]*[)\]]"""), "") // enlève tout ce qui est entre parenthèses/crochets
            .replace(Regex("""\s{2,}"""), " ")
            .trim()
        val noFeat = noParenthesis.replace(Regex("""\s*(feat\.?|ft\.?)\s+.*$""", RegexOption.IGNORE_CASE), "").trim()
        val beforeDash = title.substringBefore(" - ").trim()
        val raw = title.trim()

        return listOf(noFeat, noParenthesis, beforeDash, raw).distinct().filter { it.isNotBlank() }
    }

    /** Plusieurs variantes du nom d'artiste (nom de chaîne YouTube → nom d'artiste probable). */
    private fun buildArtistVariants(artist: String): List<String> {
        val cleaned = artist
            .replace(Regex("""\s*-\s*Topic$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""VEVO$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*\(Official\)$""", RegexOption.IGNORE_CASE), "")
            .trim()
        val raw = artist.trim()
        return listOf(cleaned, raw).distinct().filter { it.isNotBlank() }
    }
}

@Serializable
private data class LyricsOvhResponse(val lyrics: String? = null)
