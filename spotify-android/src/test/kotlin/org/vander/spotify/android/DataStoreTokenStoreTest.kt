package org.vander.spotify.android

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.vander.spotify.android.auth.DataStoreTokenStore
import org.vander.spotify.auth.StoredToken
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `DataStoreTokenStore` prend un `DataStore` et non un `Context`, donc ces tests tournent sur
 * JVM nue, sur un fichier temporaire — sans Robolectric ni émulateur.
 */
class DataStoreTokenStoreTest {
    private fun store(
        file: File,
        scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    ) = DataStoreTokenStore(PreferenceDataStoreFactory.create(scope = scope) { file })

    private fun tempFile(): File =
        File.createTempFile("spotify-test", ".preferences_pb").also { it.delete() }

    @Test
    fun `un jeton ecrit se relit a l'identique`() =
        runTest {
            val file = tempFile()
            val subject = store(file)
            val token = StoredToken("access-1", "refresh-1", expiresAtEpochMs = 1_700_000_000_000)

            subject.save(token)

            assertEquals(token, subject.load())
        }

    @Test
    fun `un store vide rend null plutot qu'un jeton vide`() =
        runTest {
            assertNull(store(tempFile()).load())
        }

    @Test
    fun `un renouvellement sans refresh_token conserve l'ancien`() =
        runTest {
            // Spotify n'en renvoie pas systématiquement : l'écraser avec null tuerait la
            // session au renouvellement suivant.
            val subject = store(tempFile())
            subject.save(StoredToken("access-1", "refresh-original", 1_000))

            subject.save(StoredToken("access-2", refreshToken = null, expiresAtEpochMs = 2_000))

            val reloaded = subject.load()!!
            assertEquals("access-2", reloaded.accessToken)
            assertEquals("refresh-original", reloaded.refreshToken)
            assertEquals(2_000, reloaded.expiresAtEpochMs)
        }

    @Test
    fun `clear efface tout`() =
        runTest {
            val subject = store(tempFile())
            subject.save(StoredToken("access-1", "refresh-1", 1_000))

            subject.clear()

            assertNull(subject.load())
        }

    @Test
    fun `le jeton survit a un redemarrage du processus`() =
        runTest {
            // C'est tout l'intérêt du store : franchir un redémarrage.
            //
            // DataStore refuse deux instances actives sur un même fichier — d'où la portée
            // annulée avant d'en rouvrir une. C'est aussi pourquoi `DataStoreTokenStore.create`
            // doit être appelée une seule fois par processus : ce test échouait exactement de
            // cette façon avant d'être écrit correctement.
            val file = tempFile()
            val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            store(file, firstScope).save(StoredToken("access-1", "refresh-1", 9_999))
            firstScope.cancel()

            val reloaded = store(file).load()

            assertEquals("access-1", reloaded?.accessToken)
            assertEquals("refresh-1", reloaded?.refreshToken)
            assertTrue(file.exists())
        }
}
