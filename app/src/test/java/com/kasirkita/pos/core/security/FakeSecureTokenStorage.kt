package com.kasirkita.pos.core.security

import java.util.Base64
import java.util.concurrent.atomic.AtomicLong

/**
 * Fake SecureTokenStorage for unit tests.
 * Versioned non-secret format, fresh nonce marker, JVM-only.
 */
class FakeSecureTokenStorage(
    private val failDecryptForPayloads: Set<String> = emptySet(),
) : SecureTokenStorage(
    crypto = object : AndroidKeystoreAesGcm() {
        private val counter = AtomicLong(0)

        override fun encrypt(plaintext: String): String {
            val nonce = "fakeiv%06d".format(counter.incrementAndGet())
            val encoded = Base64.getEncoder().encodeToString(plaintext.toByteArray(Charsets.UTF_8))
            return "v1:$nonce:$encoded"
        }

        override fun decrypt(payload: String): String {
            if (payload in failDecryptForPayloads) {
                throw TokenStorageException("Forced decrypt failure")
            }
            val parts = payload.split(":", limit = 3)
            if (parts.size != 3 || parts[0] != "v1" || parts[1].isBlank() || parts[2].isBlank()) {
                throw TokenStorageException("Invalid payload format")
            }
            return try {
                String(Base64.getDecoder().decode(parts[2]), Charsets.UTF_8)
            } catch (e: IllegalArgumentException) {
                throw TokenStorageException("Invalid base64 encoding", e)
            }
        }

        override fun hasValidKey(): Boolean = true
    },
)
