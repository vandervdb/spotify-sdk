package org.vander.spotify.auth

/**
 * Token tel qu'il est conservé entre deux lancements.
 *
 * [expiresAtEpochMs] est une date absolue et non une durée : une durée relative devient
 * fausse dès que le processus est suspendu, ce qui est le cas normal sur mobile.
 */
public data class StoredToken(
    public val accessToken: String,
    public val refreshToken: String?,
    public val expiresAtEpochMs: Long,
)

/**
 * Conservation du token, fournie par la plateforme.
 *
 * Le cœur ne sait pas où le token est écrit : DataStore sur Android, Keychain sur iOS. Il ne
 * sait pas non plus le chiffrer — c'est la responsabilité de l'implémentation, et la raison
 * pour laquelle ce contrat est si étroit.
 */
public interface TokenStore {
    public suspend fun load(): StoredToken?

    public suspend fun save(token: StoredToken)

    public suspend fun clear()
}

/**
 * Implémentation en mémoire, utile en test et pour un hôte sans besoin de persistance.
 *
 * Ne pas l'utiliser en production : le token disparaît à la mort du processus, et
 * l'utilisateur doit se réidentifier à chaque lancement.
 */
public class InMemoryTokenStore : TokenStore {
    private var token: StoredToken? = null

    override suspend fun load(): StoredToken? = token

    override suspend fun save(token: StoredToken) {
        this.token = token
    }

    override suspend fun clear() {
        token = null
    }
}
