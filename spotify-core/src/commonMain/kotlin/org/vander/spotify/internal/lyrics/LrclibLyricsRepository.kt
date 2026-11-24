package org.vander.spotify.internal.lyrics

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import org.vander.spotify.LyricsRepository
import org.vander.spotify.SpotifyError
import org.vander.spotify.internal.web.SpotifyJson
import org.vander.spotify.model.LyricLine
import org.vander.spotify.model.Lyrics

internal const val LRCLIB_BASE: String = "https://lrclib.net/"

@Serializable
internal data class LrclibDto(
    val instrumental: Boolean = false,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null,
)

/**
 * Client LRCLIB : `GET /api/get` renvoie la correspondance exacte (titre, artiste, album,
 * durée à quelques secondes près), pas une liste à trier.
 */
internal class LrclibLyricsRepository(
    private val client: HttpClient,
    private val baseUrl: String = LRCLIB_BASE,
    private val cache: MutableMap<String, Lyrics?> = mutableMapOf(),
) : LyricsRepository {
    override suspend fun lyrics(
        track: String,
        artist: String,
        album: String?,
        durationMs: Long,
    ): Result<Lyrics?> {
        val key = "$artist\u0000$track\u0000${album.orEmpty()}\u0000${durationMs / 1_000}"
        // `containsKey` et non `?:` : « pas de paroles » (null) est un résultat qu'on retient aussi.
        if (cache.containsKey(key)) return Result.success(cache[key])

        return fetch(track, artist, album, durationMs).onSuccess { cache[key] = it }
    }

    private suspend fun fetch(
        track: String,
        artist: String,
        album: String?,
        durationMs: Long,
    ): Result<Lyrics?> {
        val response =
            try {
                client.get("${baseUrl}api/get") {
                    parameter("track_name", track)
                    parameter("artist_name", artist)
                    if (!album.isNullOrBlank()) parameter("album_name", album)
                    parameter("duration", durationMs / 1_000)
                    // LRCLIB demande un User-Agent qui identifie le client. Générique : rien de
                    // propre à l'application ou à l'utilisateur ne part vers le service.
                    header(HttpHeaders.UserAgent, "spotify-sdk")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return Result.failure(SpotifyError.Network(e))
            }

        if (response.status.value == 404) return Result.success(null)
        if (response.status.value !in 200..299) {
            return Result.failure(SpotifyError.Api(response.status.value, response.status.description))
        }

        return try {
            val dto = SpotifyJson.decodeFromString<LrclibDto>(response.bodyAsText())
            Result.success(dto.toLyrics())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(SpotifyError.Serialization(e))
        }
    }
}

/** `null` quand la réponse ne porte aucune parole exploitable et n'est pas instrumentale. */
internal fun LrclibDto.toLyrics(): Lyrics? {
    val lines = syncedLyrics?.let(::parseLrc).orEmpty()
    val plain = plainLyrics?.takeIf { it.isNotBlank() }
    if (!instrumental && lines.isEmpty() && plain == null) return null
    return Lyrics(lines = lines, plain = plain, instrumental = instrumental)
}

// `[mm:ss]`, `[mm:ss.x]`, `[mm:ss.xx]` ou `[mm:ss.xxx]`. Les balises de métadonnées
// (`[ar:…]`, `[ti:…]`) ne correspondent pas : leur début n'est pas numérique.
private val TIMESTAMP = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")

/** Une ligne peut porter plusieurs horodatages (refrain répété) : elle est alors émise à chacun. */
internal fun parseLrc(lrc: String): List<LyricLine> =
    lrc
        .lineSequence()
        .flatMap { raw ->
            val stamps = TIMESTAMP.findAll(raw).toList()
            if (stamps.isEmpty()) return@flatMap emptySequence()
            val text = raw.substring(stamps.last().range.last + 1).trim()
            stamps.asSequence().map { LyricLine(it.toMillis(), text) }
        }.sortedBy { it.startMs }
        .toList()

private fun MatchResult.toMillis(): Long {
    val (minutes, seconds, fraction) = destructured
    // La fraction est décimale : « 5 » = 500 ms, « 05 » = 50 ms, « 050 » = 50 ms.
    val millis = fraction.padEnd(3, '0').toLong()
    return minutes.toLong() * 60_000 + seconds.toLong() * 1_000 + millis
}
