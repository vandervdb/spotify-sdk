package org.vander.spotify.internal.web

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.http.Parameters
import org.vander.spotify.SpotifyError
import org.vander.spotify.auth.StoredToken
import org.vander.spotify.internal.dto.TokenResponseDto
import org.vander.spotify.internal.time.Clock

/**
 * Échange et renouvellement du jeton contre `accounts.spotify.com`.
 *
 * Remarquer ce qui n'est pas là : aucun en-tête `Authorization: Basic`, aucun
 * `client_secret` dans le formulaire. C'est toute la différence entre une lib qu'on peut
 * publier et une qu'on ne peut pas.
 */
internal class TokenEndpoint(
    private val client: HttpClient,
    private val clientId: String,
    private val clock: Clock = Clock.System,
    private val accountsBase: String = SPOTIFY_ACCOUNTS_BASE,
) {
    suspend fun exchange(
        code: String,
        codeVerifier: String,
        redirectUri: String,
    ): Result<StoredToken> =
        post(
            Parameters.build {
                append("grant_type", "authorization_code")
                append("code", code)
                append("redirect_uri", redirectUri)
                append("client_id", clientId)
                append("code_verifier", codeVerifier)
            },
        )

    /**
     * Spotify ne renvoie pas toujours un nouveau `refresh_token` : quand il est absent,
     * l'ancien reste valable et doit être conservé, faute de quoi la session meurt au
     * renouvellement suivant.
     */
    suspend fun refresh(refreshToken: String): Result<StoredToken> =
        post(
            Parameters.build {
                append("grant_type", "refresh_token")
                append("refresh_token", refreshToken)
                append("client_id", clientId)
            },
        ).map { fresh -> fresh.copy(refreshToken = fresh.refreshToken ?: refreshToken) }

    private suspend fun post(form: Parameters): Result<StoredToken> =
        runCatching { client.submitForm(url = accountsBase + TOKEN_PATH, formParameters = form) }
            .fold(
                onSuccess = { response -> response.parseSpotify<TokenResponseDto>().map { it.toStored() } },
                onFailure = { Result.failure(SpotifyError.Network(it)) },
            )

    private fun TokenResponseDto.toStored(): StoredToken =
        StoredToken(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresAtEpochMs = clock.nowMs() + expiresInSeconds * 1000,
        )
}
