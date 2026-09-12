package org.vander.spotify.android.internal.remote

import org.vander.spotify.android.RepeatMode

/**
 * Frontière entre la lib et le SDK App Remote.
 *
 * `SpotifyAppRemote.connect` est une fonction statique : impossible à simuler. Toute la
 * logique au-dessus de cette interface se teste donc sans appareil, sans application Spotify
 * installée et sans le moindre type du SDK — il suffit d'un faux connecteur.
 *
 * Un seul fichier implémente ce contrat avec le vrai SDK, et c'est le seul de la lib qui ne
 * soit pas couvert par un test.
 *
 * Le `Context` Android n'apparaît pas ici : il est confié à l'implémentation SDK, qui en a
 * besoin. Le lecteur au-dessus devient ainsi du Kotlin ordinaire, testable sur JVM nue.
 */
internal interface RemoteConnector {
    fun connect(
        clientId: String,
        redirectUri: String,
        listener: Listener,
    )

    interface Listener {
        fun onConnected(handle: RemoteHandle)

        fun onFailure(error: Throwable)
    }
}

/**
 * Liaison ouverte vers l'application Spotify.
 *
 * Typée comme une interface et non comme le `SpotifyAppRemote` du SDK — ni comme un `Any?`
 * opaque. Un `Any?` obligerait chaque appelant à transtyper, une interface laisse le
 * compilateur vérifier les appels tout en gardant le SDK hors de portée.
 */
internal interface RemoteHandle {
    suspend fun play(uri: String): Result<Unit>

    suspend fun resume(): Result<Unit>

    suspend fun pause(): Result<Unit>

    suspend fun skipNext(): Result<Unit>

    suspend fun skipPrevious(): Result<Unit>

    suspend fun seekTo(positionMs: Long): Result<Unit>

    suspend fun setShuffle(enabled: Boolean): Result<Unit>

    suspend fun setRepeat(mode: RepeatMode): Result<Unit>

    /** L'abonnement rendu doit être annulé avant [disconnect], jamais après. */
    fun subscribe(onState: (PlayerSnapshot) -> Unit): RemoteSubscription

    fun disconnect()
}

internal fun interface RemoteSubscription {
    fun cancel()
}

/**
 * État poussé par le SDK, déjà traduit.
 *
 * La conversion se fait dans l'implémentation SDK du connecteur, pas plus haut : c'est ce
 * qui permet de tester la logique de lecture sans jamais construire un `PlayerState` du SDK,
 * type que l'on ne peut pas instancier hors d'un appareil.
 */
internal data class PlayerSnapshot(
    val trackId: String?,
    val title: String,
    val artist: String,
    val album: String,
    val coverImageUri: String?,
    val durationMs: Long,
    val positionMs: Long,
    val isPaused: Boolean,
    val shuffle: Boolean,
    val repeatSdkValue: Int,
)
