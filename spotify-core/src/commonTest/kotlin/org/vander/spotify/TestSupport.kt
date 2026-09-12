package org.vander.spotify

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import org.vander.spotify.auth.AuthorizationRequest
import org.vander.spotify.auth.AuthorizationResponse
import org.vander.spotify.auth.Authorizer

internal const val TEST_ACCOUNTS = "https://accounts.test/"
internal const val TEST_API = "https://api.test/v1/"

internal val testConfig =
    SpotifyConfig(
        clientId = "client-abc",
        redirectUri = "vinylotech://callback",
        scopes = setOf(SpotifyScope.Streaming, SpotifyScope.UserLibraryRead),
    )

/** Journalise les requêtes vues, pour pouvoir affirmer ce qui est parti — et ce qui ne l'est pas. */
internal class RecordingEngine(
    private val handler: (HttpRequestData) -> MockResponse,
) {
    val requests: MutableList<HttpRequestData> = mutableListOf()

    val engine: MockEngine =
        MockEngine { request ->
            requests += request
            val response = handler(request)
            respond(
                content = response.body,
                status = HttpStatusCode.fromValue(response.status),
                headers = headersOf("Content-Type", "application/json"),
            )
        }
}

internal data class MockResponse(
    val body: String,
    val status: Int = 200,
)

internal fun HttpRequestData.formBody(): String =
    (body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString() ?: ""

/** Authorizer scriptable : renvoie ce qu'on lui dit, sans jamais ouvrir quoi que ce soit. */
internal class FakeAuthorizer(
    private val respond: (AuthorizationRequest) -> Result<AuthorizationResponse>,
) : Authorizer {
    var lastRequest: AuthorizationRequest? = null
        private set

    override suspend fun authorize(request: AuthorizationRequest): Result<AuthorizationResponse> {
        lastRequest = request
        return respond(request)
    }
}

internal fun tokenJson(
    accessToken: String = "access-1",
    refreshToken: String? = "refresh-1",
    expiresIn: Long = 3600,
): String =
    buildString {
        append("""{"access_token":"$accessToken","token_type":"Bearer","expires_in":$expiresIn""")
        if (refreshToken != null) append(""","refresh_token":"$refreshToken"""")
        append("}")
    }
