package org.vander.spotify.android

import android.content.Context
import org.vander.spotify.Spotify
import org.vander.spotify.SpotifyClient
import org.vander.spotify.SpotifyConfig
import org.vander.spotify.android.internal.remote.DefaultSpotifyPlayer
import org.vander.spotify.android.internal.remote.SdkRemoteConnector
import org.vander.spotify.auth.Authorizer
import org.vander.spotify.auth.TokenStore

/**
 * Client Android : tout ce que fait [SpotifyClient], plus le contrôle de lecture local.
 *
 * Un seul type à injecter dans un ViewModel, au lieu d'une façade et d'un lecteur à tenir
 * ensemble. L'héritage exprime le fait qu'Android est un sur-ensemble : le code écrit contre
 * [SpotifyClient] fonctionne tel quel, et seul ce qui touche à [player] est spécifique.
 */
public interface AndroidSpotifyClient : SpotifyClient {
    public val player: SpotifyPlayer
}

/**
 * Construction de la lib côté Android.
 *
 * Une fabrique, pas un module Hilt : une bibliothèque publiée ne choisit pas l'injection de
 * dépendances de ses consommateurs. Le câblage Hilt vit dans `:spotify-android-hilt`,
 * facultatif.
 */
public object SpotifyAndroid {
    /**
     * @param authorizer comment ouvrir l'écran d'autorisation. En général
     *   [org.vander.spotify.android.auth.ActivityAuthorizer], construit avec un fournisseur
     *   d'Activity courante.
     * @param tokenStore où conserver le jeton. Par défaut un DataStore dans le répertoire
     *   privé de l'application.
     *
     * @throws IllegalArgumentException si `config.redirectUri` est manifestement incompatible
     *   avec ce que le SDK Spotify attend — mieux vaut échouer ici qu'au retour de l'écran
     *   d'autorisation, où le diagnostic est bien plus coûteux.
     */
    public fun create(
        context: Context,
        config: SpotifyConfig,
        authorizer: Authorizer,
        tokenStore: TokenStore = org.vander.spotify.android.auth.DataStoreTokenStore.create(context),
    ): AndroidSpotifyClient {
        config.requireUsableRedirectUri()

        val core = Spotify.create(config, tokenStore, authorizer)

        val player =
            DefaultSpotifyPlayer(
                clientId = config.clientId,
                redirectUri = config.redirectUri,
                connector = SdkRemoteConnector(context.applicationContext),
            )

        return DefaultAndroidSpotifyClient(core, player)
    }

    /**
     * Le SDK Spotify déclare `LoginActivity` avec un intent-filter dont le schéma et l'hôte
     * viennent des placeholders de manifeste `redirectSchemeName` et `redirectHostName`,
     * fixés à la compilation de l'application. Ils doivent correspondre à cette redirect URI.
     *
     * On ne peut pas lire ces placeholders depuis la lib, mais on peut vérifier que l'URI a
     * la forme qu'ils exigent : un schéma personnalisé avec un hôte.
     */
    private fun SpotifyConfig.requireUsableRedirectUri() {
        val match = REDIRECT_URI_SHAPE.matchEntire(redirectUri)
        require(match != null) {
            "redirectUri « $redirectUri » doit avoir la forme « schema://hote », et schema/hote " +
                "doivent correspondre aux placeholders redirectSchemeName et redirectHostName " +
                "du manifeste de l'application. Voir spotify-android/README.md."
        }
    }

    private val REDIRECT_URI_SHAPE = Regex("""^[a-zA-Z][a-zA-Z0-9+.\-]*://[^/?#]+/?$""")
}

private class DefaultAndroidSpotifyClient(
    private val core: SpotifyClient,
    override val player: SpotifyPlayer,
) : AndroidSpotifyClient,
    SpotifyClient by core {
    /**
     * Ferme les deux axes.
     *
     * Seul endroit où ils se rejoignent, et c'est volontaire : `signOut()` ne coupe pas la
     * liaison App Remote, et `player.disconnect()` ne déconnecte pas l'utilisateur.
     */
    override fun close() {
        player.disconnect()
        core.close()
    }
}
