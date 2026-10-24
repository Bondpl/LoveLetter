package com.example.loveletter.data.crypto

import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class EcKeyPair(
    val publicKeyBase64: String,
    val privateKeyBase64: String
)

data class EncryptedPayload(
    val cipherBytes: ByteArray,
    val ivBase64: String
)

object CryptoManager {
    private const val EC_CURVE = "secp256r1"
    private const val AES_GCM_ALGO = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128
    private const val GCM_IV_LENGTH = 12

    fun generateKeyPair(): EcKeyPair {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec(EC_CURVE))
        val keyPair = kpg.generateKeyPair()

        val pubBase64 = Base64.getEncoder().encodeToString(keyPair.public.encoded)
        val privBase64 = Base64.getEncoder().encodeToString(keyPair.private.encoded)
        return EcKeyPair(publicKeyBase64 = pubBase64, privateKeyBase64 = privBase64)
    }

    fun decodePublicKey(base64PublicKey: String): PublicKey {
        val keyBytes = Base64.getDecoder().decode(base64PublicKey.trim())
        val keyFactory = KeyFactory.getInstance("EC")
        return keyFactory.generatePublic(X509EncodedKeySpec(keyBytes))
    }

    fun decodePrivateKey(base64PrivateKey: String): PrivateKey {
        val keyBytes = Base64.getDecoder().decode(base64PrivateKey.trim())
        val keyFactory = KeyFactory.getInstance("EC")
        return keyFactory.generatePrivate(PKCS8EncodedKeySpec(keyBytes))
    }

    fun deriveAesKey(myPrivateKey: PrivateKey, partnerPublicKey: PublicKey): SecretKeySpec {
        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(myPrivateKey)
        keyAgreement.doPhase(partnerPublicKey, true)
        val sharedSecret = keyAgreement.generateSecret()

        val digest = MessageDigest.getInstance("SHA-256")
        val aesKeyBytes = digest.digest(sharedSecret)
        return SecretKeySpec(aesKeyBytes, "AES")
    }

    fun encryptDrawing(
        plainBytes: ByteArray,
        partnerPublicKeyBase64: String,
        myPrivateKeyBase64: String
    ): EncryptedPayload {
        val myPrivKey = decodePrivateKey(myPrivateKeyBase64)
        val partnerPubKey = decodePublicKey(partnerPublicKeyBase64)
        val secretKey = deriveAesKey(myPrivKey, partnerPubKey)

        val iv = ByteArray(GCM_IV_LENGTH)
        SecureRandom().nextBytes(iv)

        val cipher = Cipher.getInstance(AES_GCM_ALGO)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val cipherBytes = cipher.doFinal(plainBytes)
        val ivBase64 = Base64.getEncoder().encodeToString(iv)

        return EncryptedPayload(cipherBytes = cipherBytes, ivBase64 = ivBase64)
    }

    fun decryptDrawing(
        cipherBytes: ByteArray,
        ivBase64: String,
        partnerPublicKeyBase64: String,
        myPrivateKeyBase64: String
    ): ByteArray {
        val myPrivKey = decodePrivateKey(myPrivateKeyBase64)
        val partnerPubKey = decodePublicKey(partnerPublicKeyBase64)
        val secretKey = deriveAesKey(myPrivKey, partnerPubKey)

        val iv = Base64.getDecoder().decode(ivBase64.trim())
        val cipher = Cipher.getInstance(AES_GCM_ALGO)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return cipher.doFinal(cipherBytes)
    }
}
