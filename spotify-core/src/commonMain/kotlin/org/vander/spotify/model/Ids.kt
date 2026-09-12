package org.vander.spotify.model

// `kotlin.jvm.*` fait partie des imports implicites côté JVM mais pas côté Native :
// sans cet import, le module ne compile que pour la moitié de ses cibles.
import kotlin.jvm.JvmInline

/**
 * Identifiant nu d'une piste, tel que Spotify le rend — pas une URI `spotify:track:<id>`.
 *
 * Le type existe pour fermer une classe de bug observée : dans l'implémentation d'origine,
 * une couche prenait l'identifiant nu et celle du dessous une URI complète, sans que rien
 * ne le signale. Une `value class` coûte zéro à l'exécution et rend la confusion impossible
 * à compiler.
 */
@JvmInline
public value class TrackId(public val value: String) {
    init {
        require(value.isNotBlank()) { "TrackId ne peut pas être vide" }
    }

    /** Forme URI attendue par l'App Remote. */
    public val uri: String get() = "spotify:track:$value"

    override fun toString(): String = value
}

@JvmInline
public value class PlaylistId(public val value: String) {
    init {
        require(value.isNotBlank()) { "PlaylistId ne peut pas être vide" }
    }

    override fun toString(): String = value
}

@JvmInline
public value class UserId(public val value: String) {
    override fun toString(): String = value
}
