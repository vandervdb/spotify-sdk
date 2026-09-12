package org.vander.spotify.internal.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Formes de fil, séparées du modèle de domaine.
 *
 * Tous les champs sont optionnels avec une valeur par défaut : l'API Spotify omet
 * régulièrement des champs selon l'endpoint et le type d'objet, et une lib publiée ne doit
 * pas échouer parce qu'un album n'a pas de pochette.
 */
@Serializable
internal data class ImageDto(
    val url: String = "",
    val width: Int? = null,
    val height: Int? = null,
)

@Serializable
internal data class ArtistDto(
    val id: String = "",
    val name: String = "",
)

@Serializable
internal data class AlbumDto(
    val id: String = "",
    val name: String = "",
    val images: List<ImageDto> = emptyList(),
)

@Serializable
internal data class TrackDto(
    val id: String? = null,
    val name: String = "",
    val artists: List<ArtistDto> = emptyList(),
    val album: AlbumDto? = null,
    @SerialName("duration_ms") val durationMs: Long = 0,
    @SerialName("track_number") val trackNumber: Int? = null,
)

@Serializable
internal data class PlaylistTracksRefDto(
    val total: Int = 0,
)

@Serializable
internal data class PlaylistDto(
    val id: String? = null,
    val name: String = "",
    val images: List<ImageDto> = emptyList(),
    val tracks: PlaylistTracksRefDto? = null,
)

@Serializable
internal data class PlaylistPageDto(
    val items: List<PlaylistDto> = emptyList(),
    val total: Int = 0,
)

@Serializable
internal data class QueueDto(
    @SerialName("currently_playing") val currentlyPlaying: TrackDto? = null,
    val queue: List<TrackDto> = emptyList(),
)

@Serializable
internal data class UserDto(
    val id: String = "",
    @SerialName("display_name") val displayName: String? = null,
    val email: String? = null,
    val images: List<ImageDto> = emptyList(),
)

@Serializable
internal data class TokenResponseDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresInSeconds: Long = 3600,
    @SerialName("token_type") val tokenType: String = "Bearer",
)

@Serializable
internal data class ApiErrorBodyDto(
    val error: ApiErrorDto,
)

@Serializable
internal data class ApiErrorDto(
    val status: Int = 0,
    val message: String = "",
)

/** Le service `accounts` ne parle pas la même langue d'erreur que le Web API. */
@Serializable
internal data class AccountsErrorDto(
    val error: String = "",
    @SerialName("error_description") val errorDescription: String? = null,
)
