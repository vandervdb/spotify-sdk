package org.vander.spotify.android.internal.remote

import android.content.Context
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.PlayerApi
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.protocol.client.CallResult
import com.spotify.protocol.types.Empty
import com.spotify.protocol.types.PlayerState
import kotlinx.coroutines.suspendCancellableCoroutine
import org.vander.spotify.SpotifyError
import org.vander.spotify.android.RepeatMode
import kotlin.coroutines.resume

/**
 * Le seul fichier de la lib qui touche le SDK App Remote — et le seul sans test.
 *
 * Tout ce qui est au-dessus travaille sur [RemoteHandle] et [PlayerSnapshot], donc se teste
 * sans appareil. Concentrer l'intestable en un point, aussi mince que possible, est le seul
 * moyen honnête de traiter une dépendance qu'on ne peut pas simuler.
 */
internal class SdkRemoteConnector(
    private val context: Context,
) : RemoteConnector {
    override fun connect(
        clientId: String,
        redirectUri: String,
        listener: RemoteConnector.Listener,
    ) {
        val params =
            ConnectionParams
                .Builder(clientId)
                .setRedirectUri(redirectUri)
                // `false` : l'autorisation est déjà faite par SpotifyClient.signIn(), avec
                // PKCE. Laisser le SDK ouvrir son propre écran créerait un second chemin
                // d'identité, hors du contrôle de la lib.
                .showAuthView(false)
                .build()

        SpotifyAppRemote.connect(
            context,
            params,
            object : Connector.ConnectionListener {
                override fun onConnected(remote: SpotifyAppRemote) {
                    listener.onConnected(SdkRemoteHandle(remote))
                }

                override fun onFailure(error: Throwable?) {
                    listener.onFailure(
                        error ?: SpotifyError.Network(IllegalStateException("échec sans cause")),
                    )
                }
            },
        )
    }
}

private class SdkRemoteHandle(
    private val remote: SpotifyAppRemote,
) : RemoteHandle {
    private val player: PlayerApi get() = remote.playerApi

    override suspend fun play(uri: String): Result<Unit> = player.play(uri).awaitCompletion()

    override suspend fun resume(): Result<Unit> = player.resume().awaitCompletion()

    override suspend fun pause(): Result<Unit> = player.pause().awaitCompletion()

    override suspend fun skipNext(): Result<Unit> = player.skipNext().awaitCompletion()

    override suspend fun skipPrevious(): Result<Unit> = player.skipPrevious().awaitCompletion()

    override suspend fun seekTo(positionMs: Long): Result<Unit> = player.seekTo(positionMs).awaitCompletion()

    override suspend fun setShuffle(enabled: Boolean): Result<Unit> = player.setShuffle(enabled).awaitCompletion()

    override suspend fun setRepeat(mode: RepeatMode): Result<Unit> = player.setRepeat(mode.sdkValue).awaitCompletion()

    override fun subscribe(onState: (PlayerSnapshot) -> Unit): RemoteSubscription {
        // Chaque appel à subscribeToPlayerState() ouvre un NOUVEL abonnement. C'est celui-ci
        // qu'il faut garder pour pouvoir l'annuler : rappeler la méthode pour annuler
        // créerait un second abonnement et laisserait le premier vivant.
        val subscription =
            remote.playerApi
                .subscribeToPlayerState()
                .setEventCallback { state -> onState(state.toSnapshot()) }

        return RemoteSubscription { subscription.cancel() }
    }

    override fun disconnect() {
        SpotifyAppRemote.disconnect(remote)
    }
}

/**
 * Le SDK rend ses résultats par callbacks ; on les ramène dans le monde des coroutines.
 *
 * Pas nommée `await` : `PendingResultBase` en déclare déjà une, bloquante et rendant le
 * `Result` du SDK. Un membre l'emporte toujours sur une extension, et l'appel serait parti
 * silencieusement sur la mauvaise.
 */
private suspend fun CallResult<Empty>.awaitCompletion(): Result<Unit> =
    suspendCancellableCoroutine { continuation ->
        setResultCallback { continuation.resume(Result.success(Unit)) }
        setErrorCallback { error ->
            continuation.resume(Result.failure(SpotifyError.Network(error)))
        }
    }

private fun PlayerState.toSnapshot(): PlayerSnapshot =
    PlayerSnapshot(
        // `track.uri` a la forme `spotify:track:<id>` ; on ne garde que l'identifiant,
        // la lib ne parle qu'en identifiants nus et reconstruit l'URI quand il le faut.
        trackId = track?.uri?.substringAfterLast(':'),
        title = track?.name.orEmpty(),
        artist = track?.artist?.name.orEmpty(),
        album = track?.album?.name.orEmpty(),
        coverImageUri = track?.imageUri?.raw,
        durationMs = track?.duration ?: 0,
        positionMs = playbackPosition,
        isPaused = isPaused,
        shuffle = playbackOptions?.isShuffling ?: false,
        repeatSdkValue = playbackOptions?.repeatMode ?: 0,
    )
