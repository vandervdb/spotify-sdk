package org.vander.spotify.android

import org.vander.spotify.android.internal.remote.PlayerSnapshot
import org.vander.spotify.android.internal.remote.RemoteConnector
import org.vander.spotify.android.internal.remote.RemoteHandle
import org.vander.spotify.android.internal.remote.RemoteSubscription

/**
 * Second adapter du seam [RemoteConnector] — celui qui justifie son existence.
 *
 * Grâce à lui toute la logique de connexion, d'abonnement et de commande se vérifie sans
 * appareil, sans application Spotify installée et sans un seul type du SDK.
 */
internal class FakeRemoteConnector(
    private val failWith: Throwable? = null,
) : RemoteConnector {
    val handles: MutableList<FakeRemoteHandle> = mutableListOf()
    var connectCalls: Int = 0
        private set

    override fun connect(
        clientId: String,
        redirectUri: String,
        listener: RemoteConnector.Listener,
    ) {
        connectCalls++
        if (failWith != null) {
            listener.onFailure(failWith)
            return
        }
        val handle = FakeRemoteHandle()
        handles += handle
        listener.onConnected(handle)
    }
}

internal class FakeRemoteHandle : RemoteHandle {
    val commands: MutableList<String> = mutableListOf()
    var disconnected: Boolean = false
        private set
    var subscriptionCancelled: Boolean = false
        private set
    var disconnectedAfterCancel: Boolean? = null
        private set

    private var listener: ((PlayerSnapshot) -> Unit)? = null

    /** Simule un état poussé par l'application Spotify. */
    fun push(snapshot: PlayerSnapshot) {
        listener?.invoke(snapshot)
    }

    val hasLiveSubscription: Boolean get() = listener != null

    override suspend fun play(uri: String): Result<Unit> = record("play:$uri")

    override suspend fun resume(): Result<Unit> = record("resume")

    override suspend fun pause(): Result<Unit> = record("pause")

    override suspend fun skipNext(): Result<Unit> = record("skipNext")

    override suspend fun skipPrevious(): Result<Unit> = record("skipPrevious")

    override suspend fun seekTo(positionMs: Long): Result<Unit> = record("seekTo:$positionMs")

    override suspend fun setShuffle(enabled: Boolean): Result<Unit> = record("shuffle:$enabled")

    override suspend fun setRepeat(mode: RepeatMode): Result<Unit> = record("repeat:${mode.name}")

    override fun subscribe(onState: (PlayerSnapshot) -> Unit): RemoteSubscription {
        listener = onState
        return RemoteSubscription {
            subscriptionCancelled = true
            listener = null
        }
    }

    override fun disconnect() {
        disconnected = true
        // Mémorise l'ordre : annuler l'abonnement doit précéder la fermeture.
        disconnectedAfterCancel = subscriptionCancelled
    }

    private fun record(command: String): Result<Unit> {
        commands += command
        return Result.success(Unit)
    }
}

internal fun snapshot(
    trackId: String? = "track-1",
    title: String = "Titre",
    isPaused: Boolean = false,
    repeat: Int = 0,
): PlayerSnapshot =
    PlayerSnapshot(
        trackId = trackId,
        title = title,
        artist = "Artiste",
        album = "Album",
        coverImageUri = "spotify:image:abc",
        durationMs = 210_000,
        positionMs = 12_000,
        isPaused = isPaused,
        shuffle = false,
        repeatSdkValue = repeat,
    )
