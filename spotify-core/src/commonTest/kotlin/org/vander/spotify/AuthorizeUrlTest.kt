package org.vander.spotify

import io.ktor.http.Url
import org.vander.spotify.auth.Pkce
import org.vander.spotify.internal.web.buildAuthorizeUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuthorizeUrlTest {
    private val challenge = Pkce.fromVerifier("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk")

    @Test
    fun `l'URL porte les parametres PKCE`() {
        val url = Url(buildAuthorizeUrl(testConfig, challenge, "state-xyz", TEST_ACCOUNTS))

        assertEquals("code", url.parameters["response_type"])
        assertEquals("S256", url.parameters["code_challenge_method"])
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", url.parameters["code_challenge"])
        assertEquals("state-xyz", url.parameters["state"])
        assertEquals("client-abc", url.parameters["client_id"])
        assertEquals("vinylotech://callback", url.parameters["redirect_uri"])
    }

    @Test
    fun `l'URL ne porte jamais de secret ni de verifier`() {
        // Le verifier reste sur l'appareil jusqu'à l'échange : le publier dans l'URL
        // d'autorisation annulerait tout l'intérêt de PKCE.
        val raw = buildAuthorizeUrl(testConfig, challenge, "state-xyz", TEST_ACCOUNTS)

        assertFalse(raw.contains("client_secret"), raw)
        assertFalse(raw.contains(challenge.verifier), raw)
    }

    @Test
    fun `les permissions sont jointes par des espaces`() {
        val url = Url(buildAuthorizeUrl(testConfig, challenge, "s", TEST_ACCOUNTS))
        val scope = url.parameters["scope"].orEmpty()

        assertTrue(scope.contains("streaming"), scope)
        assertTrue(scope.contains("user-library-read"), scope)
        assertEquals(2, scope.split(" ").size, scope)
    }
}
