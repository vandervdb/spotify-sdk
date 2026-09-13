package org.vander.spotify.sample

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import org.vander.spotify.SpotifyConfig
import org.vander.spotify.android.AndroidSpotifyClient
import org.vander.spotify.android.SpotifyAndroid
import org.vander.spotify.android.auth.CustomTabAuthorizer

/**
 * Application de démonstration, et surtout **preuve de fonctionnement**.
 *
 * Tout le reste du dépôt est vérifié contre des contrats : 71 tests, aucun appel réel. Cette
 * application est le seul endroit où la bibliothèque parle vraiment à Spotify — autorisation
 * PKCE contre le service de comptes, puis App Remote contre l'application installée.
 *
 * Elle sert aussi de documentation : un exemple qui compile vaut mieux qu'un extrait de
 * README, qui lui ne casse jamais.
 */
class MainActivity : ComponentActivity() {
    private lateinit var spotify: AndroidSpotifyClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // L'application fournit sa configuration ; la bibliothèque ne lit aucun BuildConfig
        // et ne connaît pas le clientId avant qu'on le lui donne.
        spotify =
            SpotifyAndroid.create(
                context = applicationContext,
                config =
                    SpotifyConfig(
                        clientId = BuildConfig.SPOTIFY_CLIENT_ID,
                        redirectUri = REDIRECT_URI,
                    ),
                // Ne demande qu'un Context : l'Activity d'autorisation est fournie par la
                // bibliothèque et se charge d'ouvrir l'onglet, de recevoir la redirection
                // et de détecter un abandon.
                authorizer = CustomTabAuthorizer(applicationContext),
            )

        Log.i(TAG, "client créé — clientId présent : ${BuildConfig.SPOTIFY_CLIENT_ID.isNotBlank()}")

        setContent {
            MaterialTheme {
                SampleScreen(spotify = spotify, log = { message -> Log.i(TAG, message) })
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        spotify.close()
    }

    private companion object {
        const val TAG = "SpotifySample"

        /**
         * Doit correspondre à une redirect URI enregistrée sur le dashboard pour ce clientId,
         * et aux placeholders de manifeste. Celle-ci est celle de vinyl-otech, dont on
         * réutilise l'identifiant.
         */
        const val REDIRECT_URI = "org-vander-androidapp://callback"
    }
}
