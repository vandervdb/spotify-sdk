package org.vander.spotify.android.auth

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CancellationException
import org.vander.spotify.SpotifyError
import org.vander.spotify.auth.AuthorizationRequest
import org.vander.spotify.auth.AuthorizationResponse
import org.vander.spotify.auth.Authorizer

/**
 * Autorisation par onglet personnalisé, sur l'URL que `spotify-core` construit lui-même.
 *
 * **Pourquoi pas le SDK d'autorisation de Spotify.** Son chemin app-à-app ne transmet à
 * l'application Spotify que six champs — `VERSION`, `CLIENT_ID`, `REDIRECT_URI`,
 * `RESPONSE_TYPE`, `SCOPES`, `STATE` — et jette les paramètres personnalisés. Le
 * `code_challenge` n'arrive donc jamais, le service émet un code sans lien PKCE, et
 * l'échange échoue sur `Invalid client secret`. Vérifié sur appareil, puis confirmé dans le
 * bytecode de `SpotifyNativeAuthUtil.startAuthActivity()`.
 *
 * Construire l'URL nous-mêmes garantit que les paramètres PKCE arrivent, et permet de ne
 * plus embarquer l'AAR `spotify-auth` du tout. Le prix : on perd la connexion automatique
 * app-à-app ; l'utilisateur voit un onglet Spotify. C'est le flot OAuth natif standard
 * (RFC 8252), et c'est aussi ce que fait la version iOS avec `ASWebAuthenticationSession`.
 *
 * Ne demande qu'un [Context] : [SpotifyAuthActivity] se charge de tout le reste, y compris
 * de détecter un abandon.
 */
public class CustomTabAuthorizer(
    context: Context,
) : Authorizer {
    private val appContext = context.applicationContext

    override suspend fun authorize(request: AuthorizationRequest): Result<AuthorizationResponse> {
        val pending = PendingAuthorization.start()

        val intent =
            Intent(appContext, SpotifyAuthActivity::class.java).apply {
                putExtra(SpotifyAuthActivity.EXTRA_AUTHORIZE_URL, request.url)
                // Lancée depuis un contexte applicatif : sans cette marque, Android refuse.
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

        return try {
            appContext.startActivity(intent)
            pending.await()
        } catch (cancellation: CancellationException) {
            PendingAuthorization.deliver(Result.failure(SpotifyError.AuthorizationCancelled()))
            throw cancellation
        } catch (error: Throwable) {
            PendingAuthorization.deliver(Result.failure(SpotifyError.Network(error)))
            Result.failure(SpotifyError.Network(error))
        }
    }
}
