package org.vander.spotify.android

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.vander.spotify.SpotifyError
import org.vander.spotify.android.internal.remote.DefaultSpotifyPlayer
import org.vander.spotify.model.TrackId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpotifyPlayerTest {
    private fun player(connector: FakeRemoteConnector) =
        DefaultSpotifyPlayer(
            clientId = "client-abc",
            redirectUri = "app://callback",
            connector = connector,
            elapsedRealtime = { 1_000L },
        )

    @Test
    fun `connect passe par Connecting puis Connected`() =
        runTest {
            val connector = FakeRemoteConnector()
            val subject = player(connector)

            subject.connection.test {
                assertEquals(PlayerConnection.NotConnected, awaitItem())
                subject.connect().getOrThrow()
                assertEquals(PlayerConnection.Connecting, awaitItem())
                assertEquals(PlayerConnection.Connected, awaitItem())
            }
        }

    @Test
    fun `un echec de connexion est publie et rendu`() =
        runTest {
            val connector = FakeRemoteConnector(failWith = IllegalStateException("Spotify absent"))
            val subject = player(connector)

            val error = subject.connect().exceptionOrNull()

            assertIs<SpotifyError.Network>(error)
            assertIs<PlayerConnection.Failed>(subject.connection.value)
        }

    @Test
    fun `se reconnecter apres disconnect retablit l'abonnement`() =
        runTest {
            // C'est le défaut exact relevé dans l'implémentation d'origine : un drapeau
            // « déjà à l'écoute » qui survivait à la déconnexion, et l'état du lecteur
            // restait muet jusqu'à la mort du processus.
            val connector = FakeRemoteConnector()
            val subject = player(connector)

            subject.connect().getOrThrow()
            subject.disconnect()
            subject.connect().getOrThrow()

            assertEquals(2, connector.connectCalls)
            assertTrue(connector.handles.last().hasLiveSubscription, "le second abonnement doit être vivant")

            connector.handles.last().push(snapshot(title = "Après reconnexion"))
            assertEquals("Après reconnexion", subject.nowPlaying.value?.title)
        }

    @Test
    fun `disconnect annule l'abonnement avant de fermer la liaison`() =
        runTest {
            // L'ordre inverse laisse le SDK pousser un dernier état sur une liaison rendue.
            val connector = FakeRemoteConnector()
            val subject = player(connector)
            subject.connect().getOrThrow()

            subject.disconnect()

            val handle = connector.handles.single()
            assertTrue(handle.subscriptionCancelled)
            assertTrue(handle.disconnected)
            assertEquals(true, handle.disconnectedAfterCancel, "l'abonnement doit être annulé en premier")
        }

    @Test
    fun `un second connect ne rouvre pas de liaison`() =
        runTest {
            val connector = FakeRemoteConnector()
            val subject = player(connector)

            subject.connect().getOrThrow()
            subject.connect().getOrThrow()

            assertEquals(1, connector.connectCalls)
        }

    @Test
    fun `les commandes sont transmises a la liaison`() =
        runTest {
            val connector = FakeRemoteConnector()
            val subject = player(connector)
            subject.connect().getOrThrow()

            subject.play(TrackId("t1")).getOrThrow()
            subject.pause().getOrThrow()
            subject.seekTo(42_000).getOrThrow()
            subject.setRepeat(RepeatMode.Track).getOrThrow()

            assertEquals(
                listOf("play:spotify:track:t1", "pause", "seekTo:42000", "repeat:Track"),
                connector.handles.single().commands,
            )
        }

    @Test
    fun `une commande sans liaison echoue explicitement`() =
        runTest {
            // Ignorer en silence rend un bouton inerte impossible à diagnostiquer.
            val subject = player(FakeRemoteConnector())

            assertIs<SpotifyError.NotSignedIn>(subject.skipNext().exceptionOrNull())
        }

    @Test
    fun `l'etat pousse est traduit en NowPlaying`() =
        runTest {
            val connector = FakeRemoteConnector()
            val subject = player(connector)
            subject.connect().getOrThrow()

            connector.handles.single().push(snapshot(isPaused = true, repeat = 2))

            val state = subject.nowPlaying.value!!
            assertEquals(TrackId("track-1"), state.track)
            assertEquals("Titre", state.title)
            assertTrue(state.isPaused)
            assertEquals(RepeatMode.Context, state.repeat, "le 2 du SDK est la répétition du contexte")
            assertEquals(1_000L, state.emittedAtElapsedMs)
        }

    @Test
    fun `une piste sans identifiant donne un NowPlaying sans TrackId`() =
        runTest {
            // Cas réel : un fichier local, ou une publicité.
            val connector = FakeRemoteConnector()
            val subject = player(connector)
            subject.connect().getOrThrow()

            connector.handles.single().push(snapshot(trackId = ""))

            assertNull(subject.nowPlaying.value?.track)
            assertEquals("Titre", subject.nowPlaying.value?.title)
        }

    @Test
    fun `disconnect efface l'etat de lecture`() =
        runTest {
            val connector = FakeRemoteConnector()
            val subject = player(connector)
            subject.connect().getOrThrow()
            connector.handles.single().push(snapshot())

            subject.disconnect()

            assertNull(subject.nowPlaying.value)
            assertEquals(PlayerConnection.NotConnected, subject.connection.value)
            assertFalse(connector.handles.single().hasLiveSubscription)
        }

    @Test
    fun `RepeatMode traduit les valeurs du SDK et retombe sur Off`() {
        assertEquals(RepeatMode.Off, RepeatMode.fromSdk(0))
        assertEquals(RepeatMode.Track, RepeatMode.fromSdk(1))
        assertEquals(RepeatMode.Context, RepeatMode.fromSdk(2))
        assertEquals(RepeatMode.Off, RepeatMode.fromSdk(99), "une valeur inconnue ne doit pas planter")
    }
}
