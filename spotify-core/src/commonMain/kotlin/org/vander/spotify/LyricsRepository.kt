package org.vander.spotify

import org.vander.spotify.model.Lyrics

/**
 * Paroles d'un titre.
 *
 * Spotify n'en fournit pas par ses API publiques : la source est un service tiers (LRCLIB). Il
 * reçoit le titre, l'artiste, l'album et la durée de la piste, jamais la session ni le jeton
 * Spotify : la lib ne les lui transmet pas.
 */
public interface LyricsRepository {
    /**
     * @param durationMs durée du titre : LRCLIB s'en sert pour choisir la bonne version.
     * @return les paroles, ou `Result.success(null)` si la source n'en a pas pour ce titre ;
     *   un échec ([SpotifyError.Network], [SpotifyError.Api]…) seulement si l'appel a raté.
     */
    public suspend fun lyrics(
        track: String,
        artist: String,
        album: String?,
        durationMs: Long,
    ): Result<Lyrics?>
}
