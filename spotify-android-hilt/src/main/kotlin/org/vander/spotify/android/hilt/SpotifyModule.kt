package org.vander.spotify.android.hilt

import android.content.Context
import dagger.BindsOptionalOf
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.vander.spotify.SpotifyClient
import org.vander.spotify.SpotifyConfig
import org.vander.spotify.android.AndroidSpotifyClient
import org.vander.spotify.android.SpotifyAndroid
import org.vander.spotify.android.SpotifyPlayer
import org.vander.spotify.android.auth.DataStoreTokenStore
import org.vander.spotify.auth.Authorizer
import org.vander.spotify.auth.TokenStore
import java.util.Optional
import javax.inject.Singleton

/**
 * Câblage Hilt, dans un artefact **facultatif**.
 *
 * Une bibliothèque publiée ne choisit pas l'injection de dépendances de ses consommateurs :
 * `:spotify-android` s'utilise très bien avec une fabrique, un service locator ou rien du
 * tout. Ce module existe pour ceux qui utilisent Hilt, et il ne leur coûte qu'une ligne de
 * dépendance.
 *
 * L'application doit fournir deux liaisons, parce qu'elles lui appartiennent :
 *
 * ```kotlin
 * @Module
 * @InstallIn(SingletonComponent::class)
 * object AppSpotifyModule {
 *     @Provides fun config(): SpotifyConfig = SpotifyConfig(
 *         clientId = BuildConfig.SPOTIFY_CLIENT_ID,
 *         redirectUri = "vinylotech://callback",
 *     )
 *
 *     @Provides fun authorizer(holder: CurrentActivityHolder): Authorizer =
 *         ActivityAuthorizer { holder.current }
 * }
 * ```
 *
 * Une troisième liaison est **facultative** : un [TokenStore]. Sans elle, le jeton est conservé en
 * clair dans un DataStore ; une application qui veut le chiffrer fournit le sien :
 *
 * ```kotlin
 * @Provides @Singleton fun tokenStore(@ApplicationContext context: Context): TokenStore = MonStoreChiffre(context)
 * ```
 *
 * Le `clientId` ne peut pas venir d'ici : il est propre à chaque application. Quant à
 * l'[Authorizer], il a besoin de l'Activity courante, que seule l'application sait fournir.
 */
@Module
@InstallIn(SingletonComponent::class)
public object SpotifyModule {
    /**
     * `@Singleton` est structurel, pas un confort : le client détient un client HTTP et le
     * lecteur détient la liaison App Remote. Deux instances ouvriraient deux liaisons vers
     * l'application Spotify, et `DataStoreTokenStore` refuserait la seconde — DataStore
     * n'accepte qu'une instance active par fichier.
     */
    @Provides
    @Singleton
    public fun provideAndroidSpotifyClient(
        @ApplicationContext context: Context,
        config: SpotifyConfig,
        authorizer: Authorizer,
        tokenStore: Optional<TokenStore>,
    ): AndroidSpotifyClient =
        SpotifyAndroid.create(
            context = context,
            config = config,
            authorizer = authorizer,
            // `orElseGet` et non `orElse` : le DataStore par défaut n'accepte qu'une instance par
            // fichier, on ne le construit donc que si l'application n'a pas fourni le sien.
            tokenStore = tokenStore.orElseGet { DataStoreTokenStore.create(context) },
        )

    /** Pour un ViewModel qui n'a besoin que du contrat commun, sans le lecteur. */
    @Provides
    public fun provideSpotifyClient(client: AndroidSpotifyClient): SpotifyClient = client

    /** Pour un ViewModel qui ne pilote que la lecture. */
    @Provides
    public fun provideSpotifyPlayer(client: AndroidSpotifyClient): SpotifyPlayer = client.player
}

/**
 * Déclare le [TokenStore] comme liaison facultative. Un module distinct et abstrait : `@BindsOptionalOf`
 * n'existe que sur une méthode abstraite, et [SpotifyModule] est un `object`.
 */
@Module
@InstallIn(SingletonComponent::class)
public abstract class SpotifyOptionalBindings {
    @BindsOptionalOf
    public abstract fun optionalTokenStore(): TokenStore
}
