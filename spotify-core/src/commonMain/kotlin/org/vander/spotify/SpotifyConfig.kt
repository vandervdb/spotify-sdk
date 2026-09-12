package org.vander.spotify

/**
 * Configuration fournie par l'application hôte, à l'initialisation.
 *
 * Rien n'est lu depuis un `BuildConfig` ni depuis le manifeste : une lib publiée ne peut pas
 * se reposer sur la configuration de build de ses consommateurs, et surtout elle ne doit
 * embarquer aucun secret. Il n'y a délibérément pas de champ `clientSecret` — le flot est
 * PKCE, et un secret dans une application distribuée n'en est pas un.
 */
public data class SpotifyConfig(
    public val clientId: String,
    /** Doit correspondre exactement à une redirect URI déclarée dans le dashboard Spotify. */
    public val redirectUri: String,
    public val scopes: Set<SpotifyScope> = SpotifyScope.DEFAULT,
) {
    init {
        require(clientId.isNotBlank()) { "clientId est obligatoire" }
        require(redirectUri.isNotBlank()) { "redirectUri est obligatoire" }
        require(scopes.isNotEmpty()) { "au moins une permission est nécessaire" }
    }
}

/**
 * Permissions demandées à l'utilisateur.
 *
 * Une énumération plutôt que des chaînes libres : une faute de frappe dans un scope ne se
 * voit qu'au moment du refus par Spotify, souvent en production.
 */
public enum class SpotifyScope(public val wireName: String) {
    Streaming("streaming"),
    UserReadPrivate("user-read-private"),
    UserReadEmail("user-read-email"),
    UserReadPlaybackState("user-read-playback-state"),
    UserModifyPlaybackState("user-modify-playback-state"),
    UserReadCurrentlyPlaying("user-read-currently-playing"),
    UserLibraryRead("user-library-read"),
    UserLibraryModify("user-library-modify"),
    PlaylistReadPrivate("playlist-read-private"),
    ;

    public companion object {
        /** Ce que demande une application de contrôle typique. */
        public val DEFAULT: Set<SpotifyScope> =
            setOf(
                Streaming,
                UserReadPrivate,
                UserReadPlaybackState,
                UserReadCurrentlyPlaying,
                UserLibraryRead,
                UserLibraryModify,
                PlaylistReadPrivate,
            )
    }
}
