package org.vander.spotify

import app.cash.turbine.test
import io.ktor.client.request.HttpRequestData
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.vander.spotify.auth.AuthorizationResponse
import org.vander.spotify.auth.InMemoryTokenStore
import org.vander.spotify.auth.SessionState
import org.vander.spotify.auth.StoredToken
import org.vander.spotify.internal.DefaultSpotifyClient
import org.vander.spotify.internal.time.Clock
import org.vander.spotify.internal.web.TokenEndpoint
import org.vander.spotify.internal.web.spotifyHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpotifyClientTest {
    private val now = 5_000_000L
    private val clock = Clock { now }
    private val store = InMemoryTokenStore()

    private fun client(
        recording: RecordingEngine,
        authorizer: FakeAuthorizer,
    ): DefaultSpotifyClient {
        val http = spotifyHttpClient(recording.engine)
        return DefaultSpotifyClient(
            config = testConfig,
            tokenStore = store,
            authorizer = authorizer,
            http = http,
            tokenEndpoint = TokenEndpoint(http, testConfig.clientId, clock, TEST_ACCOUNTS),
            clock = clock,
            accountsBase = TEST_ACCOUNTS,
            apiBase = TEST_API,
        )
    }

    private fun echoState() = FakeAuthorizer { Result.success(AuthorizationResponse("code-1", it.state)) }

    @Test
    fun `signIn traverse Authorizing puis Authorized`() =
        runTest {
            val authorizer = echoState()
            val subject = client(RecordingEngine { MockResponse(tokenJson()) }, authorizer)

            subject.session.test {
                assertEquals(SessionState.SignedOut, awaitItem())

                subject.signIn().getOrThrow()

                assertEquals(SessionState.Authorizing, awaitItem())
                assertEquals(SessionState.Authorized, awaitItem())
            }

            assertEquals("access-1", store.load()?.accessToken)
            // Le verifier envoyé doit être celui qui a servi à fabriquer le challenge de l'URL.
            assertTrue(authorizer.lastRequest!!.url.contains("code_challenge="))
        }

    @Test
    fun `un state qui ne correspond pas fait echouer sans echanger le code`() =
        runTest {
            // Protection CSRF : une réponse injectée ne doit jamais atteindre /api/token.
            val recording = RecordingEngine { MockResponse(tokenJson()) }
            val attacker = FakeAuthorizer { Result.success(AuthorizationResponse("code-injecte", "autre-state")) }

            val error = client(recording, attacker).signIn().exceptionOrNull()

            assertIs<SpotifyError.StateMismatch>(error)
            assertTrue(recording.requests.isEmpty(), "aucun échange de jeton ne doit avoir lieu")
            assertNull(store.load())
        }

    @Test
    fun `une autorisation annulee laisse la session en echec`() =
        runTest {
            val cancelling = FakeAuthorizer { Result.failure(SpotifyError.AuthorizationCancelled()) }
            val subject = client(RecordingEngine { MockResponse("{}") }, cancelling)

            subject.signIn()

            val state = subject.session.value
            assertIs<SessionState.Failed>(state)
            assertIs<SpotifyError.AuthorizationCancelled>(state.error)
        }

    @Test
    fun `un jeton expire est renouvele avant l'appel`() =
        runTest {
            store.save(StoredToken("vieux", "refresh-1", expiresAtEpochMs = now - 1))
            val recording =
                RecordingEngine { request ->
                    if (request.isTokenCall()) {
                        MockResponse(tokenJson(accessToken = "neuf", refreshToken = "refresh-2"))
                    } else {
                        MockResponse("""{"id":"u1"}""")
                    }
                }

            client(recording, echoState()).currentUser().getOrThrow()

            assertEquals(2, recording.requests.size, "un renouvellement puis l'appel")
            assertEquals("neuf", store.load()?.accessToken)
            assertEquals("refresh-2", store.load()?.refreshToken)
            assertEquals("Bearer neuf", recording.requests.last().headers["Authorization"])
        }

    @Test
    fun `un jeton qui expire dans moins d'une minute est deja renouvele`() =
        runTest {
            // Sans marge, un jeton valide au moment du contrôle peut expirer en vol.
            store.save(StoredToken("limite", "refresh-1", expiresAtEpochMs = now + 30_000))
            val recording =
                RecordingEngine { request ->
                    if (request.isTokenCall()) MockResponse(tokenJson(accessToken = "neuf")) else MockResponse("""{"id":"u"}""")
                }

            client(recording, echoState()).currentUser().getOrThrow()

            assertTrue(recording.requests.first().isTokenCall(), "le renouvellement doit précéder l'appel")
        }

    @Test
    fun `deux appels concurrents ne declenchent qu'un seul renouvellement`() =
        runTest {
            // Deux renouvellements en parallèle invalideraient le refresh_token l'un de
            // l'autre, et la session mourrait sans cause visible.
            store.save(StoredToken("vieux", "refresh-1", expiresAtEpochMs = now - 1))
            val recording =
                RecordingEngine { request ->
                    if (request.isTokenCall()) MockResponse(tokenJson(accessToken = "neuf")) else MockResponse("""{"id":"u"}""")
                }
            val subject = client(recording, echoState())

            listOf(
                async { subject.currentUser() },
                async { subject.currentUser() },
            ).awaitAll().forEach { it.getOrThrow() }

            assertEquals(1, recording.requests.count { it.isTokenCall() })
        }

    @Test
    fun `un refresh refuse efface le jeton local`() =
        runTest {
            store.save(StoredToken("vieux", "revoque", expiresAtEpochMs = now - 1))
            val recording =
                RecordingEngine {
                    MockResponse("""{"error":"invalid_grant","error_description":"revoked"}""", status = 400)
                }

            val error = client(recording, echoState()).refreshPlaylists().exceptionOrNull()

            assertIs<SpotifyError.Unauthorized>(error)
            assertNull(store.load(), "un refresh refusé n'est pas récupérable")
        }

    @Test
    fun `signOut efface le jeton et les caches`() =
        runTest {
            store.save(StoredToken("a", "r", expiresAtEpochMs = now + 3_600_000))
            val recording =
                RecordingEngine { MockResponse("""{"total":1,"items":[{"id":"p1","name":"X"}]}""") }
            val subject = client(recording, echoState())
            subject.refreshPlaylists().getOrThrow()
            assertEquals(1, subject.playlists.value.items.size)

            subject.signOut()

            assertEquals(SessionState.SignedOut, subject.session.value)
            assertNull(store.load())
            assertTrue(subject.playlists.value.items.isEmpty())
        }

    @Test
    fun `sans session les appels echouent en NotSignedIn`() =
        runTest {
            val recording = RecordingEngine { MockResponse("{}") }

            val error = client(recording, echoState()).refreshQueue().exceptionOrNull()

            assertIs<SpotifyError.NotSignedIn>(error)
            assertTrue(recording.requests.isEmpty())
        }

    private fun HttpRequestData.isTokenCall(): Boolean = url.encodedPath.endsWith("/api/token")
}
