package com.soundflow.core.spotify

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.security.SecureRandom

class SpotifyRepository(
    private var defaultClientId: String = DEFAULT_CLIENT_ID,
    private val redirectUri: String = REDIRECT_URI,
) {

    companion object {
        const val DEFAULT_CLIENT_ID = "45f47a610e7b46959be734bf6b4b419b"
        const val REDIRECT_URI = "soundflow://spotify-callback"
        const val SCOPE = "playlist-read-private playlist-read-collaborative"
    }

    private val httpClient = OkHttpClient()
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private var codeVerifier: String? = null
    var accessToken: String? = null
        private set

    fun generateAuthorizationUrl(customClientId: String? = null): String {
        val cid = customClientId?.trim()?.ifEmpty { null } ?: defaultClientId
        defaultClientId = cid
        val verifier = generateCodeVerifier()
        codeVerifier = verifier
        val challenge = generateCodeChallenge(verifier)

        return "https://accounts.spotify.com/authorize" +
                "?client_id=$cid" +
                "&response_type=code" +
                "&redirect_uri=$redirectUri" +
                "&scope=${SCOPE.replace(" ", "%20")}" +
                "&code_challenge_method=S256" +
                "&code_challenge=$challenge"
    }

    suspend fun exchangeCodeForToken(code: String, customClientId: String? = null): Boolean = withContext(Dispatchers.IO) {
        val verifier = codeVerifier ?: return@withContext false
        val cid = customClientId?.trim()?.ifEmpty { null } ?: defaultClientId
        val formBody = FormBody.Builder()
            .add("client_id", cid)
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", redirectUri)
            .add("code_verifier", verifier)
            .build()

        val request = Request.Builder()
            .url("https://accounts.spotify.com/api/token")
            .post(formBody)
            .build()

        runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use false
                val bodyString = response.body?.string() ?: return@use false
                val token = json.decodeFromString<SpotifyAuthToken>(bodyString)
                accessToken = token.accessToken
                true
            }
        }.getOrDefault(false)
    }

    suspend fun getUserPlaylists(): List<SpotifyPlaylist> = withContext(Dispatchers.IO) {
        val token = accessToken ?: return@withContext emptyList()
        val playlists = mutableListOf<SpotifyPlaylist>()
        var offset = 0
        val limit = 50

        while (true) {
            val url = "https://api.spotify.com/v1/me/playlists?limit=$limit&offset=$offset"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val page = runCatching {
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val body = response.body?.string() ?: return@use null
                    json.decodeFromString<SpotifyPaginatedResponse<SpotifyPlaylist>>(body)
                }
            }.getOrNull() ?: break

            playlists.addAll(page.items)
            if (page.next == null || page.items.isEmpty()) break
            offset += limit
            delay(100) // Delai de sécurité pour éviter l'erreur 429 (rate limiting)
        }

        playlists
    }

    suspend fun getPlaylistTracks(playlistId: String): List<SpotifyTrack> = withContext(Dispatchers.IO) {
        val token = accessToken ?: return@withContext emptyList()
        val tracks = mutableListOf<SpotifyTrack>()
        var offset = 0
        val limit = 50

        while (true) {
            val url = "https://api.spotify.com/v1/playlists/$playlistId/tracks?limit=$limit&offset=$offset"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val page = runCatching {
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val body = response.body?.string() ?: return@use null
                    json.decodeFromString<SpotifyPaginatedResponse<SpotifyPlaylistItem>>(body)
                }
            }.getOrNull() ?: break

            page.items.forEach { item ->
                item.track?.let { tracks.add(it) }
            }

            if (page.next == null || page.items.isEmpty()) break
            offset += limit
            delay(100) // Delai de sécurité pour éviter l'erreur 429 (rate limiting)
        }

        tracks
    }

    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun generateCodeChallenge(verifier: String): String {
        val bytes = verifier.toByteArray(Charsets.US_ASCII)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }
}
