package org.vander.spotify.android.koin

import org.koin.core.annotation.KoinInternalApi
import org.koin.core.definition.Kind
import org.vander.spotify.SpotifyClient
import org.vander.spotify.SpotifyConfig
import org.vander.spotify.android.AndroidSpotifyClient
import org.vander.spotify.android.SpotifyPlayer
import org.vander.spotify.auth.Authorizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Ce que ces tests vérifient : les **déclarations** du module.
 *
 * Pas l'instanciation : `SpotifyAndroid.create` a besoin d'un `Context` réel, pour le
 * répertoire de DataStore et la liaison App Remote. La vérifier exigerait un appareil ou
 * Robolectric, et ce que l'on gagnerait ne concernerait plus le câblage mais la lib elle-même
 * — déjà couverte par les tests de `:spotify-android`.
 *
 * Le module Hilt équivalent n'a, lui, aucun test : son graphe n'est vérifiable qu'à la
 * compilation d'une application qui l'utilise. C'est une différence réelle entre les deux
 * approches, pas une préférence.
 */
@OptIn(KoinInternalApi::class)
class SpotifyModuleTest {
    private val definitions = spotifyModule().mappings.values.map { it.beanDefinition }

    @Test
    fun `le module declare les trois contrats attendus`() {
        val types = definitions.map { it.primaryType }.toSet()

        assertEquals(
            setOf(AndroidSpotifyClient::class, SpotifyClient::class, SpotifyPlayer::class),
            types,
        )
    }

    @Test
    fun `les trois definitions sont des singletons`() {
        // Structurel : deux instances ouvriraient deux liaisons App Remote, et DataStore
        // refuserait le second store — il n'accepte qu'une instance active par fichier.
        assertTrue(
            definitions.all { it.kind == Kind.Singleton },
            definitions.filterNot { it.kind == Kind.Singleton }.toString(),
        )
    }

    @Test
    fun `le module ne declare ni la configuration ni l'autorisation`() {
        // C'est le contrat, et il mérite un test : le clientId est propre à chaque
        // application, et l'Authorizer a besoin de l'Activity courante. Si l'un des deux
        // apparaissait ici, la lib déciderait à la place de son consommateur.
        val types = definitions.map { it.primaryType }

        assertTrue(SpotifyConfig::class !in types)
        assertTrue(Authorizer::class !in types)
    }

    @Test
    fun `deux appels rendent un module equivalent`() {
        // `spotifyModule()` est une fonction, pas un `val` : deux appels ne doivent pas
        // diverger, sinon un test qui recrée le module testerait autre chose.
        val first = spotifyModule().mappings.values.map { it.beanDefinition.primaryType }.toSet()
        val second = spotifyModule().mappings.values.map { it.beanDefinition.primaryType }.toSet()

        assertEquals(first, second)
    }
}
