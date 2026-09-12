package org.vander.spotify.auth

import org.vander.spotify.internal.crypto.base64UrlNoPad
import org.vander.spotify.internal.crypto.secureRandomBytes
import org.vander.spotify.internal.crypto.sha256

/**
 * Couple `code_verifier` / `code_challenge` d'un échange PKCE (RFC 7636).
 *
 * Le verifier ne quitte jamais l'appareil avant l'échange du code : c'est lui qui prouve au
 * serveur d'autorisation que le client qui présente le code est bien celui qui a lancé le
 * flot. C'est ce qui permet de se passer d'un `client_secret`, lequel n'a de toute façon
 * aucun sens dans une application distribuée — un `unzip` de l'APK le rendrait.
 */
public class PkceChallenge internal constructor(
    public val verifier: String,
    public val challenge: String,
) {
    public val method: String get() = METHOD

    override fun toString(): String = "PkceChallenge(challenge=$challenge, method=$METHOD)"

    public companion object {
        /** Seul `S256` est accepté ici ; `plain` est autorisé par la RFC mais sans intérêt. */
        public const val METHOD: String = "S256"
    }
}

internal object Pkce {
    /**
     * 32 octets aléatoires produisent 43 caractères en base64url, soit exactement la borne
     * basse imposée par la RFC 7636 §4.1 (43 à 128 caractères).
     */
    const val VERIFIER_BYTES: Int = 32

    fun generate(): PkceChallenge = fromVerifier(base64UrlNoPad(secureRandomBytes(VERIFIER_BYTES)))

    /**
     * Chemin déterministe, utilisé par les tests : la RFC publie un couple de référence,
     * donc la correction de cette fonction se prouve au lieu de s'affirmer.
     */
    fun fromVerifier(verifier: String): PkceChallenge {
        require(verifier.length in 43..128) {
            "code_verifier doit faire 43 à 128 caractères (RFC 7636 §4.1), ici ${verifier.length}"
        }
        // §4.2 : code_challenge = BASE64URL-ENCODE(SHA256(ASCII(code_verifier)))
        return PkceChallenge(verifier, base64UrlNoPad(sha256(verifier.encodeToByteArray())))
    }
}
