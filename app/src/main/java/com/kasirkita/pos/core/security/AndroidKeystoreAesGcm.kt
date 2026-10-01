package com.kasirkita.pos.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.InvalidKeyException
import java.security.KeyStore
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val KEYSTORE_ALIAS = "kasirkita_auth_token_key"
private const val KEY_SIZE = 256
private const val CIPHER_ALGORITHM = "AES/GCM/NoPadding"
private const val IV_BYTE_SIZE = 12
private const val GCM_TAG_BIT_LENGTH = 128
private const val PAYLOAD_VERSION = "v1"

/**
 * AES-256-GCM encryption using Android Keystore.
 * Key material remains non-exportable and does not require user authentication,
 * so WorkManager can decrypt tokens while running in the background.
 */
open class AndroidKeystoreAesGcm {

    private val keyStore by lazy {
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    }

    private val secretKey: SecretKey
        get() {
            val entry = keyStore.getEntry(KEYSTORE_ALIAS, null) as? KeyStore.SecretKeyEntry
            return entry?.secretKey ?: generateKey()
        }

    private fun generateKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore",
        )

        val keySpec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setKeySize(KEY_SIZE)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(false)
            .setInvalidatedByBiometricEnrollment(false)
            .build()

        keyGenerator.init(keySpec)
        return keyGenerator.generateKey()
    }

    /**
     * Encrypt plaintext using Android Keystore generated fresh 96-bit GCM IV.
     * Returns formatted string: "v1:<base64-iv>:<base64-ciphertext>".
     */
    open fun encrypt(plaintext: String): String {
        return try {
            val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            if (iv.size != IV_BYTE_SIZE) {
                throw TokenStorageException("Unexpected IV length")
            }

            val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            val encodedIv = Base64.encodeToString(iv, Base64.NO_WRAP)
            val encodedCiphertext = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
            "$PAYLOAD_VERSION:$encodedIv:$encodedCiphertext"
        } catch (e: TokenStorageException) {
            throw e
        } catch (e: Exception) {
            throw TokenStorageException("Encryption failed", e)
        }
    }

    /**
     * Decrypt formatted payload. Rejects malformed and unsupported formats.
     */
    open fun decrypt(payload: String): String {
        val parts = payload.split(":", limit = 3)
        if (parts.size != 3 || parts[0] != PAYLOAD_VERSION || parts[1].isBlank() || parts[2].isBlank()) {
            throw TokenStorageException("Invalid payload format or unsupported version")
        }

        try {
            val iv = Base64.decode(parts[1], Base64.NO_WRAP)
            val ciphertext = Base64.decode(parts[2], Base64.NO_WRAP)
            if (iv.size != IV_BYTE_SIZE || ciphertext.isEmpty()) {
                throw TokenStorageException("Invalid payload data")
            }

            val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, existingSecretKey(), GCMParameterSpec(GCM_TAG_BIT_LENGTH, iv))
            val plaintextBytes = cipher.doFinal(ciphertext)
            return String(plaintextBytes, Charsets.UTF_8)
        } catch (e: TokenStorageException) {
            throw e
        } catch (e: IllegalArgumentException) {
            throw TokenStorageException("Invalid base64 encoding", e)
        } catch (e: AEADBadTagException) {
            throw TokenStorageException("Authentication tag verification failed", e)
        } catch (e: InvalidKeyException) {
            throw TokenStorageException("Keystore key invalid or permanently invalidated", e)
        } catch (e: Exception) {
            throw TokenStorageException("Decryption failed", e)
        }
    }

    private fun existingSecretKey(): SecretKey {
        val entry = keyStore.getEntry(KEYSTORE_ALIAS, null) as? KeyStore.SecretKeyEntry
        return entry?.secretKey ?: throw TokenStorageException("Keystore key missing")
    }

    open fun hasValidKey(): Boolean = try {
        val test = encrypt("test")
        decrypt(test) == "test"
    } catch (_: Exception) {
        false
    }

    companion object {
        fun isSupported(): Boolean = try {
            Cipher.getInstance(CIPHER_ALGORITHM)
            KeyStore.getInstance("AndroidKeyStore")
            true
        } catch (_: Exception) {
            false
        }
    }
}
