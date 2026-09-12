package org.vander.spotify.android.auth

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.spotify.sdk.android.auth.AuthorizationClient
import com.spotify.sdk.android.auth.AuthorizationRequest as SdkAuthorizationRequest
import com.spotify.sdk.android.auth.AuthorizationResponse as SdkAuthorizationResponse
import kotlinx.coroutines.suspendCancellableCoroutine
import org.vander.spotify.SpotifyError
import org.vander.spotify.auth.AuthorizationRequest
import org.vander.spotify.auth.AuthorizationResponse
import org.vander.spotify.auth.Authorizer
import java.util.UUID
import kotlin.coroutines.resume

/**
 * Autorisation via le SDK Spotify, qui délègue à l'application Spotify quand elle est
 * installée et retombe sur un onglet personnalisé sinon.
 *
 * Le lanceur est enregistré sur `activityResultRegistry` plutôt que par
 * `registerForActivityResult`. La raison est une contrainte de la plateforme :
 * `registerForActivityResult` doit être appelé avant que l'Activity n'atteigne STARTED, ce
 * qu'une bibliothèque appelée à un moment quelconque ne peut pas garantir. Le registre, lui,
 * accepte un enregistrement tardif.
 *
 * @param activityProvider appelé au moment de l'autorisation, pas à la construction :
 *   l'Activity courante change, et en capturer une figerait une référence morte.
 */
public class ActivityAuthorizer(
    private val activityProvider: () -> ComponentActivity?,
) : Authorizer {
    override suspend fun authorize(request: AuthorizationRequest): Result<AuthorizationResponse> {
        val activity =
            activityProvider()
                ?: return Result.failure(
                    SpotifyError.AuthorizationCancelled(),
                )

        return suspendCancellableCoroutine { continuation ->
            // Clé unique : deux autorisations concurrentes ne doivent pas se marcher dessus,
            // et une clé réutilisée après un changement de configuration livrerait le
            // résultat au mauvais appelant.
            val key = "org.vander.spotify.auth.${UUID.randomUUID()}"

            val launcher =
                activity.activityResultRegistry.register(
                    key,
                    ActivityResultContracts.StartActivityForResult(),
                ) { result ->
                    continuation.resume(result.data.toAuthorizationResult())
                }

            continuation.invokeOnCancellation { launcher.unregister() }

            runCatching {
                launcher.launch(
                    AuthorizationClient.createLoginActivityIntent(activity, request.toSdkRequest()),
                )
            }.onFailure { error ->
                launcher.unregister()
                continuation.resume(Result.failure(SpotifyError.Network(error)))
            }
        }
    }

    /**
     * Reconstruit la requête avec le builder du SDK.
     *
     * Les paramètres PKCE passent par `setCustomParam` : le SDK ne les connaît pas
     * nativement, mais il recopie les paramètres personnalisés dans l'URL `/authorize`.
     * C'est ce qui permet d'utiliser le flot PKCE sans renoncer au confort de l'écran
     * d'autorisation natif de l'application Spotify.
     */
    private fun AuthorizationRequest.toSdkRequest(): SdkAuthorizationRequest =
        SdkAuthorizationRequest
            .Builder(clientId, SdkAuthorizationResponse.Type.CODE, redirectUri)
            .setScopes(scopes.map { it.wireName }.toTypedArray())
            .setCustomParam("code_challenge", codeChallenge)
            .setCustomParam("code_challenge_method", codeChallengeMethod)
            .setState(state)
            .setShowDialog(false)
            .build()

    private fun Intent?.toAuthorizationResult(): Result<AuthorizationResponse> {
        // Un `data` nul est le cas normal du retour arrière : l'utilisateur a renoncé.
        val intent = this ?: return Result.failure(SpotifyError.AuthorizationCancelled())
        val response = AuthorizationClient.getResponse(android.app.Activity.RESULT_OK, intent)

        return when (response.type) {
            SdkAuthorizationResponse.Type.CODE ->
                Result.success(
                    AuthorizationResponse(code = response.code, state = response.state.orEmpty()),
                )

            SdkAuthorizationResponse.Type.ERROR ->
                Result.failure(SpotifyError.Unauthorized(response.error ?: "erreur inconnue"))

            SdkAuthorizationResponse.Type.EMPTY ->
                Result.failure(SpotifyError.AuthorizationCancelled())

            else ->
                Result.failure(
                    SpotifyError.Unauthorized("réponse inattendue : ${response.type}"),
                )
        }
    }
}
