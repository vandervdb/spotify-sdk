package org.vander.spotify

import io.ktor.http.HttpHeaders
import kotlinx.coroutines.test.runTest
import org.vander.spotify.internal.web.SpotifyWebApi
import org.vander.spotify.internal.web.spotifyHttpClient
import org.vander.spotify.model.TrackId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SpotifyWebApiTest {
    private fun api(
        recording: RecordingEngine,
        token: Result<String> = Result.success("tok-1"),
    ) = SpotifyWebApi(spotifyHttpClient(recording.engine), { token }, TEST_API)

    @Test
    fun `chaque appel porte l'en-tete porteur`() =
        runTest {
            val recording = RecordingEngine { MockResponse("""{"id":"u1","display_name":"Arnaud"}""") }

            val user = api(recording).me().getOrThrow()

            assertEquals("Bearer tok-1", recording.requests.single().headers[HttpHeaders.Authorization])
            assertEquals("u1", user.id.value)
            assertEquals("Arnaud", user.displayName)
        }

    @Test
    fun `les playlists sont converties et paginees`() =
        runTest {
            val recording =
                RecordingEngine {
                    MockResponse(
                        """
                        {"total":42,"items":[
                          {"id":"p1","name":"Matin","tracks":{"total":12},
                           "images":[{"url":"https://i/1","width":300,"height":300}]},
                          {"id":"p2","name":"Soir","tracks":{"total":8}}
                        ]}
                        """.trimIndent(),
                    )
                }

            val page = api(recording).playlists().getOrThrow()

            assertEquals(42, page.total, "le total serveur doit survivre à la pagination")
            assertEquals(listOf("Matin", "Soir"), page.items.map { it.name })
            assertEquals(12, page.items.first().trackCount)
            assertEquals("https://i/1", page.items.first().cover?.url)
            assertTrue(recording.requests.single().url.parameters["limit"] == "50")
        }

    @Test
    fun `une piste sans identifiant est ecartee a la frontiere`() =
        runTest {
            // Cas réel : un titre local, ou un épisode de podcast dans la file d'attente.
            val recording =
                RecordingEngine {
                    MockResponse(
                        """
                        {"currently_playing":{"id":"t1","name":"A","duration_ms":1000},
                         "queue":[{"id":null,"name":"fichier local"},{"id":"t2","name":"B"}]}
                        """.trimIndent(),
                    )
                }

            val queue = api(recording).queue().getOrThrow()

            assertEquals("t1", queue.currentlyPlaying?.id?.value)
            assertEquals(listOf("t2"), queue.upcoming.map { it.id.value })
        }

    @Test
    fun `un objet error dans une reponse 200 reste un echec`() =
        runTest {
            // L'API répond parfois 200 avec une erreur applicative : le statut seul ment.
            val recording =
                RecordingEngine { MockResponse("""{"error":{"status":403,"message":"Forbidden"}}""", status = 200) }

            val error = api(recording).me().exceptionOrNull()

            assertIs<SpotifyError.Api>(error)
            assertEquals(403, error.status)
        }

    @Test
    fun `un 401 devient Unauthorized`() =
        runTest {
            val recording =
                RecordingEngine { MockResponse("""{"error":{"status":401,"message":"expired"}}""", status = 401) }

            assertIs<SpotifyError.Unauthorized>(api(recording).me().exceptionOrNull())
        }

    @Test
    fun `setSaved choisit PUT ou DELETE`() =
        runTest {
            val recording = RecordingEngine { MockResponse("", status = 200) }
            val subject = api(recording)

            subject.setSaved(TrackId("t1"), saved = true).getOrThrow()
            subject.setSaved(TrackId("t1"), saved = false).getOrThrow()

            assertEquals(listOf("PUT", "DELETE"), recording.requests.map { it.method.value })
            assertTrue(recording.requests.all { it.url.parameters["ids"] == "t1" })
        }

    @Test
    fun `sans jeton l'appel echoue avant de partir sur le reseau`() =
        runTest {
            val recording = RecordingEngine { MockResponse("{}") }

            val error =
                api(recording, token = Result.failure(SpotifyError.NotSignedIn())).me().exceptionOrNull()

            assertIs<SpotifyError.NotSignedIn>(error)
            assertTrue(recording.requests.isEmpty(), "aucune requête ne doit partir sans jeton")
        }
}
