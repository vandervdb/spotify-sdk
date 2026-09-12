package org.vander.spotify

import io.ktor.client.engine.HttpClientEngine
import org.vander.spotify.auth.Authorizer
import org.vander.spotify.auth.TokenStore
import org.vander.spotify.internal.DefaultSpotifyClient
import org.vander.spotify.internal.web.TokenEndpoint
import org.vander.spotify.internal.web.defaultHttpEngine
import org.vander.spotify.internal.web.spotifyHttpClient

/**
 * Point de construction de la lib.
 *
 * Une fabrique, pas un module Hilt ni un composant Dagger : une lib publiée ne choisit pas
 * l'injection de dépendances de ses consommateurs. Le câblage Hilt existe, dans un artefact
 * séparé et facultatif (`:spotify-android-hilt`).
 */
public object Spotify {
    /**
     * @param tokenStore où conserver le jeton. [org.vander.spotify.auth.InMemoryTokenStore]
     *   convient aux tests ; une application veut DataStore ou le Keychain.
     * @param authorizer comment ouvrir l'écran d'autorisation. Fourni par la couche
     *   plateforme — `:spotify-android` en propose une implémentation prête.
     * @param engine moteur HTTP. Par défaut celui de la plateforme ; à surcharger pour
     *   réutiliser un pool existant, ou injecter un `MockEngine` en test.
     */
    public fun create(
        config: SpotifyConfig,
        tokenStore: TokenStore,
        authorizer: Authorizer,
        engine: HttpClientEngine = defaultHttpEngine(),
    ): SpotifyClient {
        val http = spotifyHttpClient(engine)
        return DefaultSpotifyClient(
            config = config,
            tokenStore = tokenStore,
            authorizer = authorizer,
            http = http,
            tokenEndpoint = TokenEndpoint(http, config.clientId),
        )
    }
}
