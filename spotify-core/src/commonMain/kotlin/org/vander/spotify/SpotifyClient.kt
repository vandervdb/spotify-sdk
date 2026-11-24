package org.vander.spotify

import org.vander.spotify.model.PlaylistId
import org.vander.spotify.model.PlaylistDetail
import org.vander.spotify.model.SearchResults
import org.vander.spotify.model.SearchType
import org.vander.spotify.model.ArtistDetail
import org.vander.spotify.model.AlbumDetail
import kotlinx.coroutines.flow.StateFlow
import org.vander.spotify.auth.SessionState
import org.vander.spotify.model.AlbumCollection
import org.vander.spotify.model.ArtistCollection
import org.vander.spotify.model.PlaylistCollection
import org.vander.spotify.model.Queue
import org.vander.spotify.model.TrackId
import org.vander.spotify.model.User

/**
 * La porte d'entrée de la lib, disponible sur toutes les cibles.
 *
 * Le contrôle de lecture local n'est pas ici : il passe par l'App Remote, qui n'existe que
 * sur Android, et vit donc dans `SpotifyPlayer` (artefact `:spotify-android`). Ce n'est pas
 * un oubli mais le contrat : sur iOS, le code qui appellerait `skipNext()` ne compile pas,
 * plutôt que d'échouer à l'exécution.
 *
 * Toutes les opérations rendent un [Result] dont l'échec est un [SpotifyError] — aucune
 * exception ne traverse cette frontière.
 */
public interface SpotifyClient {
    /** Identité seulement : connecté ou non. Voir [SessionState]. */
    public val session: StateFlow<SessionState>

    /** Dernières playlists connues. Vide tant que [refreshPlaylists] n'a pas abouti. */
    public val playlists: StateFlow<PlaylistCollection>

    /** Derniers albums enregistrés connus. Vide tant que [refreshSavedAlbums] n'a pas abouti. */
    public val savedAlbums: StateFlow<AlbumCollection>

    /** Derniers artistes suivis connus. Vide tant que [refreshFollowedArtists] n'a pas abouti. */
    public val followedArtists: StateFlow<ArtistCollection>

    /** Dernière file d'attente connue. Instantané, rafraîchi par [refreshQueue]. */
    public val queue: StateFlow<Queue>

    /**
     * Ouvre l'écran d'autorisation Spotify et échange le code contre un jeton.
     *
     * Suspend jusqu'à l'issue. Il n'y a pas d'ordre d'appel à retenir et pas de `launcher` à
     * fournir : l'[org.vander.spotify.auth.Authorizer] passé à la construction s'en charge.
     */
    public suspend fun signIn(): Result<Unit>

    /**
     * Reprend la session depuis le jeton conservé par le `TokenStore`, en le renouvelant si
     * besoin. N'ouvre jamais l'écran d'autorisation.
     *
     * À appeler au démarrage : [session] vaut [SessionState.SignedOut] tant qu'aucun appel
     * n'a lu le jeton, même si l'utilisateur s'est connecté à un lancement précédent. Rend
     * [SpotifyError.NotSignedIn] — sans toucher à [session] — quand il n'y a rien à reprendre.
     */
    public suspend fun restoreSession(): Result<Unit>

    /** Efface le jeton et ramène [session] à [SessionState.SignedOut]. */
    public suspend fun signOut()

    public suspend fun currentUser(): Result<User>

    public suspend fun refreshPlaylists(): Result<PlaylistCollection>

    public suspend fun refreshSavedAlbums(): Result<AlbumCollection>

    /** Exige le scope [org.vander.spotify.SpotifyScope.UserFollowRead]. */
    public suspend fun refreshFollowedArtists(): Result<ArtistCollection>

    /** Nombre de titres likés. Une seule requête d'un élément : on ne rapatrie pas la liste. */
    public suspend fun savedTracksTotal(): Result<Int>

    /**
     * Cherche dans les [types] demandés (tous par défaut). Un résultat par appel, rien n'est
     * mémorisé. Les genres non demandés restent vides dans [SearchResults].
     *
     * @param offset rang du premier résultat, pour paginer un genre (`limit` + `offset` à chaque page).
     * @throws IllegalArgumentException si [types] est vide.
     */
    public suspend fun search(
        query: String,
        limit: Int = 20,
        types: Set<SearchType> = SearchType.entries.toSet(),
        offset: Int = 0,
    ): Result<SearchResults>

    public suspend fun album(id: String): Result<AlbumDetail>

    public suspend fun artist(id: String): Result<ArtistDetail>

    public suspend fun playlist(id: PlaylistId): Result<PlaylistDetail>

    public suspend fun isFollowingArtist(id: String): Result<Boolean>

    /**
     * Suit ou ne suit plus l'artiste. Un seul verbe avec un booléen, comme [setSaved]. Exige le
     * scope [org.vander.spotify.SpotifyScope.UserFollowModify] ; [followedArtists] n'est pas mis à
     * jour, l'appelant relit avec [refreshFollowedArtists].
     */
    public suspend fun setFollowingArtist(
        id: String,
        following: Boolean,
    ): Result<Unit>

    public suspend fun refreshQueue(): Result<Queue>

    public suspend fun isSaved(track: TrackId): Result<Boolean>

    /**
     * Ajoute ou retire [track] de la bibliothèque de l'utilisateur.
     *
     * Un seul verbe avec un booléen plutôt que `save`/`remove` : l'appelant tient un état
     * binaire, et deux méthodes l'obligeaient à choisir laquelle appeler — c'est exactement
     * là que l'implémentation d'origine avait divergé entre deux chemins de code.
     */
    public suspend fun setSaved(
        track: TrackId,
        saved: Boolean,
    ): Result<Unit>

    /** Libère le client HTTP. Après cet appel l'instance n'est plus utilisable. */
    public fun close()
}
