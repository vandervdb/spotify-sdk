package org.vander.spotify.internal.web

import io.ktor.http.URLBuilder
import org.vander.spotify.SpotifyConfig
import org.vander.spotify.auth.PkceChallenge

/**
 * Construit l'URL de `/authorize` d'un flot Authorization Code + PKCE.
 *
 * Aucun secret n'y figure ni n'y figurera : la preuve d'identité du client est le
 * `code_verifier`, présenté plus tard à l'échange. C'est ce qui rend la lib distribuable.
 */
internal fun buildAuthorizeUrl(
    config: SpotifyConfig,
    challenge: PkceChallenge,
    state: String,
    accountsBase: String = SPOTIFY_ACCOUNTS_BASE,
): String =
    URLBuilder(accountsBase + AUTHORIZE_PATH)
        .apply {
            parameters.append("client_id", config.clientId)
            parameters.append("response_type", "code")
            parameters.append("redirect_uri", config.redirectUri)
            parameters.append("scope", config.scopes.joinToString(" ") { it.wireName })
            parameters.append("code_challenge_method", challenge.method)
            parameters.append("code_challenge", challenge.challenge)
            parameters.append("state", state)
        }.buildString()
