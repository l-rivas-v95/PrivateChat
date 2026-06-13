package com.lrv.privatechat.crypto

import com.lrv.privatechat.data.entity.ContactEntity
import com.lrv.privatechat.network.payload.ChatPayloads

class ChatCryptoService(
    private val keyPairManager: KeyPairManager
) {

    companion object {
        const val DECRYPTION_ERROR_TEXT = "[No se pudo descifrar]"
    }

    fun buildKeyExchangePayload(from: String, to: String): String {
        return ChatPayloads.keyExchange(
            from = from,
            to = to,
            publicKey = keyPairManager.getPublicKeyText()
        )
    }

    fun getPublicKeyFromPayload(payload: String): String {
        return ChatPayloads.value(payload, "publicKey")
    }

    fun buildOutgoingPayload(
        messageId: String,
        from: String,
        to: String,
        plainText: String,
        contact: ContactEntity?
    ): String {
        val contactPublicKey = contact?.publicKey

        if (contactPublicKey.isNullOrBlank()) {
            return plainPayload(messageId, from, to, plainText)
        }

        return try {
            val localPrivateKey = keyPairManager.getOrCreateKeyPair().private
            val remotePublicKey = keyPairManager.decodePublicKey(contactPublicKey)
            val encrypted = CryptoUtils.encrypt(plainText, localPrivateKey, remotePublicKey)

            ChatPayloads.encryptedMessage(
                messageId = messageId,
                from = from,
                to = to,
                cipherText = encrypted.cipherText,
                iv = encrypted.iv
            )
        } catch (_: Exception) {
            plainPayload(messageId, from, to, plainText)
        }
    }

    fun readPlainTextFromPayload(
        payload: String,
        from: String,
        contacts: List<ContactEntity>
    ): String {
        val text = ChatPayloads.value(payload, "text")
        if (text.isNotBlank()) return text

        val cipherText = ChatPayloads.value(payload, "cipherText")
        val iv = ChatPayloads.value(payload, "iv")
        val contactPublicKey = contacts.firstOrNull { it.username == from }?.publicKey

        if (cipherText.isBlank() || iv.isBlank() || contactPublicKey.isNullOrBlank()) {
            return DECRYPTION_ERROR_TEXT
        }

        return try {
            val localPrivateKey = keyPairManager.getOrCreateKeyPair().private
            val remotePublicKey = keyPairManager.decodePublicKey(contactPublicKey)
            CryptoUtils.decrypt(cipherText, iv, localPrivateKey, remotePublicKey)
        } catch (_: Exception) {
            DECRYPTION_ERROR_TEXT
        }
    }

    private fun plainPayload(
        messageId: String,
        from: String,
        to: String,
        text: String
    ): String {
        return ChatPayloads.plainMessage(messageId, from, to, text)
    }
}
