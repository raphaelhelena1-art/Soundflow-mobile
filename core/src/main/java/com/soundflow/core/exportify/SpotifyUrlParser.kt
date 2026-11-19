package com.soundflow.core.exportify

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.regex.Pattern

object SpotifyUrlParser {

    private val httpClient by lazy { OkHttpClient() }

    /**
     * Tente de récupérer les pistes (titre, artiste, durée_ms) depuis l'URL d'une playlist Spotify.
     */
    suspend fun parseSpotifyUrl(url: String): ParsedExportifyPlaylist? = withContext(Dispatchers.IO) {
        val playlistId = extractPlaylistId(url) ?: return@withContext null
        val embedUrl = "https://open.spotify.com/embed/playlist/$playlistId"

        val request = Request.Builder()
            .url(embedUrl)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            .build()

        val html = runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        }.getOrNull() ?: return@withContext null

        parseEmbedHtml(html)
    }

    fun extractPlaylistId(url: String): String? {
        val pattern = Pattern.compile("playlist[/:]([a-zA-Z0-9]+)")
        val matcher = pattern.matcher(url)
        return if (matcher.find()) matcher.group(1) else null
    }

    private fun parseEmbedHtml(html: String): ParsedExportifyPlaylist? {
        // Détection de la balise <script id="__NEXT_DATA__" type="application/json"> ou <script id="resource" ...>
        val scriptPattern = Pattern.compile("<script id=\"(?:__NEXT_DATA__|resource)\"[^>]*>(.*?)</script>", Pattern.DOTALL)
        val matcher = scriptPattern.matcher(html)
        if (!matcher.find()) return null

        val jsonString = matcher.group(1) ?: return null
        return runCatching {
            val root = JSONObject(jsonString)
            var resource = root.optJSONObject("props")?.optJSONObject("pageProps")?.optJSONObject("state")?.optJSONObject("data")?.optJSONObject("entity")
            if (resource == null) {
                resource = root.optJSONObject("resource")
            }

            val name = resource?.optString("name") ?: "Playlist Spotify"
            val tracksList = mutableListOf<ExportifyTrack>()

            val tracksArray = resource?.optJSONObject("tracks")?.optJSONArray("items")
                ?: resource?.optJSONArray("trackList")

            if (tracksArray != null) {
                for (i in 0 until tracksArray.length()) {
                    val item = tracksArray.optJSONObject(i) ?: continue
                    val trackObj = item.optJSONObject("track") ?: item

                    val title = trackObj.optString("name", trackObj.optString("title"))
                    val durationMs = trackObj.optLong("duration_ms", trackObj.optLong("duration", 0L))

                    var artistName = ""
                    val artistsArray = trackObj.optJSONArray("artists")
                    if (artistsArray != null && artistsArray.length() > 0) {
                        val names = mutableListOf<String>()
                        for (a in 0 until artistsArray.length()) {
                            val aObj = artistsArray.optJSONObject(a)
                            val aName = aObj?.optString("name")
                            if (!aName.isNullOrBlank()) names.add(aName)
                        }
                        artistName = names.joinToString(", ")
                    } else {
                        artistName = trackObj.optString("subtitle", trackObj.optString("artist", ""))
                    }

                    if (title.isNotBlank()) {
                        tracksList.add(
                            ExportifyTrack(
                                title = title,
                                artist = artistName,
                                durationMs = durationMs,
                            )
                        )
                    }
                }
            }

            ParsedExportifyPlaylist(name, tracksList)
        }.getOrNull()
    }
}
