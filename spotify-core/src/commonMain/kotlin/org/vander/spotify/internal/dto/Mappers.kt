package org.vander.spotify.internal.dto

import org.vander.spotify.model.PlaylistDetail
import org.vander.spotify.model.SearchResults
import org.vander.spotify.model.AlbumDetail
import org.vander.spotify.model.Album
import org.vander.spotify.model.AlbumCollection
import org.vander.spotify.model.Artist
import org.vander.spotify.model.ArtistCollection
import org.vander.spotify.model.Image
import org.vander.spotify.model.Playlist
import org.vander.spotify.model.PlaylistCollection
import org.vander.spotify.model.PlaylistId
import org.vander.spotify.model.Queue
import org.vander.spotify.model.Track
import org.vander.spotify.model.TrackId
import org.vander.spotify.model.User
import org.vander.spotify.model.UserId

internal fun ImageDto.toDomain(): Image = Image(url = url, width = width, height = height)

internal fun ArtistDto.toDomain(): Artist = Artist(id = id, name = name, images = images.map { it.toDomain() })

internal fun AlbumDto.toDomain(): Album =
    Album(
        id = id,
        name = name,
        images = images.map { it.toDomain() },
        artists = artists.map { it.toDomain() },
        // `release_date` vaut « 2023 », « 2023-05 » ou « 2023-05-12 » selon la précision.
        releaseYear = releaseDate?.take(4)?.toIntOrNull(),
        trackCount = totalTracks,
    )

/**
 * Rend `null` quand la piste n'a pas d'identifiant.
 *
 * Ça arrive réellement : un titre local ajouté par l'utilisateur, ou un épisode de podcast
 * dans une file d'attente. Les laisser passer avec un identifiant vide contaminerait tout le
 * modèle en aval, donc ils sont filtrés ici, à la frontière.
 */
internal fun TrackDto.toDomainOrNull(): Track? {
    val trackId = id?.takeIf { it.isNotBlank() } ?: return null
    return Track(
        id = TrackId(trackId),
        name = name,
        artists = artists.map { it.toDomain() },
        album = album?.toDomain(),
        durationMs = durationMs,
        trackNumber = trackNumber,
    )
}

internal fun PlaylistDto.toDomainOrNull(): Playlist? {
    val playlistId = id?.takeIf { it.isNotBlank() } ?: return null
    return Playlist(
        id = PlaylistId(playlistId),
        name = name,
        trackCount = tracks?.total ?: 0,
        images = images.map { it.toDomain() },
    )
}

internal fun PlaylistPageDto.toDomain(): PlaylistCollection =
    PlaylistCollection(items = items.mapNotNull { it.toDomainOrNull() }, total = total)

internal fun QueueDto.toDomain(): Queue =
    Queue(
        currentlyPlaying = currentlyPlaying?.toDomainOrNull(),
        upcoming = queue.mapNotNull { it.toDomainOrNull() },
    )

internal fun UserDto.toDomain(): User =
    User(
        id = UserId(id),
        displayName = displayName,
        email = email,
        images = images.map { it.toDomain() },
        product = product,
    )

internal fun SavedAlbumPageDto.toDomain(): AlbumCollection =
    AlbumCollection(items = items.mapNotNull { it.album?.takeIf { a -> a.id.isNotBlank() }?.toDomain() }, total = total)

internal fun FollowedArtistsDto.toDomain(): ArtistCollection =
    ArtistCollection(items = artists.items.filter { it.id.isNotBlank() }.map { it.toDomain() }, total = artists.total)

internal fun SearchResponseDto.toDomain(): SearchResults =
    SearchResults(
        albums = albums.items.mapNotNull { it?.takeIf { a -> a.id.isNotBlank() }?.toDomain() },
        artists = artists.items.mapNotNull { it?.takeIf { a -> a.id.isNotBlank() }?.toDomain() },
        playlists = playlists.items.mapNotNull { it?.toDomainOrNull() },
        tracks = tracks.items.mapNotNull { it?.toDomainOrNull() },
    )

internal fun AlbumDto.toDetail(): AlbumDetail {
    val album = toDomain()
    // Les titres d'un album n'embarquent pas l'album : on le rattache pour leur pochette.
    val tracks = tracks?.items.orEmpty().mapNotNull { it?.toDomainOrNull()?.copy(album = album) }
    return AlbumDetail(album = album, tracks = tracks)
}

internal fun TopTracksDto.toDomain(): List<Track> = tracks.mapNotNull { it?.toDomainOrNull() }

internal fun PlaylistDetailDto.toDomainOrNull(): PlaylistDetail? {
    val playlistId = id?.takeIf { it.isNotBlank() } ?: return null
    val page = tracks ?: items
    val tracks = page?.items.orEmpty().mapNotNull { (it?.track ?: it?.item)?.toDomainOrNull() }
    return PlaylistDetail(
        playlist =
            Playlist(
                id = PlaylistId(playlistId),
                name = name,
                trackCount = page?.total ?: tracks.size,
                images = images.map { it.toDomain() },
            ),
        description = description?.takeIf { it.isNotBlank() },
        tracks = tracks,
        totalTracks = page?.total ?: tracks.size,
    )
}
