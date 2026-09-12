package org.vander.spotify.internal.dto

import org.vander.spotify.model.Album
import org.vander.spotify.model.Artist
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

internal fun ArtistDto.toDomain(): Artist = Artist(id = id, name = name)

internal fun AlbumDto.toDomain(): Album =
    Album(id = id, name = name, images = images.map { it.toDomain() })

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
    )
