package org.vander.spotify.testing

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.vander.spotify.SpotifyError
import org.vander.spotify.auth.SessionState
import org.vander.spotify.model.Playlist
import org.vander.spotify.model.PlaylistCollection
import org.vander.spotify.model.PlaylistId
import org.vander.spotify.model.TrackId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Un double publié comme artefact doit être testé comme n'importe quel code livré.
 *
 * Un double dont les corps sont vides passe les tests qu'il est censé servir, et l'erreur se
 * découvre chez le consommateur. Ces tests vérifient qu'il a bien un comportement.
 */
class FakeSpotifyClientTest {
    @Test
    fun `signIn fait basculer la session`() =
        runTest {
            val subject = FakeSpotifyClient()

            subject.session.test {
                assertEquals(SessionState.SignedOut, awaitItem())
                subject.signIn().getOrThrow()
                assertEquals(SessionState.Authorized, awaitItem())
            }
        }

    @Test
    fun `un signIn scenarise en echec laisse la session en Failed`() =
        runTest {
            val subject = FakeSpotifyClient()
            subject.nextSignInResult = Result.failure(SpotifyError.AuthorizationCancelled())

            val error = subject.signIn().exceptionOrNull()

            assertIs<SpotifyError.AuthorizationCancelled>(error)
            assertIs<SessionState.Failed>(subject.session.value)
        }

    @Test
    fun `un scenario ne vaut que pour un appel`() =
        runTest {
            // Sans ça, impossible de tester une reprise après erreur sans reconstruire
            // le double — et un test qui reconstruit son double teste deux choses.
            val subject = FakeSpotifyClient()
            subject.nextSignInResult = Result.failure(SpotifyError.AuthorizationCancelled())

            subject.signIn()
            val second = subject.signIn()

            assertTrue(second.isSuccess)
            assertEquals(SessionState.Authorized, subject.session.value)
        }

    @Test
    fun `setSaved modifie un etat que isSaved relit`() =
        runTest {
            val subject = FakeSpotifyClient()
            val track = TrackId("t1")
            assertFalse(subject.isSaved(track).getOrThrow())

            subject.setSaved(track, saved = true).getOrThrow()
            assertTrue(subject.isSaved(track).getOrThrow())

            subject.setSaved(track, saved = false).getOrThrow()
            assertFalse(subject.isSaved(track).getOrThrow())
        }

    @Test
    fun `les titres deja likes sont respectes a la construction`() =
        runTest {
            val subject = FakeSpotifyClient(savedTracks = setOf(TrackId("deja")))

            assertTrue(subject.isSaved(TrackId("deja")).getOrThrow())
        }

    @Test
    fun `signOut vide les caches`() =
        runTest {
            val subject = FakeSpotifyClient(initialSession = SessionState.Authorized)
            subject.emitPlaylists(PlaylistCollection(listOf(Playlist(PlaylistId("p1"), "X")), total = 1))

            subject.signOut()

            assertEquals(SessionState.SignedOut, subject.session.value)
            assertTrue(subject.playlists.value.items.isEmpty())
        }

    @Test
    fun `les appels sont journalises dans l'ordre`() =
        runTest {
            val subject = FakeSpotifyClient()

            subject.signIn()
            subject.refreshPlaylists()
            subject.setSaved(TrackId("t1"), saved = true)

            assertEquals(
                listOf(
                    SpotifyCall.SignIn,
                    SpotifyCall.RefreshPlaylists,
                    SpotifyCall.SetSaved(TrackId("t1"), saved = true),
                ),
                subject.calls,
            )
        }

    @Test
    fun `une panne injectee touche le prochain appel reseau`() =
        runTest {
            val subject = FakeSpotifyClient()
            subject.nextFailure = SpotifyError.Unauthorized("jeton expiré")

            assertIs<SpotifyError.Unauthorized>(subject.refreshQueue().exceptionOrNull())
            assertTrue(subject.refreshQueue().isSuccess, "la panne ne vaut que pour un appel")
        }

    @Test
    fun `refreshPlaylists rend ce qui a ete emis`() =
        runTest {
            val subject = FakeSpotifyClient()
            val page = PlaylistCollection(listOf(Playlist(PlaylistId("p1"), "Matin")), total = 1)
            subject.emitPlaylists(page)

            assertEquals(page, subject.refreshPlaylists().getOrThrow())
        }

    @Test
    fun `close est observable`() {
        val subject = FakeSpotifyClient()
        assertFalse(subject.closed)

        subject.close()

        assertTrue(subject.closed, "une fuite de client doit pouvoir être détectée en test")
    }
}
