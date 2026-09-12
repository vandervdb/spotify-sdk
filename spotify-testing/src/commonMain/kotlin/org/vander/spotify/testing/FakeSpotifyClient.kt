package org.vander.spotify.testing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.vander.spotify.SpotifyClient
import org.vander.spotify.SpotifyError
import org.vander.spotify.auth.SessionState
import org.vander.spotify.model.PlaylistCollection
import org.vander.spotify.model.Queue
import org.vander.spotify.model.TrackId
import org.vander.spotify.model.User
import org.vander.spotify.model.UserId

/**
 * Double de [SpotifyClient] qui **se comporte** comme l'original.
 *
 * C'est la différence entre un double utile et un double qui ment : [signIn] fait réellement
 * passer [session] à `Authorized`, [setSaved] modifie un état que [isSaved] relit ensuite, et
 * [signOut] vide les caches. Un double dont toutes les méthodes ont un corps vide laisse
 * passer les tests et les aperçus qu'il était censé servir — c'est exactement ce qu'on
 * cherche à éviter en le publiant comme artefact plutôt qu'en le réécrivant dans chaque
 * projet.
 *
 * Trois usages :
 * - piloter l'état : [emitSession], [emitPlaylists], [emitQueue] ;
 * - scénariser une panne : [nextSignInResult], [nextFailure] ;
 * - vérifier ce qui a été demandé : [calls].
 */
public class FakeSpotifyClient(
    initialSession: SessionState = SessionState.SignedOut,
    savedTracks: Set<TrackId> = emptySet(),
) : SpotifyClient {
    private val _session = MutableStateFlow(initialSession)
    override val session: StateFlow<SessionState> = _session.asStateFlow()

    private val _playlists = MutableStateFlow(PlaylistCollection.EMPTY)
    override val playlists: StateFlow<PlaylistCollection> = _playlists.asStateFlow()

    private val _queue = MutableStateFlow(Queue.EMPTY)
    override val queue: StateFlow<Queue> = _queue.asStateFlow()

    private val saved: MutableSet<TrackId> = savedTracks.toMutableSet()

    private val _calls = mutableListOf<SpotifyCall>()

    /** Ce qui a été demandé, dans l'ordre. */
    public val calls: List<SpotifyCall> get() = _calls.toList()

    /** Profil rendu par [currentUser]. */
    public var user: User = User(UserId("fake-user"), displayName = "Utilisateur de test")

    /** Résultat du prochain [signIn]. Remis à `null` après usage. */
    public var nextSignInResult: Result<Unit>? = null

    /**
     * Panne injectée pour le prochain appel réseau, quel qu'il soit. Remise à `null` après
     * usage, pour qu'un test vérifie une reprise après erreur sans reconstruire le double.
     */
    public var nextFailure: SpotifyError? = null

    /** Indique si [close] a été appelé — une fuite de client est un défaut courant. */
    public var closed: Boolean = false
        private set

    public fun emitSession(state: SessionState) {
        _session.value = state
    }

    public fun emitPlaylists(collection: PlaylistCollection) {
        _playlists.value = collection
    }

    public fun emitQueue(value: Queue) {
        _queue.value = value
    }

    public fun clearCalls() {
        _calls.clear()
    }

    override suspend fun signIn(): Result<Unit> {
        _calls += SpotifyCall.SignIn
        val scripted = nextSignInResult?.also { nextSignInResult = null }
        val result = scripted ?: Result.success(Unit)
        _session.value =
            result.fold(
                onSuccess = { SessionState.Authorized },
                onFailure = { SessionState.Failed(it as? SpotifyError ?: SpotifyError.Network(it)) },
            )
        return result
    }

    override suspend fun signOut() {
        _calls += SpotifyCall.SignOut
        _session.value = SessionState.SignedOut
        _playlists.value = PlaylistCollection.EMPTY
        _queue.value = Queue.EMPTY
    }

    override suspend fun currentUser(): Result<User> {
        _calls += SpotifyCall.CurrentUser
        return failIfScripted() ?: Result.success(user)
    }

    override suspend fun refreshPlaylists(): Result<PlaylistCollection> {
        _calls += SpotifyCall.RefreshPlaylists
        return failIfScripted() ?: Result.success(_playlists.value)
    }

    override suspend fun refreshQueue(): Result<Queue> {
        _calls += SpotifyCall.RefreshQueue
        return failIfScripted() ?: Result.success(_queue.value)
    }

    override suspend fun isSaved(track: TrackId): Result<Boolean> {
        _calls += SpotifyCall.IsSaved(track)
        return failIfScripted() ?: Result.success(track in saved)
    }

    override suspend fun setSaved(
        track: TrackId,
        saved: Boolean,
    ): Result<Unit> {
        _calls += SpotifyCall.SetSaved(track, saved)
        failIfScripted<Unit>()?.let { return it }
        if (saved) this.saved += track else this.saved -= track
        return Result.success(Unit)
    }

    override fun close() {
        closed = true
    }

    private fun <T> failIfScripted(): Result<T>? =
        nextFailure?.let { error ->
            nextFailure = null
            Result.failure(error)
        }
}

/** Trace d'un appel reçu par [FakeSpotifyClient]. */
public sealed interface SpotifyCall {
    public data object SignIn : SpotifyCall

    public data object SignOut : SpotifyCall

    public data object CurrentUser : SpotifyCall

    public data object RefreshPlaylists : SpotifyCall

    public data object RefreshQueue : SpotifyCall

    public data class IsSaved(
        public val track: TrackId,
    ) : SpotifyCall

    public data class SetSaved(
        public val track: TrackId,
        public val saved: Boolean,
    ) : SpotifyCall
}
