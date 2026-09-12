package org.vander.spotify.android.testing

import kotlinx.coroutines.test.runTest
import org.vander.spotify.SpotifyError
import org.vander.spotify.android.PlayerConnection
import org.vander.spotify.android.RepeatMode
import org.vander.spotify.model.TrackId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FakeSpotifyPlayerTest {
    @Test
    fun `connect ouvre la liaison`() =
        runTest {
            val subject = FakeSpotifyPlayer()

            subject.connect().getOrThrow()

            assertEquals(PlayerConnection.Connected, subject.connection.value)
            assertEquals(1, subject.connectCalls)
        }

    @Test
    fun `une commande sans liaison echoue, comme dans l'implementation reelle`() =
        runTest {
            // Un double permissif laisserait passer un écran qui commande le lecteur avant
            // de s'y être connecté : le test réussirait, l'application non.
            val subject = FakeSpotifyPlayer()

            assertIs<SpotifyError.NotSignedIn>(subject.pause().exceptionOrNull())
            assertTrue(subject.commands.isEmpty())
        }

    @Test
    fun `les commandes sont journalisees une fois connecte`() =
        runTest {
            val subject = FakeSpotifyPlayer()
            subject.connect().getOrThrow()

            subject.play(TrackId("t1")).getOrThrow()
            subject.seekTo(5_000).getOrThrow()
            subject.setRepeat(RepeatMode.Context).getOrThrow()

            assertEquals(
                listOf(
                    PlayerCommand.Play(TrackId("t1")),
                    PlayerCommand.SeekTo(5_000),
                    PlayerCommand.SetRepeat(RepeatMode.Context),
                ),
                subject.commands,
            )
        }

    @Test
    fun `une panne de connexion injectee ne vaut que pour un appel`() =
        runTest {
            val subject = FakeSpotifyPlayer()
            subject.nextConnectFailure = SpotifyError.Network(IllegalStateException("Spotify absent"))

            assertTrue(subject.connect().isFailure)
            assertIs<PlayerConnection.Failed>(subject.connection.value)

            assertTrue(subject.connect().isSuccess)
            assertEquals(PlayerConnection.Connected, subject.connection.value)
        }

    @Test
    fun `disconnect ferme la liaison et efface l'etat`() =
        runTest {
            val subject = FakeSpotifyPlayer()
            subject.connect().getOrThrow()
            subject.emitNowPlaying(nowPlaying())

            subject.disconnect()

            assertEquals(PlayerConnection.NotConnected, subject.connection.value)
            assertNull(subject.nowPlaying.value)
        }

    @Test
    fun `le client Android combine les deux doubles`() =
        runTest {
            val subject = FakeAndroidSpotifyClient()

            subject.signIn().getOrThrow()
            subject.player.connect().getOrThrow()
            subject.close()

            // close() ferme bien les deux axes : c'est le seul endroit où ils se rejoignent.
            assertTrue(subject.fakeClient.closed)
            assertEquals(PlayerConnection.NotConnected, subject.fakePlayer.connection.value)
        }

    private fun nowPlaying() =
        org.vander.spotify.android.NowPlaying(
            track = TrackId("t1"),
            title = "Titre",
            artist = "Artiste",
            album = "Album",
            coverImageUri = null,
            durationMs = 1_000,
            positionMs = 0,
            isPaused = false,
            shuffle = false,
            repeat = RepeatMode.Off,
            emittedAtElapsedMs = 0,
        )
}
