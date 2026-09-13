package org.vander.spotify.rn

import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.WritableNativeArray
import com.facebook.react.bridge.WritableNativeMap
import com.facebook.react.module.annotations.ReactModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import org.vander.spotify.SpotifyError
import org.vander.spotify.android.AndroidSpotifyClient
import org.vander.spotify.android.NowPlaying
import org.vander.spotify.android.RepeatMode
import org.vander.spotify.auth.SessionState
import org.vander.spotify.model.Playlist
import org.vander.spotify.model.Queue
import org.vander.spotify.model.Track
import org.vander.spotify.model.TrackId

/**
 * Adaptateur TurboModule : il traduit, il n'invente rien.
 *
 * Les `suspend fun` de [AndroidSpotifyClient] deviennent des `Promise`, les `StateFlow`
 * deviennent des événements. Toute la logique reste dans la lib — un pont qui se met à
 * décider finit par diverger de ce qu'il adapte, ce qui est exactement ce qui était arrivé
 * à l'implémentation dont ce projet s'inspire, avec quatre variantes de démarrage dont une
 * déléguait à la mauvaise.
 *
 * Le `Result<T>` de la lib se projette sur le couple `resolve`/`reject` de React Native :
 * aucune exception ne traverse le pont, et le code d'erreur est celui du type scellé
 * [SpotifyError], donc exploitable par un `switch` côté JavaScript.
 */
@ReactModule(name = NativeSpotifySpec.NAME)
class SpotifyRnModule(
    reactContext: ReactApplicationContext,
    private val spotify: AndroidSpotifyClient,
) : NativeSpotifySpec(reactContext) {
    private val job: Job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main.immediate + job)

    init {
        observeState()
    }

    // ---- identité ----------------------------------------------------------

    override fun signIn(promise: Promise) = promise.launch { spotify.signIn() }

    override fun signOut(promise: Promise) =
        promise.launch {
            spotify.signOut()
            Result.success(Unit)
        }

    override fun getSessionState(promise: Promise) =
        promise.launch { Result.success(spotify.session.value.wireName()) }

    // ---- bibliothèque -------------------------------------------------------

    override fun refreshPlaylists(promise: Promise) =
        promise.launch {
            spotify.refreshPlaylists().map { collection ->
                WritableNativeMap().apply {
                    putInt("total", collection.total)
                    putArray("items", collection.items.toWritableArray { it.toWritableMap() })
                }
            }
        }

    override fun refreshQueue(promise: Promise) =
        promise.launch {
            spotify.refreshQueue().map { queue -> queue.toWritableMap() }
        }

    override fun currentUser(promise: Promise) =
        promise.launch {
            spotify.currentUser().map { user ->
                WritableNativeMap().apply {
                    putString("id", user.id.value)
                    putString("displayName", user.displayName)
                    putString("avatarUrl", user.avatar?.url)
                }
            }
        }

    override fun isSaved(
        trackId: String,
        promise: Promise,
    ) = promise.launch { spotify.isSaved(TrackId(trackId)) }

    override fun setSaved(
        trackId: String,
        saved: Boolean,
        promise: Promise,
    ) = promise.launch { spotify.setSaved(TrackId(trackId), saved) }

    // ---- lecture locale -----------------------------------------------------

    /**
     * Toujours `true` dans cette implémentation : elle n'existe que côté Android.
     *
     * Le spec TurboModule est partagé entre les deux plateformes et ne peut pas exprimer
     * qu'une méthode n'existe que sur l'une — côté Kotlin, `SpotifyPlayer` est simplement
     * absent du classpath iOS et le compilateur tranche. D'où ce drapeau : l'implémentation
     * iOS rend `false`, et JavaScript sait quoi afficher.
     */
    override fun isPlayerAvailable(): Boolean = true

    override fun connectPlayer(promise: Promise) = promise.launch { spotify.player.connect() }

    override fun disconnectPlayer(promise: Promise) =
        promise.launch {
            spotify.player.disconnect()
            Result.success(Unit)
        }

    override fun play(
        trackId: String,
        promise: Promise,
    ) = promise.launch { spotify.player.play(TrackId(trackId)) }

    override fun resume(promise: Promise) = promise.launch { spotify.player.resume() }

    override fun pause(promise: Promise) = promise.launch { spotify.player.pause() }

    override fun skipNext(promise: Promise) = promise.launch { spotify.player.skipNext() }

    override fun skipPrevious(promise: Promise) = promise.launch { spotify.player.skipPrevious() }

    override fun seekTo(
        positionMs: Double,
        promise: Promise,
    ) = promise.launch { spotify.player.seekTo(positionMs.toLong()) }

    override fun setShuffle(
        enabled: Boolean,
        promise: Promise,
    ) = promise.launch { spotify.player.setShuffle(enabled) }

    override fun setRepeat(
        mode: String,
        promise: Promise,
    ) = promise.launch {
        val repeat =
            RepeatMode.entries.firstOrNull { it.name.equals(mode, ignoreCase = true) }
                ?: return@launch Result.failure(
                    SpotifyError.Api(400, "mode de répétition inconnu : $mode"),
                )
        spotify.player.setRepeat(repeat)
    }

    // ---- état poussé --------------------------------------------------------

    private fun observeState() {
        // `drop(1)` : la valeur courante d'un StateFlow est rendue à l'abonnement, et la
        // réémettre produirait un événement que JavaScript n'a pas demandé au démarrage.
        // L'état initial se lit par getSessionState().
        scope.launch { spotify.session.drop(1).collect { emitOnSessionChange(it.wireName()) } }
        scope.launch {
            spotify.player.nowPlaying.drop(1).collect { emitOnNowPlayingChange(it.toWritableMap()) }
        }
        scope.launch {
            spotify.playlists.drop(1).collect { collection ->
                emitOnPlaylistsChange(
                    WritableNativeMap().apply {
                        putInt("total", collection.total)
                        putArray("items", collection.items.toWritableArray { it.toWritableMap() })
                    },
                )
            }
        }
        scope.launch { spotify.queue.drop(1).collect { emitOnQueueChange(it.toWritableMap()) } }
    }

    /**
     * React Native appelle ceci quand le contexte est détruit.
     *
     * Sans cette annulation, les quatre collecteurs survivraient au rechargement du bundle
     * et s'accumuleraient à chaque « reload » en développement.
     */
    override fun invalidate() {
        scope.cancel()
        spotify.player.disconnect()
        super.invalidate()
    }

    // ---- plomberie ----------------------------------------------------------

    private inline fun Promise.launch(crossinline block: suspend () -> Result<Any?>) {
        scope.launch {
            block().fold(
                onSuccess = { resolve(it) },
                onFailure = { reject(it.errorCode(), it.message, it) },
            )
        }
    }

    /** Code d'erreur stable, sur lequel JavaScript peut brancher un `switch`. */
    private fun Throwable.errorCode(): String =
        when (this) {
            is SpotifyError.NotSignedIn -> "NOT_SIGNED_IN"
            is SpotifyError.Unauthorized -> "UNAUTHORIZED"
            is SpotifyError.AuthorizationCancelled -> "AUTHORIZATION_CANCELLED"
            is SpotifyError.StateMismatch -> "STATE_MISMATCH"
            is SpotifyError.Network -> "NETWORK"
            is SpotifyError.Api -> "API_$status"
            is SpotifyError.Serialization -> "SERIALIZATION"
            else -> "UNKNOWN"
        }

    private fun SessionState.wireName(): String =
        when (this) {
            SessionState.SignedOut -> "SIGNED_OUT"
            SessionState.Authorizing -> "AUTHORIZING"
            SessionState.Authorized -> "AUTHORIZED"
            is SessionState.Failed -> "FAILED"
        }

    private fun NowPlaying?.toWritableMap(): WritableNativeMap =
        WritableNativeMap().apply {
            if (this@toWritableMap == null) return@apply
            putMap(
                "track",
                WritableNativeMap().apply {
                    putString("id", track?.value)
                    putString("name", title)
                    putString("artist", artist)
                    putString("album", album)
                    putDouble("durationMs", durationMs.toDouble())
                },
            )
            putDouble("positionMs", positionMs.toDouble())
            putBoolean("isPaused", isPaused)
            putBoolean("shuffle", shuffle)
            putString("repeat", repeat.name.uppercase())
            putString("coverImageUri", coverImageUri)
        }

    private fun Queue.toWritableMap(): WritableNativeMap =
        WritableNativeMap().apply {
            putMap("currentlyPlaying", currentlyPlaying?.toWritableMap())
            putArray("upcoming", upcoming.toWritableArray { it.toWritableMap() })
        }

    private fun Track.toWritableMap(): WritableNativeMap =
        WritableNativeMap().apply {
            putString("id", id.value)
            putString("name", name)
            putString("artist", artistNames)
            putString("album", album?.name)
            putDouble("durationMs", durationMs.toDouble())
        }

    private fun Playlist.toWritableMap(): WritableNativeMap =
        WritableNativeMap().apply {
            putString("id", id.value)
            putString("name", name)
            putInt("trackCount", trackCount)
            putString("coverUrl", cover?.url)
        }

    // Une lambda et non une référence `Type::toWritableMap` : ces conversions sont des
    // extensions déclarées dans la classe, donc « membre et extension à la fois », et
    // Kotlin interdit d'en prendre une référence callable.
    private inline fun <T> List<T>.toWritableArray(convert: (T) -> WritableNativeMap): WritableNativeArray =
        WritableNativeArray().also { array -> forEach { array.pushMap(convert(it)) } }
}
