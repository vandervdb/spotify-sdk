package org.vander.spotify

import io.ktor.http.HttpHeaders
import kotlinx.coroutines.test.runTest
import org.vander.spotify.internal.time.Clock
import org.vander.spotify.internal.web.TokenEndpoint
import org.vander.spotify.internal.web.spotifyHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TokenEndpointTest {
    private val fixedNow = 1_000_000L
    private val clock = Clock { fixedNow }

    private fun endpoint(recording: RecordingEngine) =
        TokenEndpoint(
            client = spotifyHttpClient(recording.engine),
            clientId = "client-abc",
            clock = clock,
            accountsBase = TEST_ACCOUNTS,
        )

    @Test
    fun `l'echange envoie le code_verifier et aucun secret`() =
        runTest {
            val recording = RecordingEngine { MockResponse(tokenJson()) }

            val token = endpoint(recording).exchange("the-code", "the-verifier", "app://cb").getOrThrow()

            val request = recording.requests.single()
            val form = request.formBody()

            // Ce que PKCE exige d'envoyer…
            assertTrue(form.contains("grant_type=authorization_code"), form)
            assertTrue(form.contains("code=the-code"), form)
            assertTrue(form.contains("code_verifier=the-verifier"), form)
            assertTrue(form.contains("client_id=client-abc"), form)

            // …et ce qui ne doit jamais partir. C'est le test qui rend la lib publiable.
            assertFalse(form.contains("client_secret"), form)
            assertNull(request.headers[HttpHeaders.Authorization])

            assertEquals("access-1", token.accessToken)
            assertEquals(fixedNow + 3600 * 1000, token.expiresAtEpochMs)
        }

    @Test
    fun `le renouvellement conserve l'ancien refresh_token quand Spotify n'en rend pas`() =
        runTest {
            // Comportement réel de Spotify, et piège classique : écraser avec `null` tue la
            // session au renouvellement suivant.
            val recording = RecordingEngine { MockResponse(tokenJson(refreshToken = null)) }

            val token = endpoint(recording).refresh("refresh-original").getOrThrow()

            assertEquals("refresh-original", token.refreshToken)
            assertTrue(recording.requests.single().formBody().contains("grant_type=refresh_token"))
        }

    @Test
    fun `un refresh_token revoque rend Unauthorized`() =
        runTest {
            val recording =
                RecordingEngine {
                    MockResponse(
                        """{"error":"invalid_grant","error_description":"Refresh token revoked"}""",
                        status = 400,
                    )
                }

            val error = endpoint(recording).refresh("dead").exceptionOrNull()

            assertIs<SpotifyError.Unauthorized>(error)
        }

    @Test
    fun `une panne de transport devient SpotifyError Network`() =
        runTest {
            val recording = RecordingEngine { throw IllegalStateException("socket fermée") }

            val error = endpoint(recording).exchange("c", "v", "app://cb").exceptionOrNull()

            assertIs<SpotifyError.Network>(error)
        }
}
