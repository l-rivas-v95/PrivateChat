package com.lrv.privatechat.network.payload

import org.json.JSONObject

object ChatPayloadTypes {
    const val MESSAGE = "message"
    const val ACK = "ack"
    const val KEY_EXCHANGE = "key_exchange"
    const val CONTACT_INVITE = "contact_invite"
    const val CONTACT_ACCEPT = "contact_accept"
    const val PROFILE_AVATAR = "profile_avatar"
    const val PRIVATECHAT_CONTACT = "privatechat_contact"
}

data class ContactQrPayload(
    val userId: String,
    val displayName: String,
    val publicKey: String
)

object ChatPayloads {

    fun contactInvite(from: String, to: String, displayName: String, publicKey: String): String {
        return JSONObject()
            .put("type", ChatPayloadTypes.CONTACT_INVITE)
            .put("from", from)
            .put("to", to)
            .put("displayName", displayName)
            .put("publicKey", publicKey)
            .toString()
    }

    fun contactAccept(from: String, to: String, displayName: String, publicKey: String): String {
        return JSONObject()
            .put("type", ChatPayloadTypes.CONTACT_ACCEPT)
            .put("from", from)
            .put("to", to)
            .put("displayName", displayName)
            .put("publicKey", publicKey)
            .toString()
    }

    fun keyExchange(from: String, to: String, publicKey: String): String {
        return JSONObject()
            .put("type", ChatPayloadTypes.KEY_EXCHANGE)
            .put("from", from)
            .put("to", to)
            .put("publicKey", publicKey)
            .toString()
    }

    fun encryptedMessage(messageId: String, from: String, to: String, cipherText: String, iv: String): String {
        return JSONObject()
            .put("type", ChatPayloadTypes.MESSAGE)
            .put("id", messageId)
            .put("from", from)
            .put("to", to)
            .put("cipherText", cipherText)
            .put("iv", iv)
            .toString()
    }

    fun plainMessage(messageId: String, from: String, to: String, text: String): String {
        return JSONObject()
            .put("type", ChatPayloadTypes.MESSAGE)
            .put("id", messageId)
            .put("from", from)
            .put("to", to)
            .put("text", text)
            .toString()
    }

    fun profileAvatar(from: String, to: String, avatarBase64: String, updatedAt: Long): String {
        return JSONObject()
            .put("type", ChatPayloadTypes.PROFILE_AVATAR)
            .put("from", from)
            .put("to", to)
            .put("avatarBase64", avatarBase64)
            .put("updatedAt", updatedAt.toString())
            .toString()
    }

    fun contactQr(userId: String, displayName: String, publicKey: String): String {
        return JSONObject()
            .put("type", ChatPayloadTypes.PRIVATECHAT_CONTACT)
            .put("userId", userId)
            .put("displayName", displayName)
            .put("publicKey", publicKey)
            .toString()
    }

    fun parseContactQr(content: String): ContactQrPayload? {
        val json = parse(content) ?: return null
        if (json.optString("type") != ChatPayloadTypes.PRIVATECHAT_CONTACT) return null

        val userId = json.optString("userId").trim()
        if (userId.isBlank()) return null

        return ContactQrPayload(
            userId = userId,
            displayName = json.optString("displayName").ifBlank { userId },
            publicKey = json.optString("publicKey")
        )
    }

    fun value(payload: String, key: String): String {
        return parse(payload)?.optString(key).orEmpty()
    }

    private fun parse(payload: String): JSONObject? {
        return runCatching { JSONObject(payload) }.getOrNull()
    }
}
