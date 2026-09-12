package org.vander.spotify.auth

/**
 * Ce que le cœur ne peut pas faire : ouvrir un écran d'autorisation et en récupérer le code.
 *
 * C'est le seul seam vraiment spécifique à la plateforme du côté identité. Sur Android,
 * l'implémentation passe par le SDK Spotify et un `ActivityResultLauncher` ; sur iOS, par
 * `ASWebAuthenticationSession`. Le cœur ne connaît ni l'un ni l'autre : il fabrique une URL
 * et attend un code.
 */
public interface Authorizer {
    /**
     * Ouvre [request] et suspend jusqu'à la réponse de l'utilisateur.
     *
     * @return le paramètre `code` de la redirection, ou un échec
     *   ([SpotifyError.AuthorizationCancelled] si l'utilisateur a renoncé).
     *   La vérification du `state` est faite par l'appelant, pas par l'implémentation.
     */
    public suspend fun authorize(request: AuthorizationRequest): Result<AuthorizationResponse>
}

public data class AuthorizationRequest(
    /** URL complète de `accounts.spotify.com/authorize`, paramètres PKCE compris. */
    public val url: String,
    public val redirectUri: String,
    /** Valeur anti-rejeu à retrouver telle quelle dans la redirection. */
    public val state: String,
)

public data class AuthorizationResponse(
    public val code: String,
    public val state: String,
)
