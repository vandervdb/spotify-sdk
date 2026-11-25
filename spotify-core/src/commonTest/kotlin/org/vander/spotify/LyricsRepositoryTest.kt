package org.vander.spotify

import io.ktor.http.HttpHeaders
import kotlinx.coroutines.test.runTest
import org.vander.spotify.internal.lyrics.LrclibLyricsRepository
import org.vander.spotify.internal.lyrics.parseLrc
import org.vander.spotify.internal.web.spotifyHttpClient
import org.vander.spotify.model.LyricLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LyricsRepositoryTest {
    private fun repository(recording: RecordingEngine) = LrclibLyricsRepository(spotifyHttpClient(recording.engine), "https://lrclib.test/")

    private suspend fun LyricsRepository.medea() = lyrics("Medea", "Damon Jee", "Medea EP", 456_000)

    @Test
    fun `la requete porte titre - artiste - album et duree en secondes - sans jeton`() =
        runTest {
            val recording = RecordingEngine { MockResponse("""{"plainLyrics":"la la"}""") }

            repository(recording).medea()

            val request = recording.requests.single()
            assertEquals("/api/get", request.url.encodedPath)
            assertEquals("Medea", request.url.parameters["track_name"])
            assertEquals("Damon Jee", request.url.parameters["artist_name"])
            assertEquals("Medea EP", request.url.parameters["album_name"])
            assertEquals("456", request.url.parameters["duration"])
            assertEquals("spotify-sdk", request.headers[HttpHeaders.UserAgent])
            assertNull(request.headers[HttpHeaders.Authorization])
        }

    @Test
    fun `des paroles synchronisees sont lues et triees`() =
        runTest {
            val recording =
                RecordingEngine {
                    MockResponse(
                        """{"plainLyrics":"b\na","syncedLyrics":"[00:12.50] b\n[00:01.00] a"}""",
                    )
                }

            val lyrics = repository(recording).medea().getOrThrow()!!

            assertTrue(lyrics.isSynced)
            assertEquals(listOf(LyricLine(1_000, "a"), LyricLine(12_500, "b")), lyrics.lines)
            assertEquals("b\na", lyrics.plain)
        }

    @Test
    fun `sans synchro - il reste le texte brut`() =
        runTest {
            val lyrics = repository(RecordingEngine { MockResponse("""{"plainLyrics":"seul"}""") }).medea().getOrThrow()!!

            assertTrue(!lyrics.isSynced)
            assertEquals("seul", lyrics.plain)
        }

    @Test
    fun `un titre instrumental est rendu comme tel`() =
        runTest {
            val lyrics = repository(RecordingEngine { MockResponse("""{"instrumental":true}""") }).medea().getOrThrow()!!

            assertTrue(lyrics.instrumental)
        }

    @Test
    fun `un 404 est une absence de paroles - pas une erreur`() =
        runTest {
            val result = repository(RecordingEngine { MockResponse("""{"code":404,"name":"NotFound"}""", 404) }).medea()

            assertTrue(result.isSuccess)
            assertNull(result.getOrNull())
        }

    @Test
    fun `une reponse vide de paroles vaut absence`() =
        runTest {
            val result = repository(RecordingEngine { MockResponse("""{"plainLyrics":"  ","syncedLyrics":""}""") }).medea()

            assertNull(result.getOrThrow())
        }

    @Test
    fun `une erreur serveur est un echec Api`() =
        runTest {
            val result = repository(RecordingEngine { MockResponse("{}", 500) }).medea()

            val error = assertIs<SpotifyError.Api>(result.exceptionOrNull())
            assertEquals(500, error.status)
        }

    @Test
    fun `un corps illisible est un echec de serialisation`() =
        runTest {
            val result = repository(RecordingEngine { MockResponse("pas du json") }).medea()

            assertIs<SpotifyError.Serialization>(result.exceptionOrNull())
        }

    @Test
    fun `un meme titre n'interroge le service qu'une fois - absence comprise`() =
        runTest {
            val recording = RecordingEngine { MockResponse("{}", 404) }
            val repository = repository(recording)

            repository.medea()
            repository.medea()

            assertEquals(1, recording.requests.size)
        }

    @Test
    fun `un echec n'est pas retenu en cache`() =
        runTest {
            val recording = RecordingEngine { MockResponse("{}", 500) }
            val repository = repository(recording)

            repository.medea()
            repository.medea()

            assertEquals(2, recording.requests.size)
        }

    @Test
    fun `le parseur LRC gere fractions - horodatages multiples et metadonnees`() {
        val lines =
            parseLrc(
                """
                [ar:Damon Jee]
                [ti:Medea]
                [00:05.5]cinq
                [01:00.05][02:00]refrain
                sans horodatage
                """.trimIndent(),
            )

        assertEquals(
            listOf(
                LyricLine(5_500, "cinq"),
                LyricLine(60_050, "refrain"),
                LyricLine(120_000, "refrain"),
            ),
            lines,
        )
    }
}
