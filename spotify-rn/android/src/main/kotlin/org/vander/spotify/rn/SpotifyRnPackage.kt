package org.vander.spotify.rn

import androidx.activity.ComponentActivity
import com.facebook.react.BaseReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.module.model.ReactModuleInfo
import com.facebook.react.module.model.ReactModuleInfoProvider
import org.vander.spotify.SpotifyConfig
import org.vander.spotify.android.AndroidSpotifyClient
import org.vander.spotify.android.SpotifyAndroid
import org.vander.spotify.android.auth.ActivityAuthorizer

/**
 * Enregistrement du module auprès de React Native.
 *
 * L'application hôte ne fournit qu'une [SpotifyConfig] — son `clientId` et sa redirect URI
 * lui appartiennent. L'[ActivityAuthorizer] est construit ici parce que, côté React Native,
 * l'Activity courante se lit sur le contexte : l'hôte JavaScript n'a pas à la connaître.
 *
 * ```kotlin
 * // MainApplication.kt de l'application
 * override fun getPackages() = PackageList(this).packages.apply {
 *     add(SpotifyRnPackage(SpotifyConfig(clientId = …, redirectUri = "monapp://callback")))
 * }
 * ```
 */
class SpotifyRnPackage(
    private val config: SpotifyConfig,
) : BaseReactPackage() {
    /**
     * Une seule instance par contexte : le client détient un client HTTP et le lecteur la
     * liaison App Remote. Deux instances ouvriraient deux liaisons, et le stockage du jeton
     * refuserait la seconde — DataStore n'accepte qu'une instance active par fichier.
     */
    @Volatile
    private var client: AndroidSpotifyClient? = null

    override fun getModule(
        name: String,
        reactContext: ReactApplicationContext,
    ): NativeModule? =
        if (name == NativeSpotifySpec.NAME) {
            SpotifyRnModule(reactContext, clientFor(reactContext))
        } else {
            null
        }

    override fun getReactModuleInfoProvider(): ReactModuleInfoProvider =
        ReactModuleInfoProvider {
            mapOf(
                NativeSpotifySpec.NAME to
                    ReactModuleInfo(
                        NativeSpotifySpec.NAME,
                        NativeSpotifySpec.NAME,
                        false, // canOverrideExistingModule
                        false, // needsEagerInit
                        false, // isCxxModule
                        true, // isTurboModule
                    ),
            )
        }

    private fun clientFor(reactContext: ReactApplicationContext): AndroidSpotifyClient =
        client ?: synchronized(this) {
            client ?: SpotifyAndroid
                .create(
                    context = reactContext.applicationContext,
                    config = config,
                    // Lue à chaque autorisation, jamais capturée : l'Activity courante
                    // change, et en garder une figerait une référence morte.
                    authorizer = ActivityAuthorizer { reactContext.currentActivity as? ComponentActivity },
                ).also { client = it }
        }
}
