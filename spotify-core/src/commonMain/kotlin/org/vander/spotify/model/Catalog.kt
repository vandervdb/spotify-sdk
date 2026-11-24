package org.vander.spotify.model

/** Image de pochette. [width] et [height] sont absents sur certaines réponses de l'API. */
public data class Image(
    public val url: String,
    public val width: Int? = null,
    public val height: Int? = null,
)

public data class Artist(
    public val id: String,
    public val name: String,
    public val images: List<Image> = emptyList(),
) {
    public val cover: Image? get() = images.maxByOrNull { (it.width ?: 0) * (it.height ?: 0) }

    /** URI de contexte attendue par l'App Remote pour lire les titres de l'artiste. */
    public val uri: String get() = "spotify:artist:$id"
}

public data class Album(
    public val id: String,
    public val name: String,
    public val images: List<Image> = emptyList(),
    public val artists: List<Artist> = emptyList(),
    /** Année de sortie, `null` si la date est absente ou illisible. */
    public val releaseYear: Int? = null,
    public val trackCount: Int = 0,
) {
    /** URI de contexte attendue par l'App Remote pour lire l'album en entier. */
    public val uri: String get() = "spotify:album:$id"

    /** Artistes joints, tels qu'on les affiche sous un album. */
    public val artistNames: String get() = artists.joinToString(", ") { it.name }

    /** La plus grande image disponible, ou `null` si l'album n'en porte aucune. */
    public val cover: Image? get() = images.maxByOrNull { (it.width ?: 0) * (it.height ?: 0) }
}

public data class Track(
    public val id: TrackId,
    public val name: String,
    public val artists: List<Artist> = emptyList(),
    public val album: Album? = null,
    public val durationMs: Long = 0,
    public val trackNumber: Int? = null,
) {
    /** Artistes joints, tels qu'on les affiche sous un titre. */
    public val artistNames: String get() = artists.joinToString(", ") { it.name }
}
