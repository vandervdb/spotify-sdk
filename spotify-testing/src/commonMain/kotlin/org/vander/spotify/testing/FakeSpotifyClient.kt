package org.vander.spotify.testing

import org.vander.spotify.model.PlaylistId
import org.vander.spotify.model.PlayHistory
import org.vander.spotify.model.PlaylistDetail
import org.vander.spotify.model.SearchResults
import org.vander.spotify.model.SearchType
import org.vander.spotify.model.ArtistDetail
import org.vander.spotify.model.AlbumDetail
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.vander.spotify.SpotifyClient
import org.vander.spotify.SpotifyError
import org.vander.spotify.auth.SessionState
import org.vander.spotify.model.AlbumCollection
import org.vander.spotify.model.ArtistCollection
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

    private val _savedAlbums = MutableStateFlow(AlbumCollection.EMPTY)
    override val savedAlbums: StateFlow<AlbumCollection> = _savedAlbums.asStateFlow()

    private val _followedArtists = MutableStateFlow(ArtistCollection.EMPTY)
    override val followedArtists: StateFlow<ArtistCollection> = _followedArtists.asStateFlow()

    private val _queue = MutableStateFlow(Queue.EMPTY)
    override val queue: StateFlow<Queue> = _queue.asStateFlow()

    private val saved: MutableSet<TrackId> = savedTracks.toMutableSet()

    private val following: MutableSet<String> = mutableSetOf()

    private val _calls = mutableListOf<SpotifyCall>()

    /** Ce qui a été demandé, dans l'ordre. */
    public val calls: List<SpotifyCall> get() = _calls.toList()

    /** Profil rendu par [currentUser]. */
    public var user: User = User(UserId("fake-user"), displayName = "Utilisateur de test")

    /** Valeur rendue par [savedTracksTotal]. */
    public var savedTracksCount: Int = 0

    /** Rendu par [search], quelle que soit la requête non vide. */
    public var searchResults: SearchResults = SearchResults.EMPTY

    /**
     * Historique rendu par [recentlyPlayed], du plus récent au plus ancien, tronqué à `limit` et
     * filtré par `beforeMs`.
     */
    public var playHistory: List<PlayHistory> = emptyList()

    /** Détails rendus par [album] et [artist], par identifiant. Un identifiant absent échoue en 404. */
    public val albumDetails: MutableMap<String, AlbumDetail> = mutableMapOf()
    public val artistDetails: MutableMap<String, ArtistDetail> = mutableMapOf()
    public val playlistDetails: MutableMap<String, PlaylistDetail> = mutableMapOf()

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

    public fun emitSavedAlbums(collection: AlbumCollection) {
        _savedAlbums.value = collection
    }

    public fun emitFollowedArtists(collection: ArtistCollection) {
        _followedArtists.value = collection
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

    override suspend fun restoreSession(): Result<Unit> {
        _calls += SpotifyCall.RestoreSession
        return failIfScripted() ?: if (_session.value is SessionState.Authorized) {
            Result.success(Unit)
        } else {
            Result.failure(SpotifyError.NotSignedIn())
        }
    }

    override suspend fun signOut() {
        _calls += SpotifyCall.SignOut
        _session.value = SessionState.SignedOut
        _playlists.value = PlaylistCollection.EMPTY
        _savedAlbums.value = AlbumCollection.EMPTY
        _followedArtists.value = ArtistCollection.EMPTY
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

    override suspend fun refreshSavedAlbums(): Result<AlbumCollection> {
        _calls += SpotifyCall.RefreshSavedAlbums
        return failIfScripted() ?: Result.success(_savedAlbums.value)
    }

    override suspend fun refreshFollowedArtists(): Result<ArtistCollection> {
        _calls += SpotifyCall.RefreshFollowedArtists
        return failIfScripted() ?: Result.success(_followedArtists.value)
    }

    override suspend fun savedTracksTotal(): Result<Int> {
        _calls += SpotifyCall.SavedTracksTotal
        return failIfScripted() ?: Result.success(savedTracksCount)
    }

    override suspend fun search(
        query: String,
        limit: Int,
        types: Set<SearchType>,
        offset: Int,
    ): Result<SearchResults> {
        _calls += SpotifyCall.Search(query)
        if (query.isBlank()) return failIfScripted() ?: Result.success(SearchResults.EMPTY)
        // Même contrat que le vrai client : un genre non demandé reste vide.
        val r = searchResults
        return failIfScripted()
            ?: Result.success(
                SearchResults(
                    albums = if (SearchType.Album in types) r.albums else emptyList(),
                    artists = if (SearchType.Artist in types) r.artists else emptyList(),
                    playlists = if (SearchType.Playlist in types) r.playlists else emptyList(),
                    tracks = if (SearchType.Track in types) r.tracks else emptyList(),
                ),
            )
    }

    override suspend fun album(id: String): Result<AlbumDetail> {
        _calls += SpotifyCall.GetAlbum(id)
        return failIfScripted() ?: albumDetails[id]?.let { Result.success(it) } ?: Result.failure(SpotifyError.Api(404, "album inconnu"))
    }

    override suspend fun artist(id: String): Result<ArtistDetail> {
        _calls += SpotifyCall.GetArtist(id)
        return failIfScripted() ?: artistDetails[id]?.let { Result.success(it) } ?: Result.failure(SpotifyError.Api(404, "artiste inconnu"))
    }

    override suspend fun playlist(id: PlaylistId): Result<PlaylistDetail> {
        _calls += SpotifyCall.GetPlaylist(id)
        return failIfScripted() ?: playlistDetails[id.value]?.let { Result.success(it) } ?: Result.failure(SpotifyError.Api(404, "playlist inconnue"))
    }

    override suspend fun isFollowingArtist(id: String): Result<Boolean> {
        _calls += SpotifyCall.IsFollowingArtist(id)
        return failIfScripted() ?: Result.success(id in following)
    }

    override suspend fun setFollowingArtist(
        id: String,
        following: Boolean,
    ): Result<Unit> {
        _calls += SpotifyCall.SetFollowingArtist(id, following)
        failIfScripted<Unit>()?.let { return it }
        if (following) this.following += id else this.following -= id
        return Result.success(Unit)
    }

    override suspend fun refreshQueue(): Result<Queue> {
        _calls += SpotifyCall.RefreshQueue
        return failIfScripted() ?: Result.success(_queue.value)
    }

    override suspend fun recentlyPlayed(
        limit: Int,
        beforeMs: Long?,
    ): Result<List<PlayHistory>> {
        _calls += SpotifyCall.RecentlyPlayed(limit, beforeMs)
        require(limit in 1..50) { "limit doit être dans 1..50" }
        val older = if (beforeMs == null) playHistory else playHistory.filter { it.playedAtMs < beforeMs }
        return failIfScripted() ?: Result.success(older.take(limit))
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

    public data object RestoreSession : SpotifyCall

    public data object SignOut : SpotifyCall

    public data object CurrentUser : SpotifyCall

    public data object RefreshPlaylists : SpotifyCall

    public data object RefreshSavedAlbums : SpotifyCall

    public data object RefreshFollowedArtists : SpotifyCall

    public data object SavedTracksTotal : SpotifyCall

    public data class Search(
        public val query: String,
    ) : SpotifyCall

    public data class GetAlbum(
        public val id: String,
    ) : SpotifyCall

    public data class GetArtist(
        public val id: String,
    ) : SpotifyCall

    public data class GetPlaylist(
        public val id: PlaylistId,
    ) : SpotifyCall

    public data class IsFollowingArtist(
        public val id: String,
    ) : SpotifyCall

    public data class SetFollowingArtist(
        public val id: String,
        public val following: Boolean,
    ) : SpotifyCall

    public data object RefreshQueue : SpotifyCall

    public data class RecentlyPlayed(
        public val limit: Int,
        public val beforeMs: Long? = null,
    ) : SpotifyCall

    public data class IsSaved(
        public val track: TrackId,
    ) : SpotifyCall

    public data class SetSaved(
        public val track: TrackId,
        public val saved: Boolean,
    ) : SpotifyCall
}
