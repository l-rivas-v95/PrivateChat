package com.lrv.privatechat.crypto

import com.lrv.privatechat.data.entity.ContactEntity

class ChatCryptoService(
    private val keyPairManager: KeyPairManager
) {

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

            "{\"id\":\"$messageId\",\"from\":\"$from\",\"to\":\"$to\",\"cipherText\":\"${encrypted.cipherText}\",\"iv\":\"${encrypted.iv}\"}"
        } catch (_: Exception) {
            plainPayload(messageId, from, to, plainText)
        }
    }

    fun readPlainTextFromPayload(
        payload: String,
        from: String,
        contacts: List<ContactEntity>
    ): String {
        val text = extractValue(payload, "text")
        if (text.isNotBlank()) {
            return text
        }

        val cipherText = extractValue(payload, "cipherText")
        val iv = extractValue(payload, "iv")
        val contactPublicKey = contacts.firstOrNull { it.username == from }?.publicKey

        if (cipherText.isBlank() || iv.isBlank() || contactPublicKey.isNullOrBlank()) {
            return "[No se pudo descifrar]"
        }

        return try {
            val localPrivateKey = keyPairManager.getOrCreateKeyPair().private
            val remotePublicKey = keyPairManager.decodePublicKey(contactPublicKey)
            CryptoUtils.decrypt(cipherText, iv, localPrivateKey, remotePublicKey)
        } catch (_: Exception) {
            "[No se pudo descifrar]"
        }
    }

    private fun plainPayload(
        messageId: String,
        from: String,
        to: String,
        text: String
    ): String {
        return "{\"id\":\"$messageId\",\"from\":\"$from\",\"to\":\"$to\",\"text\":\"${escapeJson(text)}\"}"
    }

    private fun extractValue(json: String, key: String): String {
        val search = "\"$key\":\""
        val start = json.indexOf(search)

        if (start == -1) return ""

        val valueStart = start + search.length
        val end = json.indexOf("\"", valueStart)

        if (end == -1) return ""

        return json.substring(valueStart, end)
    }

    private fun escapeJson(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
    }
}
