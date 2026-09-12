package org.vander.spotify.internal

import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.vander.spotify.SpotifyClient
import org.vander.spotify.SpotifyConfig
import org.vander.spotify.SpotifyError
import org.vander.spotify.auth.AuthorizationRequest
import org.vander.spotify.auth.Authorizer
import org.vander.spotify.auth.Pkce
import org.vander.spotify.auth.SessionState
import org.vander.spotify.auth.StoredToken
import org.vander.spotify.auth.TokenStore
import org.vander.spotify.internal.crypto.base64UrlNoPad
import org.vander.spotify.internal.crypto.secureRandomBytes
import org.vander.spotify.internal.time.Clock
import org.vander.spotify.internal.web.SpotifyWebApi
import org.vander.spotify.internal.web.TokenEndpoint
import org.vander.spotify.internal.web.buildAuthorizeUrl
import org.vander.spotify.internal.web.flatMap
import org.vander.spotify.model.PlaylistCollection
import org.vander.spotify.model.Queue
import org.vander.spotify.model.TrackId
import org.vander.spotify.model.User

internal class DefaultSpotifyClient(
    private val config: SpotifyConfig,
    private val tokenStore: TokenStore,
    private val authorizer: Authorizer,
    private val http: HttpClient,
    private val tokenEndpoint: TokenEndpoint,
    private val clock: Clock = Clock.System,
    private val accountsBase: String? = null,
    apiBase: String? = null,
) : SpotifyClient {
    private val _session = MutableStateFlow<SessionState>(SessionState.SignedOut)
    override val session: StateFlow<SessionState> = _session.asStateFlow()

    private val _playlists = MutableStateFlow(PlaylistCollection.EMPTY)
    override val playlists: StateFlow<PlaylistCollection> = _playlists.asStateFlow()

    private val _queue = MutableStateFlow(Queue.EMPTY)
    override val queue: StateFlow<Queue> = _queue.asStateFlow()

    /**
     * Sérialise le renouvellement du jeton.
     *
     * Sans ce verrou, deux appels concurrents qui trouvent le jeton expiré lancent deux
     * renouvellements ; le second invalide le `refresh_token` que le premier vient
     * d'utiliser, et la session meurt sans raison visible.
     */
    private val tokenMutex = Mutex()

    private val api =
        SpotifyWebApi(
            client = http,
            accessToken = ::accessToken,
            baseUrl = apiBase ?: org.vander.spotify.internal.web.SPOTIFY_API_BASE,
        )

    override suspend fun signIn(): Result<Unit> {
        _session.value = SessionState.Authorizing

        val challenge = Pkce.generate()
        val state = base64UrlNoPad(secureRandomBytes(16))
        val url =
            accountsBase
                ?.let { buildAuthorizeUrl(config, challenge, state, it) }
                ?: buildAuthorizeUrl(config, challenge, state)

        val request =
            AuthorizationRequest(
                url = url,
                clientId = config.clientId,
                redirectUri = config.redirectUri,
                scopes = config.scopes,
                codeChallenge = challenge.challenge,
                codeChallengeMethod = challenge.method,
                state = state,
            )
        val response = authorizer.authorize(request).getOrElse { return fail(it.asSpotifyError()) }

        // RFC 6749 §10.12 : un `state` qui ne correspond pas signale une réponse injectée.
        // Le code n'est pas échangé.
        if (response.state != state) return fail(SpotifyError.StateMismatch())

        return tokenEndpoint
            .exchange(response.code, challenge.verifier, config.redirectUri)
            .fold(
                onSuccess = { token ->
                    tokenStore.save(token)
                    _session.value = SessionState.Authorized
                    Result.success(Unit)
                },
                onFailure = { fail(it.asSpotifyError()) },
            )
    }

    override suspend fun signOut() {
        tokenStore.clear()
        _playlists.value = PlaylistCollection.EMPTY
        _queue.value = Queue.EMPTY
        _session.value = SessionState.SignedOut
    }

    override suspend fun currentUser(): Result<User> = api.me()

    override suspend fun refreshPlaylists(): Result<PlaylistCollection> =
        api.playlists().onSuccess { _playlists.value = it }

    override suspend fun refreshQueue(): Result<Queue> = api.queue().onSuccess { _queue.value = it }

    override suspend fun isSaved(track: TrackId): Result<Boolean> = api.isSaved(track)

    override suspend fun setSaved(
        track: TrackId,
        saved: Boolean,
    ): Result<Unit> = api.setSaved(track, saved)

    override fun close() {
        http.close()
    }

    /**
     * Rend un jeton utilisable, en le renouvelant si besoin.
     *
     * La marge de 60 secondes évite la course où un jeton valide au moment du contrôle
     * expire pendant le vol de la requête.
     */
    private suspend fun accessToken(): Result<String> =
        tokenMutex.withLock {
            val stored = tokenStore.load() ?: return@withLock Result.failure(SpotifyError.NotSignedIn())

            if (!stored.isExpiring()) {
                if (_session.value !is SessionState.Authorized) _session.value = SessionState.Authorized
                return@withLock Result.success(stored.accessToken)
            }

            val refreshToken =
                stored.refreshToken
                    ?: return@withLock fail(SpotifyError.Unauthorized("jeton expiré, aucun refresh_token"))
                        .map { "" }

            tokenEndpoint
                .refresh(refreshToken)
                .fold(
                    onSuccess = { fresh ->
                        tokenStore.save(fresh)
                        _session.value = SessionState.Authorized
                        Result.success(fresh.accessToken)
                    },
                    onFailure = { error ->
                        // Un refresh refusé n'est pas récupérable : le jeton local est mort.
                        if (error is SpotifyError.Unauthorized) tokenStore.clear()
                        fail(error.asSpotifyError()).map { "" }
                    },
                )
        }

    private fun StoredToken.isExpiring(): Boolean = clock.nowMs() >= expiresAtEpochMs - EXPIRY_MARGIN_MS

    private fun fail(error: SpotifyError): Result<Nothing> {
        _session.value = SessionState.Failed(error)
        return Result.failure(error)
    }

    private fun Throwable.asSpotifyError(): SpotifyError =
        this as? SpotifyError ?: SpotifyError.Network(this)

    private companion object {
        const val EXPIRY_MARGIN_MS = 60_000L
    }
}
