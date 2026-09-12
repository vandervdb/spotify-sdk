package org.vander.spotify.auth

import org.vander.spotify.SpotifyError

/**
 * État d'identité de la session — et rien d'autre.
 *
 * L'état de la connexion App Remote n'est volontairement pas ici : il n'existe que sur
 * Android, et il vit dans `:spotify-android`. Mélanger les deux axes dans un seul type est
 * l'erreur qui rendait l'implémentation d'origine impossible à raisonner : on ne pouvait
 * pas distinguer « déconnecté mais toujours identifié » de « jamais identifié ».
 */
public sealed interface SessionState {
    /** Aucun token en mémoire. État initial, et état après [SpotifyClient.signOut]. */
    public data object SignedOut : SessionState

    /** L'écran d'autorisation Spotify est ouvert. L'application est souvent en arrière-plan. */
    public data object Authorizing : SessionState

    /** Un token utilisable est en réserve. Les appels Web API peuvent partir. */
    public data object Authorized : SessionState

    public data class Failed(
        public val error: SpotifyError,
    ) : SessionState
}
