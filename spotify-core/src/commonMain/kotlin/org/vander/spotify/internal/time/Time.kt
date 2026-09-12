package org.vander.spotify.internal.time

/**
 * Horloge murale en millisecondes depuis l'époque Unix.
 *
 * `expect`/`actual` plutôt qu'une dépendance à kotlinx-datetime : le besoin tient en une
 * ligne par plateforme, et une lib publiée impose le moins de dépendances transitives
 * possible à ses consommateurs.
 */
internal expect fun currentTimeMillis(): Long

/** Seam d'injection pour les tests : l'expiration d'un token doit être pilotable. */
internal fun interface Clock {
    fun nowMs(): Long

    companion object {
        val System: Clock = Clock { currentTimeMillis() }
    }
}
