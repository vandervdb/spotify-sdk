package org.vander.spotify.internal.web

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json

/**
 * Fabrique le client HTTP de la lib.
 *
 * Un seul client, sans en-tête d'autorisation posé globalement : le jeton est ajouté appel
 * par appel par [SpotifyWebApi], parce qu'il peut être renouvelé entre deux requêtes et
 * qu'un en-tête figé à la construction serait périmé sans que rien ne le signale.
 */
internal fun spotifyHttpClient(engine: HttpClientEngine): HttpClient =
    HttpClient(engine) { installSpotifyDefaults() }

internal fun <T : io.ktor.client.engine.HttpClientEngineConfig> HttpClientConfig<T>.installSpotifyDefaults() {
    install(ContentNegotiation) { json(SpotifyJson) }
    expectSuccess = false
}
