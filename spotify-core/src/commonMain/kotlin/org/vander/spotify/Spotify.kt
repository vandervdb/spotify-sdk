package org.vander.spotify

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
     *
     * Le moteur HTTP n'est pas un paramètre : l'exposer ferait entrer Ktor dans la surface
     * publique, et lierait la version de Ktor des consommateurs à la nôtre. Un paramètre
     * s'ajoute sans rupture le jour où le besoin est réel ; il ne se retire pas.
     */
    public fun create(
        config: SpotifyConfig,
        tokenStore: TokenStore,
        authorizer: Authorizer,
    ): SpotifyClient {
        val http = spotifyHttpClient(defaultHttpEngine())
        return DefaultSpotifyClient(
            config = config,
            tokenStore = tokenStore,
            authorizer = authorizer,
            http = http,
            tokenEndpoint = TokenEndpoint(http, config.clientId),
        )
    }
}
