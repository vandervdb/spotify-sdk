package org.vander.spotify.android.testing

import org.vander.spotify.SpotifyClient
import org.vander.spotify.android.AndroidSpotifyClient
import org.vander.spotify.android.SpotifyPlayer
import org.vander.spotify.testing.FakeSpotifyClient

/**
 * Double de [AndroidSpotifyClient] : le double du cœur, plus celui du lecteur.
 *
 * Un ViewModel Android n'injecte qu'un type, il ne doit en remplacer qu'un en test. Les deux
 * doubles restent accessibles séparément pour piloter chaque axe.
 */
public class FakeAndroidSpotifyClient(
    public val fakeClient: FakeSpotifyClient = FakeSpotifyClient(),
    public val fakePlayer: FakeSpotifyPlayer = FakeSpotifyPlayer(),
) : AndroidSpotifyClient,
    SpotifyClient by fakeClient {
    override val player: SpotifyPlayer get() = fakePlayer

    /** Ferme les deux axes, comme le fait l'implémentation réelle. */
    override fun close() {
        fakePlayer.disconnect()
        fakeClient.close()
    }
}
