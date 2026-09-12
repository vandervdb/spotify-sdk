package org.vander.spotify

/**
 * Toutes les défaillances que la lib expose, en un type scellé.
 *
 * Chaque opération publique rend un `Result`, jamais une exception qui s'échappe : un
 * consommateur React Native n'a pas de `try`/`catch` Kotlin, et un `when` exhaustif sur ce
 * type casse à la compilation si un cas s'ajoute.
 */
public sealed class SpotifyError(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    /** Le transport a échoué : pas de réseau, DNS, TLS, délai dépassé. */
    public class Network(
        cause: Throwable,
    ) : SpotifyError("échec réseau : ${cause.message}", cause)

    /** Spotify a répondu, mais avec une erreur applicative. */
    public class Api(
        public val status: Int,
        public val reason: String,
    ) : SpotifyError("erreur Spotify $status : $reason")

    /** Le token est absent, expiré et non renouvelable, ou révoqué. */
    public class Unauthorized(
        reason: String,
    ) : SpotifyError("non autorisé : $reason")

    /** L'appel exige une session, et aucune n'est ouverte. */
    public class NotSignedIn : SpotifyError("aucune session ouverte — appeler signIn() d'abord")

    /** L'utilisateur a refusé l'autorisation, ou fermé l'écran de connexion. */
    public class AuthorizationCancelled : SpotifyError("autorisation annulée par l'utilisateur")

    /**
     * Le paramètre `state` renvoyé ne correspond pas à celui émis.
     *
     * C'est la protection CSRF de la RFC 6749 §10.12 : une réponse dont le `state` ne
     * correspond pas ne vient pas du flot qu'on a lancé, et son code ne doit pas être échangé.
     */
    public class StateMismatch : SpotifyError("le paramètre state ne correspond pas — réponse rejetée")

    /** La charge utile ne correspond pas au format attendu. */
    public class Serialization(
        cause: Throwable,
    ) : SpotifyError("réponse illisible : ${cause.message}", cause)
}
