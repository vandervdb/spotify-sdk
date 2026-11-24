package org.vander.spotify.model

/** Une ligne de paroles, avec l'instant (en ms depuis le début du titre) où elle commence. */
public data class LyricLine(
    public val startMs: Long,
    public val text: String,
)

/**
 * Les paroles d'un titre.
 *
 * @property lines paroles synchronisées, triées par [LyricLine.startMs] ; vide si la source n'en
 *   a que le texte brut.
 * @property plain texte brut, sans horodatage ; `null` si la source n'en a pas.
 * @property instrumental la source déclare le titre instrumental : il n'y a rien à afficher.
 */
public data class Lyrics(
    public val lines: List<LyricLine> = emptyList(),
    public val plain: String? = null,
    public val instrumental: Boolean = false,
) {
    public val isSynced: Boolean get() = lines.isNotEmpty()
}
