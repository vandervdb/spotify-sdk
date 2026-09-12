package org.vander.spotify.auth

import org.vander.spotify.SpotifyScope

/**
 * Ce que le cœur ne peut pas faire : ouvrir un écran d'autorisation et en récupérer le code.
 *
 * C'est le seul seam vraiment spécifique à la plateforme du côté identité. Sur Android,
 * l'implémentation passe par le SDK Spotify, qui délègue à l'application Spotify installée
 * quand elle est présente ; sur iOS, par `ASWebAuthenticationSession`. Le cœur ne connaît ni
 * l'un ni l'autre.
 */
public interface Authorizer {
    /**
     * Ouvre [request] et suspend jusqu'à la réponse de l'utilisateur.
     *
     * @return le `code` de la redirection, ou un échec
     *   ([org.vander.spotify.SpotifyError.AuthorizationCancelled] si l'utilisateur a renoncé).
     *   La vérification du `state` est faite par l'appelant, jamais par l'implémentation :
     *   c'est une règle du protocole, elle ne doit pas dépendre de la plateforme.
     */
    public suspend fun authorize(request: AuthorizationRequest): Result<AuthorizationResponse>
}

/**
 * Requête d'autorisation, donnée sous deux formes.
 *
 * [url] convient à une implémentation qui ouvre un navigateur — c'est le cas d'iOS. Les
 * champs structurés servent à celles qui reconstruisent la requête avec leur propre SDK,
 * comme Android, où `AuthorizationRequest.Builder` veut les morceaux séparés. Les deux
 * décrivent la même demande : [url] est construite à partir des champs qui suivent.
 */
public data class AuthorizationRequest(
    public val url: String,
    public val clientId: String,
    public val redirectUri: String,
    public val scopes: Set<SpotifyScope>,
    /** `BASE64URL(SHA256(code_verifier))`. Le verifier, lui, ne sort jamais du cœur. */
    public val codeChallenge: String,
    public val codeChallengeMethod: String,
    public val state: String,
)

public data class AuthorizationResponse(
    public val code: String,
    public val state: String,
)
