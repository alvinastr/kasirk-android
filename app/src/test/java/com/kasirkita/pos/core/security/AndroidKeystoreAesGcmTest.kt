package com.kasirkita.pos.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidKeystoreAesGcmTest {

    @Test
    fun fakeCrypto_roundTrip_producesOriginalPlaintext() {
        val crypto = FakeSecureTokenStorage()

        val original = "test_token_12345"
        val encrypted = crypto.encrypt(original)
        val decrypted = crypto.decrypt(encrypted)

        assertEquals(original, decrypted)
        assertTrue(encrypted.startsWith("v1:"))
    }

    @Test
    fun fakeCrypto_rejectsMalformedFormat() {
        val crypto = FakeSecureTokenStorage()

        try {
            crypto.decrypt("not-valid")
            org.junit.Assert.fail("Expected TokenStorageException")
        } catch (e: TokenStorageException) {
            // Expected
        }
    }

    @Test
    fun isSupported_isSafeToCallOnJvm() {
        AndroidKeystoreAesGcm.isSupported()
    }
}
