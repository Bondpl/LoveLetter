package com.bond.loveletter

import com.bond.loveletter.data.crypto.SecureKeyStorage
import org.junit.Assert.assertEquals
import org.junit.Test

class SecureKeyStorageTest {

    @Test
    fun testUnencryptedFallbackAndRoundtrip() {
        // Plaintext legacy token or key without prefix should be returned directly
        val rawKey = "MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgExampleKey"
        assertEquals(rawKey, SecureKeyStorage.decrypt(rawKey))

        // Encrypt and decrypt roundtrip in JVM fallback mode
        val encrypted = SecureKeyStorage.encrypt(rawKey)
        val decrypted = SecureKeyStorage.decrypt(encrypted)
        assertEquals(rawKey, decrypted)
    }
}
