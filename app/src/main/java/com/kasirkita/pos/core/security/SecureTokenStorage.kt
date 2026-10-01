package com.kasirkita.pos.core.security

/**
 * High-level token storage abstraction.
 * Format: "v1:<base64-iv>:<base64-ciphertext>"
 */
open class SecureTokenStorage(
    private val crypto: AndroidKeystoreAesGcm? = null,
) {

    open fun encrypt(token: String): String {
        return requireNotNull(crypto) { "Crypto implementation required" }.encrypt(token)
    }

    open fun decrypt(payload: String): String {
        return requireNotNull(crypto) { "Crypto implementation required" }.decrypt(payload)
    }

    open fun isAvailable(): Boolean {
        return requireNotNull(crypto) { "Crypto implementation required" }.hasValidKey()
    }

    companion object {
        fun isValidFormat(payload: String?): Boolean {
            if (payload.isNullOrBlank()) return false
            val parts = payload.split(":", limit = 3)
            return parts.size == 3 && parts[0] == "v1" && parts[1].isNotBlank() && parts[2].isNotBlank()
        }
    }
}

class TokenStorageException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
