package org.vander.spotify.model

/** Genre de contexte d'où un titre a été lu. Les autres (podcast, collection…) sont ignorés. */
public enum class PlayContextType {
    Album,
    Playlist,
    Artist,
}

/**
 * Ce que l'utilisateur écoutait quand le titre est passé : l'album, la playlist ou l'artiste.
 *
 * @property id identifiant nu, pas l'URI : il se passe tel quel à `album(id)`, `artist(id)` ou
 *   `playlist(PlaylistId(id))`.
 */
public data class PlayContext(
    public val type: PlayContextType,
    public val id: String,
) {
    public val uri: String get() = "spotify:${type.name.lowercase()}:$id"
}

/**
 * Une écoute de l'historique.
 *
 * @property playedAtMs fin de l'écoute, en millisecondes depuis l'époque Unix. Un `Long` plutôt
 *   qu'un type date : la lib n'impose pas kotlinx-datetime à ses consommateurs.
 * @property context `null` quand Spotify n'en donne pas, par exemple un titre lu depuis les titres likés.
 */
public data class PlayHistory(
    public val track: Track,
    public val playedAtMs: Long,
    public val context: PlayContext? = null,
)
