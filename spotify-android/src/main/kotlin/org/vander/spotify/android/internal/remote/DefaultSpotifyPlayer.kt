package org.vander.spotify.android.internal.remote

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.vander.spotify.SpotifyError
import org.vander.spotify.android.NowPlaying
import org.vander.spotify.android.PlayerConnection
import org.vander.spotify.android.RepeatMode
import org.vander.spotify.android.SpotifyPlayer
import org.vander.spotify.model.TrackId
import kotlin.coroutines.resume

internal class DefaultSpotifyPlayer(
    private val clientId: String,
    private val redirectUri: String,
    private val connector: RemoteConnector,
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
) : SpotifyPlayer {
    private val _connection = MutableStateFlow<PlayerConnection>(PlayerConnection.NotConnected)
    override val connection: StateFlow<PlayerConnection> = _connection.asStateFlow()

    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    override val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

    private var handle: RemoteHandle? = null
    private var subscription: RemoteSubscription? = null

    /** Empêche deux connexions concurrentes de laisser une liaison orpheline. */
    private val connectMutex = Mutex()

    override suspend fun connect(): Result<Unit> =
        connectMutex.withLock {
            if (handle != null) return@withLock Result.success(Unit)

            _connection.value = PlayerConnection.Connecting

            val connected =
                suspendCancellableCoroutine { continuation ->
                    connector.connect(
                        clientId,
                        redirectUri,
                        object : RemoteConnector.Listener {
                            override fun onConnected(handle: RemoteHandle) {
                                continuation.resume(Result.success(handle))
                            }

                            override fun onFailure(error: Throwable) {
                                continuation.resume(Result.failure(error))
                            }
                        },
                    )
                }

            connected.fold(
                onSuccess = { opened ->
                    handle = opened
                    // L'abonnement est (re)posé à chaque connexion. C'est ce qui rend
                    // `connect()` après `disconnect()` réellement fonctionnel : un drapeau
                    // « déjà abonné » qui survivrait à la déconnexion laisserait l'état du
                    // lecteur muet jusqu'à la mort du processus.
                    subscription = opened.subscribe(::publish)
                    _connection.value = PlayerConnection.Connected
                    Result.success(Unit)
                },
                onFailure = { error ->
                    val wrapped = error.asSpotifyError()
                    _connection.value = PlayerConnection.Failed(wrapped)
                    Result.failure(wrapped)
                },
            )
        }

    override fun disconnect() {
        // Annuler l'abonnement d'abord : le SDK peut pousser un dernier état pendant la
        // fermeture, et il arriverait sur une liaison déjà rendue.
        subscription?.cancel()
        subscription = null
        handle?.disconnect()
        handle = null
        _nowPlaying.value = null
        _connection.value = PlayerConnection.NotConnected
    }

    override suspend fun play(track: TrackId): Result<Unit> = onHandle { play(track.uri) }

    override suspend fun resume(): Result<Unit> = onHandle { resume() }

    override suspend fun pause(): Result<Unit> = onHandle { pause() }

    override suspend fun skipNext(): Result<Unit> = onHandle { skipNext() }

    override suspend fun skipPrevious(): Result<Unit> = onHandle { skipPrevious() }

    override suspend fun seekTo(positionMs: Long): Result<Unit> = onHandle { seekTo(positionMs) }

    override suspend fun setShuffle(enabled: Boolean): Result<Unit> = onHandle { setShuffle(enabled) }

    override suspend fun setRepeat(mode: RepeatMode): Result<Unit> = onHandle { setRepeat(mode) }

    /**
     * Toute commande sans liaison ouverte échoue explicitement.
     *
     * L'alternative — ignorer silencieusement — rend un bouton inerte impossible à
     * diagnostiquer : l'appel réussit, et rien ne se passe.
     */
    private suspend fun onHandle(command: suspend RemoteHandle.() -> Result<Unit>): Result<Unit> {
        val open = handle ?: return Result.failure(SpotifyError.NotSignedIn())
        return runCatching { open.command() }.getOrElse { Result.failure(it.asSpotifyError()) }
    }

    private fun publish(snapshot: PlayerSnapshot) {
        _nowPlaying.value =
            NowPlaying(
                track = snapshot.trackId?.takeIf { it.isNotBlank() }?.let(::TrackId),
                title = snapshot.title,
                artist = snapshot.artist,
                album = snapshot.album,
                coverImageUri = snapshot.coverImageUri,
                durationMs = snapshot.durationMs,
                positionMs = snapshot.positionMs,
                isPaused = snapshot.isPaused,
                shuffle = snapshot.shuffle,
                repeat = RepeatMode.fromSdk(snapshot.repeatSdkValue),
                emittedAtElapsedMs = elapsedRealtime(),
            )
    }

    private fun Throwable.asSpotifyError(): SpotifyError =
        this as? SpotifyError ?: SpotifyError.Network(this)
}
