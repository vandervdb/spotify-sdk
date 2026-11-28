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
    val images: List<ImageDto> = emptyList(),
)

@Serializable
internal data class AlbumDto(
    val id: String = "",
    val name: String = "",
    val images: List<ImageDto> = emptyList(),
    val artists: List<ArtistDto> = emptyList(),
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("total_tracks") val totalTracks: Int = 0,
    /** Présent sur `GET /albums/{id}`, absent des albums imbriqués ailleurs. */
    val tracks: PageDto<TrackDto>? = null,
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
    val product: String? = null,
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

@Serializable
internal data class SavedAlbumDto(
    val album: AlbumDto? = null,
)

@Serializable
internal data class SavedAlbumPageDto(
    val items: List<SavedAlbumDto> = emptyList(),
    val total: Int = 0,
)

/** `GET /me/following` enveloppe la page dans un objet `artists`, contrairement à `/me/albums`. */
@Serializable
internal data class FollowedArtistsDto(
    val artists: FollowedArtistPageDto = FollowedArtistPageDto(),
)

@Serializable
internal data class FollowedArtistPageDto(
    val items: List<ArtistDto> = emptyList(),
    val total: Int = 0,
)

/** Seul le total nous intéresse : `limit=1` évite de rapatrier des milliers de titres likés. */
@Serializable
internal data class SavedTrackPageDto(
    val total: Int = 0,
)

/**
 * Page générique. Les éléments sont nullables : `GET /search` renvoie réellement des `null` dans
 * `playlists.items`, et un `List<PlaylistDto>` faisait échouer la réponse entière.
 */
@Serializable
internal data class PageDto<T>(
    val items: List<T?> = emptyList(),
    val total: Int = 0,
)

@Serializable
internal data class SearchResponseDto(
    val albums: PageDto<AlbumDto> = PageDto(),
    val artists: PageDto<ArtistDto> = PageDto(),
    val playlists: PageDto<PlaylistDto> = PageDto(),
    val tracks: PageDto<TrackDto> = PageDto(),
)

@Serializable
internal data class TopTracksDto(
    val tracks: List<TrackDto?> = emptyList(),
)

/**
 * Élément d'une playlist. Le titre est sous `track` ; [item] est lu aussi, par tolérance, car
 * l'API a commencé à renommer ces champs et une lib publiée ne doit pas casser à cause d'un
 * renommage côté Spotify. `null` pour un titre supprimé ou indisponible.
 */
@Serializable
internal data class PlaylistItemDto(
    val track: TrackDto? = null,
    val item: TrackDto? = null,
)

/** Même tolérance pour la page : `tracks` historiquement, `items` dans la nouvelle forme. */
@Serializable
internal data class PlaylistDetailDto(
    val id: String? = null,
    val name: String = "",
    val description: String? = null,
    val images: List<ImageDto> = emptyList(),
    val tracks: PageDto<PlaylistItemDto>? = null,
    val items: PageDto<PlaylistItemDto>? = null,
)

@Serializable
internal data class ContextDto(
    val type: String = "",
    val uri: String = "",
)

@Serializable
internal data class PlayHistoryDto(
    val track: TrackDto? = null,
    @SerialName("played_at") val playedAt: String = "",
    val context: ContextDto? = null,
)

/** `GET /me/player/recently-played` pagine par curseurs ; seuls les éléments nous servent. */
@Serializable
internal data class RecentlyPlayedDto(
    val items: List<PlayHistoryDto?> = emptyList(),
)
