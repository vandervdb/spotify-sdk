package org.vander.spotify

import org.vander.spotify.auth.Pkce
import org.vander.spotify.internal.crypto.base64UrlNoPad
import org.vander.spotify.internal.crypto.secureRandomBytes
import org.vander.spotify.internal.crypto.sha256
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Ces tests tournent sur toutes les cibles, donc ils valident aussi bien l'implémentation
 * JVM que l'implémentation Darwin du `expect fun sha256`. Une seule suite, deux preuves.
 */
class PkceTest {
    @Test
    fun `vecteur de reference RFC 7636 annexe B`() {
        // Le couple publié par la RFC. S'il sort juste, l'enchaînement
        // ASCII → SHA-256 → base64url-sans-remplissage est correct de bout en bout.
        val challenge = Pkce.fromVerifier("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk")

        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", challenge.challenge)
        assertEquals("S256", challenge.method)
    }

    @Test
    fun `sha256 vecteurs NIST`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            sha256("abc".encodeToByteArray()).toHex(),
        )
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            sha256(ByteArray(0)).toHex(),
        )
    }

    @Test
    fun `base64url n'emet ni remplissage ni caractere non sur`() {
        val encoded = base64UrlNoPad(byteArrayOf(-5, -16, 0, 1, 2, 3, 4))

        assertTrue(encoded.none { it == '=' || it == '+' || it == '/' }, "encodé : $encoded")
    }

    @Test
    fun `un verifier genere respecte les bornes de la RFC`() {
        val challenge = Pkce.generate()

        assertTrue(challenge.verifier.length in 43..128, "longueur ${challenge.verifier.length}")
        assertTrue(challenge.verifier.all { it.isUnreserved() }, challenge.verifier)
    }

    @Test
    fun `deux generations ne partagent pas le meme verifier`() {
        // Le CSPRNG est la seule chose qui tient la sécurité du flot : s'il se répète,
        // PKCE ne protège plus rien.
        assertNotEquals(Pkce.generate().verifier, Pkce.generate().verifier)
    }

    @Test
    fun `le CSPRNG rend bien le nombre d'octets demande`() {
        assertEquals(32, secureRandomBytes(32).size)
        assertFailsWith<IllegalArgumentException> { secureRandomBytes(0) }
    }

    @Test
    fun `un verifier trop court est refuse`() {
        assertFailsWith<IllegalArgumentException> { Pkce.fromVerifier("trop-court") }
    }

    private fun Char.isUnreserved(): Boolean =
        this in 'A'..'Z' || this in 'a'..'z' || this in '0'..'9' || this in "-._~"

    private fun ByteArray.toHex(): String =
        joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
}
