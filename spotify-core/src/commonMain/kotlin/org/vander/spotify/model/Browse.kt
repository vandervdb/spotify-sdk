package org.vander.spotify.model

/** Un genre de résultat de recherche ; [apiValue] est la valeur du paramètre `type` de Spotify. */
public enum class SearchType(
    public val apiValue: String,
) {
    Album("album"),
    Artist("artist"),
    Playlist("playlist"),
    Track("track"),
}

/**
 * Résultat d'une recherche, une liste par genre. Contrairement à [PlaylistCollection], pas de
 * `total` : une recherche n'est pas paginée par la lib, elle rend les premiers résultats.
 */
public data class SearchResults(
    public val albums: List<Album> = emptyList(),
    public val artists: List<Artist> = emptyList(),
    public val playlists: List<Playlist> = emptyList(),
    public val tracks: List<Track> = emptyList(),
) {
    public val isEmpty: Boolean get() = albums.isEmpty() && artists.isEmpty() && playlists.isEmpty() && tracks.isEmpty()

    public companion object {
        public val EMPTY: SearchResults = SearchResults()
    }
}

/** Un album et ses titres. Chaque titre porte son [Track.album], pour sa pochette. */
public data class AlbumDetail(
    public val album: Album,
    public val tracks: List<Track> = emptyList(),
) {
    public val durationMs: Long get() = tracks.sumOf { it.durationMs }
}

/**
 * Un artiste, ses titres populaires et ses albums.
 *
 * [topTracks] et [albums] sont lus au mieux : Spotify restreint certains endpoints selon
 * l'application, et un échec là ne doit pas empêcher d'afficher l'artiste lui-même.
 */
public data class ArtistDetail(
    public val artist: Artist,
    public val topTracks: List<Track> = emptyList(),
    public val albums: List<Album> = emptyList(),
)

/**
 * Une playlist et ses titres. [totalTracks] peut dépasser `tracks.size` : la lib ne lit que la
 * première page.
 */
public data class PlaylistDetail(
    public val playlist: Playlist,
    public val description: String? = null,
    public val tracks: List<Track> = emptyList(),
    public val totalTracks: Int = tracks.size,
)
