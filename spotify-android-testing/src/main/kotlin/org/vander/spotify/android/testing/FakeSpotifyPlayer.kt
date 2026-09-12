package org.vander.spotify.android.testing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.vander.spotify.SpotifyError
import org.vander.spotify.android.NowPlaying
import org.vander.spotify.android.PlayerConnection
import org.vander.spotify.android.RepeatMode
import org.vander.spotify.android.SpotifyPlayer
import org.vander.spotify.model.TrackId

/**
 * Double de [SpotifyPlayer] qui respecte le contrat de l'original.
 *
 * En particulier : une commande sans liaison ouverte échoue, comme dans l'implémentation
 * réelle. Un double permissif laisserait passer un écran qui commande le lecteur avant de
 * s'y être connecté — précisément le genre de défaut qu'un test est censé attraper.
 */
public class FakeSpotifyPlayer(
    initialNowPlaying: NowPlaying? = null,
) : SpotifyPlayer {
    private val _connection = MutableStateFlow<PlayerConnection>(PlayerConnection.NotConnected)
    override val connection: StateFlow<PlayerConnection> = _connection.asStateFlow()

    private val _nowPlaying = MutableStateFlow(initialNowPlaying)
    override val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

    private val _commands = mutableListOf<PlayerCommand>()

    /** Commandes reçues, dans l'ordre. */
    public val commands: List<PlayerCommand> get() = _commands.toList()

    public var connectCalls: Int = 0
        private set

    /** Panne injectée pour la prochaine connexion. Remise à `null` après usage. */
    public var nextConnectFailure: SpotifyError? = null

    public fun emitNowPlaying(state: NowPlaying?) {
        _nowPlaying.value = state
    }

    public fun clearCommands() {
        _commands.clear()
    }

    override suspend fun connect(): Result<Unit> {
        connectCalls++
        nextConnectFailure?.let { failure ->
            nextConnectFailure = null
            _connection.value = PlayerConnection.Failed(failure)
            return Result.failure(failure)
        }
        _connection.value = PlayerConnection.Connected
        return Result.success(Unit)
    }

    override fun disconnect() {
        _connection.value = PlayerConnection.NotConnected
        _nowPlaying.value = null
    }

    override suspend fun play(track: TrackId): Result<Unit> = record(PlayerCommand.Play(track))

    override suspend fun resume(): Result<Unit> = record(PlayerCommand.Resume)

    override suspend fun pause(): Result<Unit> = record(PlayerCommand.Pause)

    override suspend fun skipNext(): Result<Unit> = record(PlayerCommand.SkipNext)

    override suspend fun skipPrevious(): Result<Unit> = record(PlayerCommand.SkipPrevious)

    override suspend fun seekTo(positionMs: Long): Result<Unit> = record(PlayerCommand.SeekTo(positionMs))

    override suspend fun setShuffle(enabled: Boolean): Result<Unit> = record(PlayerCommand.SetShuffle(enabled))

    override suspend fun setRepeat(mode: RepeatMode): Result<Unit> = record(PlayerCommand.SetRepeat(mode))

    private fun record(command: PlayerCommand): Result<Unit> {
        // Même règle que l'implémentation réelle : sans liaison, la commande échoue.
        if (_connection.value !is PlayerConnection.Connected) {
            return Result.failure(SpotifyError.NotSignedIn())
        }
        _commands += command
        return Result.success(Unit)
    }
}

public sealed interface PlayerCommand {
    public data class Play(
        public val track: TrackId,
    ) : PlayerCommand

    public data object Resume : PlayerCommand

    public data object Pause : PlayerCommand

    public data object SkipNext : PlayerCommand

    public data object SkipPrevious : PlayerCommand

    public data class SeekTo(
        public val positionMs: Long,
    ) : PlayerCommand

    public data class SetShuffle(
        public val enabled: Boolean,
    ) : PlayerCommand

    public data class SetRepeat(
        public val mode: RepeatMode,
    ) : PlayerCommand
}
