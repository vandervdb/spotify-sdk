package org.vander.spotify.model

public data class Playlist(
    public val id: PlaylistId,
    public val name: String,
    public val trackCount: Int = 0,
    public val images: List<Image> = emptyList(),
) {
    public val cover: Image? get() = images.firstOrNull()

    /** URI de contexte attendue par l'App Remote pour lire la playlist en entier. */
    public val uri: String get() = "spotify:playlist:${id.value}"
}

/**
 * Page de playlists. [total] est le total côté serveur, qui peut dépasser `items.size` :
 * l'API pagine, et le champ permet à l'appelant de savoir qu'il ne voit pas tout.
 */
public data class PlaylistCollection(
    public val items: List<Playlist> = emptyList(),
    public val total: Int = 0,
) {
    public companion object {
        public val EMPTY: PlaylistCollection = PlaylistCollection()
    }
}

/** Page d'albums enregistrés. Même sens de [total] que pour [PlaylistCollection]. */
public data class AlbumCollection(
    public val items: List<Album> = emptyList(),
    public val total: Int = 0,
) {
    public companion object {
        public val EMPTY: AlbumCollection = AlbumCollection()
    }
}

/** Page d'artistes suivis. Même sens de [total] que pour [PlaylistCollection]. */
public data class ArtistCollection(
    public val items: List<Artist> = emptyList(),
    public val total: Int = 0,
) {
    public companion object {
        public val EMPTY: ArtistCollection = ArtistCollection()
    }
}
