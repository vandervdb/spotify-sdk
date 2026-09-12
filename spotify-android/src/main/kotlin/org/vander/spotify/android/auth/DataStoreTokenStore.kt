package org.vander.spotify.android.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import org.vander.spotify.auth.StoredToken
import org.vander.spotify.auth.TokenStore
import java.io.File

/**
 * Conservation du jeton dans un DataStore Preferences.
 *
 * Prend un [DataStore] plutôt qu'un `Context` : le store se teste alors sur un fichier
 * temporaire, sur JVM nue, sans Robolectric ni émulateur. [create] fournit le chemin
 * habituel pour une application.
 *
 * Le contenu n'est pas chiffré. Sur Android le répertoire privé de l'application suffit dans
 * la plupart des cas ; une application qui veut davantage fournit sa propre implémentation de
 * [TokenStore] — c'est précisément pourquoi le contrat est si étroit.
 */
public class DataStoreTokenStore(
    private val dataStore: DataStore<Preferences>,
) : TokenStore {
    override suspend fun load(): StoredToken? {
        val prefs = dataStore.data.first()
        val accessToken = prefs[ACCESS_TOKEN] ?: return null
        return StoredToken(
            accessToken = accessToken,
            refreshToken = prefs[REFRESH_TOKEN],
            expiresAtEpochMs = prefs[EXPIRES_AT] ?: 0L,
        )
    }

    override suspend fun save(token: StoredToken) {
        dataStore.edit { prefs ->
            prefs[ACCESS_TOKEN] = token.accessToken
            prefs[EXPIRES_AT] = token.expiresAtEpochMs
            // Spotify ne renvoie pas toujours un refresh_token au renouvellement ; l'effacer
            // quand il est absent condamnerait la session au renouvellement suivant.
            token.refreshToken?.let { prefs[REFRESH_TOKEN] = it }
        }
    }

    override suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    public companion object {
        private val ACCESS_TOKEN = stringPreferencesKey("access_token")
        private val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        private val EXPIRES_AT = longPreferencesKey("expires_at")

        private const val FILE_NAME = "spotify_session.preferences_pb"

        /**
         * DataStore refuse plusieurs instances actives sur un même fichier. N'appeler qu'une
         * fois par processus, et conserver l'instance rendue.
         */
        public fun create(context: Context): DataStoreTokenStore =
            DataStoreTokenStore(
                PreferenceDataStoreFactory.create {
                    File(context.applicationContext.filesDir, "datastore/$FILE_NAME")
                },
            )
    }
}
