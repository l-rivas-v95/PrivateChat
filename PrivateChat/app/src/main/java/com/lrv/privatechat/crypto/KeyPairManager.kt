package com.lrv.privatechat.crypto

import android.content.Context
import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

class KeyPairManager(context: Context) {

    private val preferences = context.getSharedPreferences("private_chat_crypto", Context.MODE_PRIVATE)

    fun getOrCreateKeyPair(): KeyPair {
        val savedPrivateKey = preferences.getString(PRIVATE_KEY, null)
        val savedPublicKey = preferences.getString(PUBLIC_KEY, null)

        if (savedPrivateKey != null && savedPublicKey != null) {
            return KeyPair(
                decodePublicKey(savedPublicKey),
                decodePrivateKey(savedPrivateKey)
            )
        }

        val generator = KeyPairGenerator.getInstance(KEY_ALGORITHM)
        generator.initialize(256)
        val keyPair = generator.generateKeyPair()

        preferences.edit()
            .putString(PRIVATE_KEY, encodeKey(keyPair.private.encoded))
            .putString(PUBLIC_KEY, encodeKey(keyPair.public.encoded))
            .apply()

        return keyPair
    }

    fun getPublicKeyText(): String {
        return encodeKey(getOrCreateKeyPair().public.encoded)
    }

    fun decodePublicKey(publicKeyText: String): PublicKey {
        val bytes = Base64.decode(publicKeyText, Base64.NO_WRAP)
        val spec = X509EncodedKeySpec(bytes)
        return KeyFactory.getInstance(KEY_ALGORITHM).generatePublic(spec)
    }

    private fun decodePrivateKey(privateKeyText: String): PrivateKey {
        val bytes = Base64.decode(privateKeyText, Base64.NO_WRAP)
        val spec = PKCS8EncodedKeySpec(bytes)
        return KeyFactory.getInstance(KEY_ALGORITHM).generatePrivate(spec)
    }

    private fun encodeKey(bytes: ByteArray): String {
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    companion object {
        private const val KEY_ALGORITHM = "EC"
        private const val PRIVATE_KEY = "local_private_key"
        private const val PUBLIC_KEY = "local_public_key"
    }
}
