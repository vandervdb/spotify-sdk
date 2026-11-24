package org.vander.spotify

import org.vander.spotify.model.TrackId
import org.vander.spotify.model.PlaylistId
import org.vander.spotify.model.SearchType
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
import kotlin.test.assertFailsWith
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
    fun `refreshSavedAlbums lit artistes, annee et nombre de titres`() =
        runTest {
            store.save(StoredToken("a", "r", expiresAtEpochMs = now + 3_600_000))
            val recording =
                RecordingEngine {
                    MockResponse(
                        """{"total":243,"items":[{"added_at":"x","album":{"id":"a1","name":"Nuits blanches",
                        "release_date":"2023-05","total_tracks":11,"artists":[{"id":"r1","name":"Elia Faure"}],
                        "images":[{"url":"u","width":640,"height":640}]}},{"album":null}]}""",
                    )
                }
            val subject = client(recording, echoState())

            val page = subject.refreshSavedAlbums().getOrThrow()

            val album = page.items.single()
            assertEquals(243, page.total)
            assertEquals("Elia Faure", album.artistNames)
            assertEquals(2023, album.releaseYear)
            assertEquals(11, album.trackCount)
            assertEquals(page, subject.savedAlbums.value)
            assertTrue(recording.requests.any { it.url.encodedPath.endsWith("/me/albums") })
        }

    @Test
    fun `restoreSession reprend le jeton stocke sans ouvrir l'autorisation`() =
        runTest {
            store.save(StoredToken("a", "r", expiresAtEpochMs = now + 3_600_000))
            val recording = RecordingEngine { MockResponse("{}") }
            val subject = client(recording, echoState())

            subject.restoreSession().getOrThrow()

            assertEquals(SessionState.Authorized, subject.session.value)
            assertTrue(recording.requests.isEmpty(), "aucun appel réseau pour un jeton encore valide")
        }

    @Test
    fun `restoreSession sans jeton echoue en NotSignedIn et laisse la session telle quelle`() =
        runTest {
            val subject = client(RecordingEngine { MockResponse("{}") }, echoState())

            val error = subject.restoreSession().exceptionOrNull()

            assertIs<SpotifyError.NotSignedIn>(error)
            assertEquals(SessionState.SignedOut, subject.session.value)
        }

    @Test
    fun `refreshFollowedArtists lit l'enveloppe artists de GET me following`() =
        runTest {
            store.save(StoredToken("a", "r", expiresAtEpochMs = now + 3_600_000))
            val recording =
                RecordingEngine {
                    MockResponse(
                        """{"artists":{"total":61,"items":[{"id":"r1","name":"Cléo Wend",
                        "images":[{"url":"u","width":640,"height":640}]}]}}""",
                    )
                }
            val subject = client(recording, echoState())

            val page = subject.refreshFollowedArtists().getOrThrow()

            assertEquals(61, page.total)
            assertEquals("Cléo Wend", page.items.single().name)
            assertEquals("spotify:artist:r1", page.items.single().uri)
            assertEquals(page, subject.followedArtists.value)
            val request = recording.requests.single { it.url.encodedPath.endsWith("/me/following") }
            assertEquals("artist", request.url.parameters["type"])
        }

    @Test
    fun `savedTracksTotal ne demande qu'un element et rend le total`() =
        runTest {
            store.save(StoredToken("a", "r", expiresAtEpochMs = now + 3_600_000))
            val recording = RecordingEngine { MockResponse("""{"total":1284,"items":[{"track":{"id":"t"}}]}""") }

            val total = client(recording, echoState()).savedTracksTotal().getOrThrow()

            assertEquals(1284, total)
            val request = recording.requests.single { it.url.encodedPath.endsWith("/me/tracks") }
            assertEquals("1", request.url.parameters["limit"])
        }

    @Test
    fun `currentUser lit l'offre et isPremium`() =
        runTest {
            store.save(StoredToken("a", "r", expiresAtEpochMs = now + 3_600_000))
            val recording = RecordingEngine { MockResponse("""{"id":"u1","display_name":"Théo","product":"premium"}""") }

            val user = client(recording, echoState()).currentUser().getOrThrow()

            assertEquals("Théo", user.displayName)
            assertTrue(user.isPremium)
        }

    @Test
    fun `search tolere les null de playlists et regroupe par genre`() =
        runTest {
            store.save(StoredToken("a", "r", expiresAtEpochMs = now + 3_600_000))
            val recording =
                RecordingEngine {
                    MockResponse(
                        """{"albums":{"items":[{"id":"a1","name":"Nuits blanches","artists":[{"id":"r1","name":"Elia Faure"}]}]},
                        "artists":{"items":[{"id":"r1","name":"Elia Faure"}]},
                        "playlists":{"items":[null,{"id":"p1","name":"Sillons"}]},
                        "tracks":{"items":[{"id":"t1","name":"Sous la neige","duration_ms":268000}]}}""",
                    )
                }

            val results = client(recording, echoState()).search("elia").getOrThrow()

            assertEquals(listOf("a1"), results.albums.map { it.id })
            assertEquals(listOf("r1"), results.artists.map { it.id })
            assertEquals(listOf("Sillons"), results.playlists.map { it.name })
            assertEquals(listOf("t1"), results.tracks.map { it.id.value })
            val request = recording.requests.single { it.url.encodedPath.endsWith("/search") }
            assertEquals("elia", request.url.parameters["q"])
        }

    @Test
    fun `search envoie tous les genres par defaut, sans offset`() =
        runTest {
            store.save(StoredToken("a", "r", expiresAtEpochMs = now + 3_600_000))
            val recording = RecordingEngine { MockResponse("{}") }

            client(recording, echoState()).search("elia").getOrThrow()

            val request = recording.requests.single { it.url.encodedPath.endsWith("/search") }
            assertEquals("album,artist,playlist,track", request.url.parameters["type"])
            assertNull(request.url.parameters["offset"])
        }

    @Test
    fun `search limite aux genres demandes et pagine par offset`() =
        runTest {
            store.save(StoredToken("a", "r", expiresAtEpochMs = now + 3_600_000))
            val recording = RecordingEngine { MockResponse("""{"tracks":{"items":[{"id":"t1","name":"Sous la neige"}]}}""") }

            val results =
                client(recording, echoState())
                    .search("elia", limit = 10, types = setOf(SearchType.Track), offset = 20)
                    .getOrThrow()

            val request = recording.requests.single { it.url.encodedPath.endsWith("/search") }
            assertEquals("track", request.url.parameters["type"])
            assertEquals("10", request.url.parameters["limit"])
            assertEquals("20", request.url.parameters["offset"])
            assertEquals(listOf("t1"), results.tracks.map { it.id.value })
            assertTrue(results.albums.isEmpty())
        }

    @Test
    fun `search sans aucun genre est refuse`() =
        runTest {
            val recording = RecordingEngine { MockResponse("{}") }

            assertFailsWith<IllegalArgumentException> {
                client(recording, echoState()).search("elia", types = emptySet())
            }
            assertTrue(recording.requests.isEmpty())
        }

    @Test
    fun `search d'une requete vide ne fait aucun appel`() =
        runTest {
            val recording = RecordingEngine { MockResponse("{}") }

            val results = client(recording, echoState()).search("   ").getOrThrow()

            assertTrue(results.isEmpty)
            assertTrue(recording.requests.isEmpty())
        }

    @Test
    fun `album rattache l'album a ses titres`() =
        runTest {
            store.save(StoredToken("a", "r", expiresAtEpochMs = now + 3_600_000))
            val recording =
                RecordingEngine {
                    MockResponse(
                        """{"id":"a1","name":"Marée haute","release_date":"2024-03-01","total_tracks":2,
                        "artists":[{"id":"r1","name":"Nord Nord"}],
                        "tracks":{"items":[{"id":"t1","name":"Estran","duration_ms":252000,"track_number":1},
                        {"id":"t2","name":"Ressac","duration_ms":381000,"track_number":2}]}}""",
                    )
                }

            val detail = client(recording, echoState()).album("a1").getOrThrow()

            assertEquals(2024, detail.album.releaseYear)
            assertEquals(listOf(1, 2), detail.tracks.map { it.trackNumber })
            assertTrue(detail.tracks.all { it.album?.id == "a1" })
            assertEquals(633_000, detail.durationMs)
        }

    @Test
    fun `artist survit a l'echec des titres populaires`() =
        runTest {
            store.save(StoredToken("a", "r", expiresAtEpochMs = now + 3_600_000))
            val recording =
                RecordingEngine { request ->
                    val path = request.url.encodedPath
                    when {
                        path.endsWith("/top-tracks") -> MockResponse("""{"error":{"status":403,"message":"Forbidden"}}""", status = 403)
                        path.endsWith("/albums") -> MockResponse("""{"items":[{"id":"a1","name":"Sillons"}]}""")
                        else -> MockResponse("""{"id":"r1","name":"Elia Faure"}""")
                    }
                }

            val detail = client(recording, echoState()).artist("r1").getOrThrow()

            assertEquals("Elia Faure", detail.artist.name)
            assertTrue(detail.topTracks.isEmpty())
            assertEquals(listOf("a1"), detail.albums.map { it.id })
        }

    @Test
    fun `playlist lit les titres sous track, ignore les elements supprimes`() =
        runTest {
            store.save(StoredToken("a", "r", expiresAtEpochMs = now + 3_600_000))
            val recording =
                RecordingEngine {
                    MockResponse(
                        """{"id":"p1","name":"Le soir tard","description":"calme",
                        "tracks":{"total":3,"items":[{"track":{"id":"t1","name":"Braise","duration_ms":1000}},
                        {"track":null},{"track":{"id":"t2","name":"Onde longue"}}]}}""",
                    )
                }

            val detail = client(recording, echoState()).playlist(PlaylistId("p1")).getOrThrow()

            assertEquals(listOf("t1", "t2"), detail.tracks.map { it.id.value })
            assertEquals(3, detail.totalTracks)
            assertEquals("calme", detail.description)
        }

    @Test
    fun `playlist lit aussi la forme items-item`() =
        runTest {
            store.save(StoredToken("a", "r", expiresAtEpochMs = now + 3_600_000))
            val recording =
                RecordingEngine {
                    MockResponse("""{"id":"p1","name":"X","items":{"total":1,"items":[{"item":{"id":"t9","name":"Neuf"}}]}}""")
                }

            val detail = client(recording, echoState()).playlist(PlaylistId("p1")).getOrThrow()

            assertEquals(listOf("t9"), detail.tracks.map { it.id.value })
        }

    @Test
    fun `isFollowingArtist lit la reponse booleenne de following contains`() =
        runTest {
            store.save(StoredToken("a", "r", expiresAtEpochMs = now + 3_600_000))
            val recording = RecordingEngine { MockResponse("[true]") }

            val following = client(recording, echoState()).isFollowingArtist("r1").getOrThrow()

            assertTrue(following)
            val request = recording.requests.single()
            assertTrue(request.url.encodedPath.endsWith("/me/following/contains"))
            assertEquals("artist", request.url.parameters["type"])
            assertEquals("r1", request.url.parameters["ids"])
        }

    @Test
    fun `setFollowingArtist envoie PUT pour suivre et DELETE pour ne plus suivre`() =
        runTest {
            store.save(StoredToken("a", "r", expiresAtEpochMs = now + 3_600_000))
            val recording = RecordingEngine { MockResponse("", status = 204) }
            val subject = client(recording, echoState())

            subject.setFollowingArtist("r1", true).getOrThrow()
            subject.setFollowingArtist("r1", false).getOrThrow()

            assertEquals(listOf("PUT", "DELETE"), recording.requests.map { it.method.value })
            assertTrue(recording.requests.all { it.url.encodedPath.endsWith("/me/following") })
        }

    @Test
    fun `isSaved lit un tableau JSON a la racine`() =
        runTest {
            store.save(StoredToken("a", "r", expiresAtEpochMs = now + 3_600_000))
            val recording = RecordingEngine { MockResponse("[false]") }

            val saved = client(recording, echoState()).isSaved(TrackId("t1")).getOrThrow()

            assertEquals(false, saved)
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
