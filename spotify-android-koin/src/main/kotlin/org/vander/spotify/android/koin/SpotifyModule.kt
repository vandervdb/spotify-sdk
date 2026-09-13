package org.vander.spotify.android.koin

import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module
import org.vander.spotify.SpotifyClient
import org.vander.spotify.android.AndroidSpotifyClient
import org.vander.spotify.android.SpotifyAndroid
import org.vander.spotify.android.SpotifyPlayer

/**
 * Câblage Koin de `:spotify-android`. **Facultatif**, comme son équivalent Hilt.
 *
 * Une bibliothèque publiée ne choisit pas l'injection de dépendances de ses consommateurs.
 * `:spotify-android` s'utilise très bien avec une fabrique, un service locator, ou rien du
 * tout ; ces artefacts existent pour ceux qui utilisent déjà l'un ou l'autre.
 *
 * L'application doit déclarer deux liaisons, parce qu'elles lui appartiennent :
 *
 * ```kotlin
 * startKoin {
 *     androidContext(this@MainApplication)
 *     modules(
 *         spotifyModule(),
 *         module {
 *             single {
 *                 SpotifyConfig(
 *                     clientId = BuildConfig.SPOTIFY_CLIENT_ID,
 *                     redirectUri = "vinylotech://callback",
 *                 )
 *             }
 *             single<Authorizer> { ActivityAuthorizer { get<CurrentActivityHolder>().current } }
 *         },
 *     )
 * }
 * ```
 *
 * Le `clientId` est propre à chaque application, et l'`Authorizer` a besoin de l'Activity
 * courante — que seule l'application sait fournir.
 *
 * `androidContext(...)` doit être renseigné dans `startKoin` : ce module en dépend.
 */
public fun spotifyModule(): Module =
    module {
        /**
         * `single` est structurel, pas un confort : le client détient un client HTTP et le
         * lecteur détient la liaison App Remote. Deux instances ouvriraient deux liaisons vers
         * l'application Spotify, et `DataStoreTokenStore` refuserait la seconde — DataStore
         * n'accepte qu'une instance active par fichier.
         */
        single<AndroidSpotifyClient> {
            SpotifyAndroid.create(
                context = androidContext(),
                config = get(),
                authorizer = get(),
            )
        }

        /** Pour un composant qui n'a besoin que du contrat commun, sans le lecteur. */
        single<SpotifyClient> { get<AndroidSpotifyClient>() }

        /** Pour un composant qui ne pilote que la lecture. */
        single<SpotifyPlayer> { get<AndroidSpotifyClient>().player }
    }
