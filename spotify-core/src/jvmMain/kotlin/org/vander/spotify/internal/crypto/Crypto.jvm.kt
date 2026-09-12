package org.vander.spotify.internal.crypto

import java.security.MessageDigest
import java.security.SecureRandom

internal actual fun sha256(input: ByteArray): ByteArray =
    MessageDigest.getInstance("SHA-256").digest(input)

private val secureRandom by lazy { SecureRandom() }

internal actual fun secureRandomBytes(size: Int): ByteArray {
    require(size > 0) { "size must be positive, was $size" }
    return ByteArray(size).also { secureRandom.nextBytes(it) }
}
