package org.vander.spotify.internal.crypto

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Condensé SHA-256, délégué à la plateforme.
 *
 * Volontairement pas réimplémenté en Kotlin commun : une primitive cryptographique écrite à
 * la main est une dette de sécurité, même quand l'algorithme est simple. JVM et Darwin
 * fournissent tous deux une implémentation éprouvée.
 */
internal expect fun sha256(input: ByteArray): ByteArray

/**
 * Octets aléatoires issus du CSPRNG de la plateforme.
 *
 * `kotlin.random.Random` ne convient pas ici : il n'est pas cryptographiquement sûr, et le
 * `code_verifier` PKCE est précisément ce qui remplace le secret client.
 */
internal expect fun secureRandomBytes(size: Int): ByteArray

/**
 * Base64 « URL and Filename safe » sans remplissage, tel que l'exige la RFC 7636 §4.2
 * (qui renvoie à la RFC 4648 §5 en précisant que le remplissage est omis).
 */
@OptIn(ExperimentalEncodingApi::class)
internal fun base64UrlNoPad(bytes: ByteArray): String =
    Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(bytes)
