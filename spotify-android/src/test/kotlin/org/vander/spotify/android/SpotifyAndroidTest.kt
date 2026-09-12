package org.vander.spotify.android

import org.vander.spotify.SpotifyConfig
import org.vander.spotify.SpotifyScope
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SpotifyConfigShapeTest {
    /**
     * Le SDK Spotify déclare son `LoginActivity` avec un intent-filter paramétré par les
     * placeholders de manifeste de l'application. Une redirect URI qui n'a pas la forme
     * `schema://hote` ne peut pas leur correspondre, et l'erreur ne se verrait qu'au retour
     * de l'écran d'autorisation — au pire endroit pour diagnostiquer.
     */
    @Test
    fun `une redirect URI sans schema personnalise est refusee tot`() {
        val error =
            assertFailsWith<IllegalArgumentException> {
                validate("pas-une-uri")
            }

        assertTrue(error.message!!.contains("redirectSchemeName"), error.message!!)
    }

    @Test
    fun `une redirect URI bien formee passe`() {
        validate("vinylotech://callback")
        validate("org-vander-app://callback/")
    }

    @Test
    fun `SpotifyConfig refuse un clientId vide`() {
        assertFailsWith<IllegalArgumentException> {
            SpotifyConfig(clientId = "  ", redirectUri = "app://cb")
        }
    }

    @Test
    fun `SpotifyConfig refuse une liste de permissions vide`() {
        assertFailsWith<IllegalArgumentException> {
            SpotifyConfig(clientId = "c", redirectUri = "app://cb", scopes = emptySet())
        }
    }

    @Test
    fun `les permissions par defaut couvrent lecture et bibliotheque`() {
        val defaults = SpotifyScope.DEFAULT

        assertTrue(SpotifyScope.Streaming in defaults)
        assertTrue(SpotifyScope.UserLibraryRead in defaults)
        assertTrue(SpotifyScope.UserLibraryModify in defaults)
    }

    /** Rejoue la validation de `SpotifyAndroid.create` sans avoir besoin d'un Context. */
    private fun validate(redirectUri: String) {
        val shape = Regex("""^[a-zA-Z][a-zA-Z0-9+.\-]*://[^/?#]+/?$""")
        require(shape.matchEntire(redirectUri) != null) {
            "redirectUri « $redirectUri » doit avoir la forme « schema://hote », et schema/hote " +
                "doivent correspondre aux placeholders redirectSchemeName et redirectHostName " +
                "du manifeste de l'application. Voir spotify-android/README.md."
        }
    }
}
