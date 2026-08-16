package com.lrv.privatechat.crypto

import android.util.Base64
import java.security.PrivateKey
import java.security.PublicKey
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

data class EncryptedMessage(
    val cipherText: String,
    val iv: String
)

data class EncryptedBytes(
    val cipherBytes: ByteArray,
    val iv: ByteArray
)

object CryptoUtils {

    private const val AES_ALGORITHM = "AES"
    private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val ECDH_ALGORITHM = "ECDH"
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val IV_SIZE_BYTES = 12

    fun encrypt(
        plainText: String,
        privateKey: PrivateKey,
        publicKey: PublicKey
    ): EncryptedMessage {
        val secretKey = deriveAesKey(privateKey, publicKey)
        val iv = Random.nextBytes(IV_SIZE_BYTES)

        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))

        val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        return EncryptedMessage(
            cipherText = Base64.encodeToString(cipherBytes, Base64.NO_WRAP),
            iv = Base64.encodeToString(iv, Base64.NO_WRAP)
        )
    }

    fun decrypt(
        cipherText: String,
        iv: String,
        privateKey: PrivateKey,
        publicKey: PublicKey
    ): String {
        val secretKey = deriveAesKey(privateKey, publicKey)
        val cipherBytes = Base64.decode(cipherText, Base64.NO_WRAP)
        val ivBytes = Base64.decode(iv, Base64.NO_WRAP)

        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, ivBytes))

        return String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
    }

    fun encryptBytes(
        data: ByteArray,
        privateKey: PrivateKey,
        publicKey: PublicKey
    ): EncryptedBytes {
        val secretKey = deriveAesKey(privateKey, publicKey)
        val iv = Random.nextBytes(IV_SIZE_BYTES)

        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))

        return EncryptedBytes(
            cipherBytes = cipher.doFinal(data),
            iv = iv
        )
    }

    fun decryptBytes(
        cipherBytes: ByteArray,
        iv: ByteArray,
        privateKey: PrivateKey,
        publicKey: PublicKey
    ): ByteArray {
        val secretKey = deriveAesKey(privateKey, publicKey)

        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))

        return cipher.doFinal(cipherBytes)
    }

    private fun deriveAesKey(privateKey: PrivateKey, publicKey: PublicKey): SecretKey {
        val agreement = KeyAgreement.getInstance(ECDH_ALGORITHM)
        agreement.init(privateKey)
        agreement.doPhase(publicKey, true)
        val sharedSecret = agreement.generateSecret()

        val digest = MessageDigest.getInstance("SHA-256")
        val keyBytes = digest.digest(sharedSecret)

        return SecretKeySpec(keyBytes, AES_ALGORITHM)
    }
}
