package org.vander.spotify.internal.crypto

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault

@OptIn(ExperimentalForeignApi::class)
internal actual fun sha256(input: ByteArray): ByteArray {
    val digest = ByteArray(CC_SHA256_DIGEST_LENGTH)
    digest.usePinned { pinnedDigest ->
        if (input.isEmpty()) {
            // `addressOf(0)` lève sur un tableau vide ; CC_SHA256 accepte un pointeur nul
            // à condition que la longueur soit 0.
            CC_SHA256(null, 0u, pinnedDigest.addressOf(0).reinterpret())
        } else {
            input.usePinned { pinnedInput ->
                CC_SHA256(
                    pinnedInput.addressOf(0),
                    input.size.toUInt(),
                    pinnedDigest.addressOf(0).reinterpret(),
                )
            }
        }
    }
    return digest
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun secureRandomBytes(size: Int): ByteArray {
    require(size > 0) { "size must be positive, was $size" }
    val out = ByteArray(size)
    val status =
        out.usePinned { pinned ->
            SecRandomCopyBytes(kSecRandomDefault, size.toULong(), pinned.addressOf(0))
        }
    // errSecSuccess vaut 0 ; toute autre valeur signifie que le CSPRNG a refusé.
    check(status == 0) { "SecRandomCopyBytes a échoué, status=$status" }
    return out
}
