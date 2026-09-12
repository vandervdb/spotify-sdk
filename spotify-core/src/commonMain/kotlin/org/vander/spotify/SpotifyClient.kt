package org.vander.spotify

import kotlinx.coroutines.flow.StateFlow
import org.vander.spotify.auth.SessionState
import org.vander.spotify.model.PlaylistCollection
import org.vander.spotify.model.Queue
import org.vander.spotify.model.TrackId
import org.vander.spotify.model.User

/**
 * La porte d'entrée de la lib, disponible sur toutes les cibles.
 *
 * Le contrôle de lecture local n'est pas ici : il passe par l'App Remote, qui n'existe que
 * sur Android, et vit donc dans `SpotifyPlayer` (artefact `:spotify-android`). Ce n'est pas
 * un oubli mais le contrat : sur iOS, le code qui appellerait `skipNext()` ne compile pas,
 * plutôt que d'échouer à l'exécution.
 *
 * Toutes les opérations rendent un [Result] dont l'échec est un [SpotifyError] — aucune
 * exception ne traverse cette frontière.
 */
public interface SpotifyClient {
    /** Identité seulement : connecté ou non. Voir [SessionState]. */
    public val session: StateFlow<SessionState>

    /** Dernières playlists connues. Vide tant que [refreshPlaylists] n'a pas abouti. */
    public val playlists: StateFlow<PlaylistCollection>

    /** Dernière file d'attente connue. Instantané, rafraîchi par [refreshQueue]. */
    public val queue: StateFlow<Queue>

    /**
     * Ouvre l'écran d'autorisation Spotify et échange le code contre un jeton.
     *
     * Suspend jusqu'à l'issue. Il n'y a pas d'ordre d'appel à retenir et pas de `launcher` à
     * fournir : l'[org.vander.spotify.auth.Authorizer] passé à la construction s'en charge.
     */
    public suspend fun signIn(): Result<Unit>

    /** Efface le jeton et ramène [session] à [SessionState.SignedOut]. */
    public suspend fun signOut()

    public suspend fun currentUser(): Result<User>

    public suspend fun refreshPlaylists(): Result<PlaylistCollection>

    public suspend fun refreshQueue(): Result<Queue>

    public suspend fun isSaved(track: TrackId): Result<Boolean>

    /**
     * Ajoute ou retire [track] de la bibliothèque de l'utilisateur.
     *
     * Un seul verbe avec un booléen plutôt que `save`/`remove` : l'appelant tient un état
     * binaire, et deux méthodes l'obligeaient à choisir laquelle appeler — c'est exactement
     * là que l'implémentation d'origine avait divergé entre deux chemins de code.
     */
    public suspend fun setSaved(
        track: TrackId,
        saved: Boolean,
    ): Result<Unit>

    /** Libère le client HTTP. Après cet appel l'instance n'est plus utilisable. */
    public fun close()
}
