package org.vander.spotify.internal.web

import org.vander.spotify.internal.dto.toDomainOrNull
import org.vander.spotify.model.PlaylistId
import org.vander.spotify.model.PlaylistDetail
import org.vander.spotify.internal.dto.PlaylistDetailDto
import org.vander.spotify.model.SearchResults
import org.vander.spotify.model.SearchType
import org.vander.spotify.model.ArtistDetail
import org.vander.spotify.model.AlbumDetail
import org.vander.spotify.internal.dto.toDetail
import org.vander.spotify.internal.dto.TopTracksDto
import org.vander.spotify.internal.dto.SearchResponseDto
import org.vander.spotify.internal.dto.PageDto
import org.vander.spotify.internal.dto.ArtistDto
import org.vander.spotify.internal.dto.AlbumDto
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.put
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import org.vander.spotify.SpotifyError
import org.vander.spotify.internal.dto.FollowedArtistsDto
import org.vander.spotify.internal.dto.PlaylistPageDto
import org.vander.spotify.internal.dto.QueueDto
import org.vander.spotify.internal.dto.SavedTrackPageDto
import org.vander.spotify.internal.dto.SavedAlbumPageDto
import org.vander.spotify.internal.dto.UserDto
import org.vander.spotify.model.AlbumCollection
import org.vander.spotify.model.ArtistCollection
import org.vander.spotify.model.PlaylistCollection
import org.vander.spotify.model.Queue
import org.vander.spotify.model.TrackId
import org.vander.spotify.model.User
import org.vander.spotify.internal.dto.toDomain

/**
 * Appels Web API. Un seul niveau : pas d'interface « data source » sous chaque repository.
 *
 * La revue de l'implémentation d'origine avait mesuré cinq couches et quinze fichiers pour
 * un seul `GET /me/playlists`, avec un seam par étage dont aucun n'avait de second adapter.
 * Ici le seam est [HttpClient] lui-même : `MockEngine` en test, un moteur réel en production.
 * Deux adapters, donc un vrai seam.
 *
 * @param accessToken relu à chaque appel, jamais capturé : le jeton peut avoir été renouvelé
 *   entre deux requêtes.
 */
internal class SpotifyWebApi(
    private val client: HttpClient,
    private val accessToken: suspend () -> Result<String>,
    private val baseUrl: String = SPOTIFY_API_BASE,
) {
    suspend fun me(): Result<User> = authorized { get("me") }.flatMap { it.parseSpotify<UserDto>() }.map { it.toDomain() }

    suspend fun playlists(limit: Int = 50): Result<PlaylistCollection> =
        authorized { get("me/playlists") { parameter("limit", limit) } }
            .flatMap { it.parseSpotify<PlaylistPageDto>() }
            .map { it.toDomain() }

    suspend fun savedAlbums(limit: Int = 50): Result<AlbumCollection> =
        authorized { get("me/albums") { parameter("limit", limit) } }
            .flatMap { it.parseSpotify<SavedAlbumPageDto>() }
            .map { it.toDomain() }

    suspend fun followedArtists(limit: Int = 50): Result<ArtistCollection> =
        authorized {
            get("me/following") {
                parameter("type", "artist")
                parameter("limit", limit)
            }
        }.flatMap { it.parseSpotify<FollowedArtistsDto>() }
            .map { it.toDomain() }

    suspend fun savedTracksTotal(): Result<Int> =
        authorized { get("me/tracks") { parameter("limit", 1) } }
            .flatMap { it.parseSpotify<SavedTrackPageDto>() }
            .map { it.total }

    suspend fun search(
        query: String,
        limit: Int,
        types: Set<SearchType>,
        offset: Int,
    ): Result<SearchResults> =
        authorized {
            get("search") {
                parameter("q", query)
                // Ordre de l'enum, pas celui de l'ensemble : la requête reste stable et testable.
                parameter("type", SearchType.entries.filter { it in types }.joinToString(",") { it.apiValue })
                parameter("limit", limit)
                if (offset > 0) parameter("offset", offset)
            }
        }.flatMap { it.parseSpotify<SearchResponseDto>() }
            .map { it.toDomain() }

    suspend fun album(id: String): Result<AlbumDetail> =
        authorized { get("albums/$id") }.flatMap { it.parseSpotify<AlbumDto>() }.map { it.toDetail() }

    /** Le profil est obligatoire ; titres populaires et albums sont lus au mieux. */
    suspend fun artist(id: String): Result<ArtistDetail> {
        val artist =
            authorized { get("artists/$id") }
                .flatMap { it.parseSpotify<ArtistDto>() }
                .getOrElse { return Result.failure(it) }
                .toDomain()
        val top =
            authorized { get("artists/$id/top-tracks") { parameter("market", "from_token") } }
                .flatMap { it.parseSpotify<TopTracksDto>() }
                .map { it.toDomain() }
                .getOrDefault(emptyList())
        val albums =
            authorized {
                get("artists/$id/albums") {
                    parameter("include_groups", "album,single")
                    parameter("limit", 20)
                }
            }.flatMap { it.parseSpotify<PageDto<AlbumDto>>() }
                .map { page -> page.items.mapNotNull { it?.takeIf { a -> a.id.isNotBlank() }?.toDomain() } }
                .getOrDefault(emptyList())
        return Result.success(ArtistDetail(artist, top, albums))
    }

    suspend fun playlist(id: PlaylistId): Result<PlaylistDetail> =
        authorized { get("playlists/${id.value}") }
            .flatMap { it.parseSpotify<PlaylistDetailDto>() }
            .flatMap { dto ->
                dto.toDomainOrNull()?.let { Result.success(it) }
                    ?: Result.failure(SpotifyError.Serialization(IllegalStateException("playlist sans identifiant")))
            }

    suspend fun isFollowingArtist(id: String): Result<Boolean> =
        authorized {
            get("me/following/contains") {
                parameter("type", "artist")
                parameter("ids", id)
            }
        }.flatMap { it.parseSpotify<List<Boolean>>() }
            .map { it.firstOrNull() ?: false }

    suspend fun setFollowingArtist(
        id: String,
        following: Boolean,
    ): Result<Unit> =
        authorized {
            val configure: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {
                parameter("type", "artist")
                parameter("ids", id)
            }
            if (following) put("me/following", configure) else delete("me/following", configure)
        }.flatMap { it.parseEmpty() }

    suspend fun queue(): Result<Queue> =
        authorized { get("me/player/queue") }.flatMap { it.parseSpotify<QueueDto>() }.map { it.toDomain() }

    suspend fun isSaved(track: TrackId): Result<Boolean> =
        authorized { get("me/tracks/contains") { parameter("ids", track.value) } }
            .flatMap { it.parseSpotify<List<Boolean>>() }
            .map { it.firstOrNull() ?: false }

    suspend fun setSaved(
        track: TrackId,
        saved: Boolean,
    ): Result<Unit> =
        authorized {
            if (saved) {
                put("me/tracks") { parameter("ids", track.value) }
            } else {
                delete("me/tracks") { parameter("ids", track.value) }
            }
        }.flatMap { it.parseEmpty() }

    /**
     * Pose l'en-tête porteur et convertit toute panne de transport en [SpotifyError.Network],
     * pour qu'aucune exception Ktor ne s'échappe de la couche data.
     */
    private suspend fun authorized(call: suspend AuthorizedScope.() -> HttpResponse): Result<HttpResponse> {
        val token = accessToken().getOrElse { return Result.failure(it) }
        return runCatching { AuthorizedScope(client, baseUrl, token).call() }
            .fold(
                onSuccess = { Result.success(it) },
                onFailure = { Result.failure(if (it is SpotifyError) it else SpotifyError.Network(it)) },
            )
    }

    internal class AuthorizedScope(
        private val client: HttpClient,
        private val baseUrl: String,
        private val token: String,
    ) {
        suspend fun get(
            path: String,
            block: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {},
        ): HttpResponse = client.get(baseUrl + path) { bearer(); block() }

        suspend fun put(
            path: String,
            block: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {},
        ): HttpResponse = client.put(baseUrl + path) { bearer(); block() }

        suspend fun delete(
            path: String,
            block: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {},
        ): HttpResponse = client.delete(baseUrl + path) { bearer(); block() }

        private fun io.ktor.client.request.HttpRequestBuilder.bearer() {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
    }
}

/** `Result.flatMap` n'existe pas dans la stdlib ; la chaîne d'appels le réclame partout. */
internal inline fun <T, R> Result<T>.flatMap(transform: (T) -> Result<R>): Result<R> =
    fold(onSuccess = transform, onFailure = { Result.failure(it) })
