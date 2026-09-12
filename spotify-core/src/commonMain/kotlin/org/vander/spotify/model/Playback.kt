package org.vander.spotify.model

/**
 * File d'attente telle que le Web API la rend : un instantané, pas un flux.
 *
 * Elle doit être redemandée pour être rafraîchie — contrairement à l'état que pousse
 * l'App Remote sur Android. C'est la différence de nature entre les deux canaux, et elle
 * est visible ici plutôt que masquée.
 */
public data class Queue(
    public val currentlyPlaying: Track? = null,
    public val upcoming: List<Track> = emptyList(),
) {
    public companion object {
        public val EMPTY: Queue = Queue()
    }
}
