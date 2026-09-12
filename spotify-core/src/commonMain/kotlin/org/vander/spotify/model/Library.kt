package org.vander.spotify.model

public data class Playlist(
    public val id: PlaylistId,
    public val name: String,
    public val trackCount: Int = 0,
    public val images: List<Image> = emptyList(),
) {
    public val cover: Image? get() = images.firstOrNull()
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
