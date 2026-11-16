package com.soundflow.core.spotify

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SpotifyAuthToken(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String,
    @SerialName("expires_in") val expiresIn: Int,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("scope") val scope: String? = null,
)

@Serializable
data class SpotifyPlaylist(
    val id: String,
    val name: String,
    val description: String? = null,
    val images: List<SpotifyImage> = emptyList(),
    val tracks: SpotifyPlaylistTracksInfo? = null,
)

@Serializable
data class SpotifyImage(val url: String)

@Serializable
data class SpotifyPlaylistTracksInfo(val total: Int)

@Serializable
data class SpotifyPaginatedResponse<T>(
    val items: List<T> = emptyList(),
    val total: Int = 0,
    val limit: Int = 50,
    val offset: Int = 0,
    val next: String? = null,
)

@Serializable
data class SpotifyPlaylistItem(val track: SpotifyTrack? = null)

@Serializable
data class SpotifyTrack(
    val id: String? = null,
    val name: String,
    val artists: List<SpotifyArtist> = emptyList(),
    val album: SpotifyAlbum? = null,
    @SerialName("duration_ms") val durationMs: Long = 0,
)

@Serializable
data class SpotifyArtist(val name: String)

@Serializable
data class SpotifyAlbum(val images: List<SpotifyImage> = emptyList())
