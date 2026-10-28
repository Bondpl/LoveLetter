package com.bond.loveletter

import com.bond.loveletter.data.crypto.CryptoManager
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class CryptoManagerTest {

    @Test
    fun testKeyGenerationAndSharedSecretE2EE() {
        // User A generates keypair
        val keyPairA = CryptoManager.generateKeyPair()
        assertNotNull(keyPairA.publicKeyBase64)
        assertNotNull(keyPairA.privateKeyBase64)

        // User B generates keypair
        val keyPairB = CryptoManager.generateKeyPair()
        assertNotNull(keyPairB.publicKeyBase64)
        assertNotNull(keyPairB.privateKeyBase64)

        // Check public keys are valid EC keys
        val pubA = CryptoManager.decodePublicKey(keyPairA.publicKeyBase64)
        val privA = CryptoManager.decodePrivateKey(keyPairA.privateKeyBase64)
        val pubB = CryptoManager.decodePublicKey(keyPairB.publicKeyBase64)
        val privB = CryptoManager.decodePrivateKey(keyPairB.privateKeyBase64)

        // Check shared secrets derived by A and B match
        val aesKeyA = CryptoManager.deriveAesKey(privA, pubB)
        val aesKeyB = CryptoManager.deriveAesKey(privB, pubA)
        assertArrayEquals(aesKeyA.encoded, aesKeyB.encoded)

        // Test encryption of sample drawing data by User A to User B
        val originalDrawing = "PNG_DUMMY_IMAGE_BYTES_1234567890".toByteArray(Charsets.UTF_8)
        val encryptedPayload = CryptoManager.encryptDrawing(
            plainBytes = originalDrawing,
            partnerPublicKeyBase64 = keyPairB.publicKeyBase64,
            myPrivateKeyBase64 = keyPairA.privateKeyBase64
        )

        // User B decrypts the drawing using their own private key and User A's public key
        val decryptedDrawing = CryptoManager.decryptDrawing(
            cipherBytes = encryptedPayload.cipherBytes,
            ivBase64 = encryptedPayload.ivBase64,
            partnerPublicKeyBase64 = keyPairA.publicKeyBase64,
            myPrivateKeyBase64 = keyPairB.privateKeyBase64
        )

        assertArrayEquals(originalDrawing, decryptedDrawing)
        assertEquals(String(originalDrawing, Charsets.UTF_8), String(decryptedDrawing, Charsets.UTF_8))
    }
}
