package org.vander.spotify.android

import kotlinx.coroutines.flow.StateFlow
import org.vander.spotify.SpotifyError
import org.vander.spotify.model.TrackId

/**
 * Contrôle de lecture local, via l'App Remote.
 *
 * Ce type n'existe que dans cet artefact. Sur iOS, le code qui l'appellerait ne compile pas,
 * plutôt que d'échouer à l'exécution : la capacité est portée par le classpath, pas par une
 * condition. C'est la contrepartie assumée du choix « App Remote sur Android seulement ».
 *
 * L'App Remote est une liaison IPC vers l'application Spotify installée. Elle est
 * indépendante de l'identité : se déconnecter ne déconnecte pas l'utilisateur, et
 * `SpotifyClient.signOut()` ne coupe pas la liaison. Deux axes, deux jeux de verbes.
 */
public interface SpotifyPlayer {
    public val connection: StateFlow<PlayerConnection>

    /** `null` tant qu'aucun état n'a été poussé par l'application Spotify. */
    public val nowPlaying: StateFlow<NowPlaying?>

    /**
     * Ouvre la liaison et s'abonne à l'état du lecteur.
     *
     * Appelable à nouveau après [disconnect] : l'abonnement est rétabli sur la nouvelle
     * liaison. Un second appel alors que la liaison est déjà ouverte ne fait rien.
     */
    public suspend fun connect(): Result<Unit>

    /**
     * Ferme la liaison, après avoir annulé l'abonnement.
     *
     * L'ordre compte : annuler l'abonnement d'abord évite que le SDK pousse un dernier état
     * sur une liaison en train de disparaître.
     */
    public fun disconnect()

    public suspend fun play(track: TrackId): Result<Unit>

    public suspend fun resume(): Result<Unit>

    public suspend fun pause(): Result<Unit>

    public suspend fun skipNext(): Result<Unit>

    public suspend fun skipPrevious(): Result<Unit>

    public suspend fun seekTo(positionMs: Long): Result<Unit>

    public suspend fun setShuffle(enabled: Boolean): Result<Unit>

    public suspend fun setRepeat(mode: RepeatMode): Result<Unit>
}

/**
 * État de la liaison App Remote — et rien d'autre. L'identité vit dans
 * `SpotifyClient.session`.
 */
public sealed interface PlayerConnection {
    public data object NotConnected : PlayerConnection

    public data object Connecting : PlayerConnection

    public data object Connected : PlayerConnection

    public data class Failed(
        public val error: SpotifyError,
    ) : PlayerConnection
}

/**
 * Instantané de ce que joue l'application Spotify.
 *
 * @param position position de lecture au moment de [emittedAtElapsedMs]. Elle ne s'écoule
 *   pas toute seule : une UI qui veut une barre fluide interpole à partir de ces deux champs.
 */
public data class NowPlaying(
    public val track: TrackId?,
    public val title: String,
    public val artist: String,
    public val album: String,
    public val coverImageUri: String?,
    public val durationMs: Long,
    public val positionMs: Long,
    public val isPaused: Boolean,
    public val shuffle: Boolean,
    public val repeat: RepeatMode,
    public val emittedAtElapsedMs: Long,
)

/**
 * Mode de répétition.
 *
 * Une énumération plutôt que l'entier brut du SDK : `setRepeat(1)` ne dit pas s'il s'agit de
 * la piste ou du contexte, et l'appelant finit par recopier une constante.
 */
public enum class RepeatMode(
    public val sdkValue: Int,
) {
    Off(0),
    Track(1),
    Context(2),
    ;

    public companion object {
        public fun fromSdk(value: Int): RepeatMode = entries.firstOrNull { it.sdkValue == value } ?: Off
    }
}
