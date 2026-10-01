package com.kasirkita.pos.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecureTokenStorageTest {

    private val storage = FakeSecureTokenStorage()

    @Test
    fun encrypt_delegatesToCrypto() {
        val token = "test_access_token"
        val result = storage.encrypt(token)

        assertTrue(result.startsWith("v1:"))
    }

    @Test
    fun decrypt_delegatesToCrypto() {
        val encrypted = storage.encrypt("test_token")
        val result = storage.decrypt(encrypted)

        assertEquals("test_token", result)
    }

    @Test
    fun isValidFormat_validFormat_returnsTrue() {
        assertTrue(SecureTokenStorage.isValidFormat("v1:abc:def"))
        assertTrue(SecureTokenStorage.isValidFormat("v1:123456789012:encodeddata"))
    }

    @Test
    fun isValidFormat_invalidVersion_returnsFalse() {
        assertFalse(SecureTokenStorage.isValidFormat("v2:abc:def"))
        assertFalse(SecureTokenStorage.isValidFormat("v0:abc:def"))
    }

    @Test
    fun isValidFormat_missingParts_returnsFalse() {
        assertFalse(SecureTokenStorage.isValidFormat("v1:abc"))
        assertFalse(SecureTokenStorage.isValidFormat("v1:"))
        assertFalse(SecureTokenStorage.isValidFormat(""))
        assertFalse(SecureTokenStorage.isValidFormat(null))
    }

    @Test
    fun isAvailable_returnsTrue() {
        assertTrue(storage.isAvailable())
    }

    @Test
    fun decrypt_withMalformedFormat_throws() {
        try {
            storage.decrypt("not_a_valid_format")
            org.junit.Assert.fail("Expected TokenStorageException")
        } catch (e: TokenStorageException) {
            // Expected
        }
    }
}
