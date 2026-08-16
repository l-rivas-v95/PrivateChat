package com.lrv.privatechat.realtime

import android.app.Application
import com.lrv.privatechat.crypto.ChatCryptoService
import com.lrv.privatechat.crypto.KeyPairManager
import com.lrv.privatechat.data.PrivateChatDatabase
import com.lrv.privatechat.data.entity.CONTACT_STATUS_ACCEPTED
import com.lrv.privatechat.data.entity.CONTACT_STATUS_PENDING
import com.lrv.privatechat.data.entity.ChatEntity
import com.lrv.privatechat.data.entity.ContactEntity
import com.lrv.privatechat.data.entity.MessageEntity
import com.lrv.privatechat.model.MESSAGE_STATUS_DELIVERED
import com.lrv.privatechat.model.MESSAGE_STATUS_RECEIVED
import com.lrv.privatechat.model.MESSAGE_STATUS_SENT
import com.lrv.privatechat.model.UNKNOWN_CONTACT_NAME
import com.lrv.privatechat.model.UiMessage
import android.util.Base64
import com.lrv.privatechat.network.ChatFileClient
import com.lrv.privatechat.network.ChatWebSocketClient
import com.lrv.privatechat.network.payload.ChatPayloadTypes
import com.lrv.privatechat.network.payload.ChatPayloads
import com.lrv.privatechat.notifications.ChatNotificationHelper
import com.lrv.privatechat.util.ImageBase64Encoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class ChatRealtimeManager private constructor(private val application: Application) {

    companion object {
        @Volatile
        private var INSTANCE: ChatRealtimeManager? = null

        fun getInstance(application: Application): ChatRealtimeManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ChatRealtimeManager(application).also { INSTANCE = it }
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val database = PrivateChatDatabase.getInstance(application)
    private val preferences = application.getSharedPreferences("private_chat_settings", Application.MODE_PRIVATE)
    private val keyPairManager = KeyPairManager(application)
    private val chatCryptoService = ChatCryptoService(keyPairManager)
    private val notificationHelper = ChatNotificationHelper(application)
    private val mutex = Mutex()

    private val _status = MutableStateFlow("Desconectado")
    val status: StateFlow<String> = _status

    private val _events = MutableSharedFlow<Unit>(extraBufferCapacity = 64)
    val events: SharedFlow<Unit> = _events

    private var currentUserId: String? = null

    private val chatClient = ChatWebSocketClient(
        onMessageReceived = { payload -> handleIncomingPayload(payload) },
        onStatusChanged = { nextStatus -> _status.value = nextStatus }
    )

    fun connect(userId: String) {
        if (currentUserId == userId && chatClient.isConnected()) return
        currentUserId = userId
        preferences.edit().putString("connected_user_id", userId).apply()
        chatClient.connect(userId)
    }

    fun disconnect() {
        currentUserId = null
        chatClient.disconnect()
        _status.value = "Desconectado"
    }

    fun send(payload: String): Boolean {
        return chatClient.send(payload)
    }

    fun isConnected(): Boolean = chatClient.isConnected()

    private fun handleIncomingPayload(received: String) {
        val type = ChatPayloads.value(received, "type")
        val from = ChatPayloads.value(received, "from")

        when (type) {
            ChatPayloadTypes.ACK -> handleAck(received)
            ChatPayloadTypes.CONTACT_INVITE -> handleContactInvite(received, from)
            ChatPayloadTypes.CONTACT_ACCEPT -> handleContactAccept(received, from)
            ChatPayloadTypes.KEY_EXCHANGE -> handleLegacyKeyExchange(received, from)
            ChatPayloadTypes.PROFILE_AVATAR -> handleProfileAvatar(received, from)
            ChatPayloadTypes.MEDIA_MESSAGE -> handleMediaMessage(received, from)
            else -> handleChatMessage(received, from)
        }
    }

    private fun handleAck(received: String) {
        val messageId = ChatPayloads.value(received, "messageId")
        if (messageId.isBlank()) return

        scope.launch {
            database.chatMessageDao().updateDeliveryStatusByMessageId(messageId, MESSAGE_STATUS_DELIVERED)
            notifyDataChanged()
        }
    }

    private fun handleContactInvite(received: String, from: String) {
        val publicKey = ChatPayloads.value(received, "publicKey")
        val displayName = ChatPayloads.value(received, "displayName").ifBlank { UNKNOWN_CONTACT_NAME }
        if (from.isBlank() || publicKey.isBlank()) return

        scope.launch {
            mutex.withLock {
                saveContactInternal(from, displayName, publicKey, CONTACT_STATUS_PENDING)
            }
            notifyDataChanged()
        }
    }

    private fun handleContactAccept(received: String, from: String) {
        val publicKey = ChatPayloads.value(received, "publicKey")
        val displayName = ChatPayloads.value(received, "displayName").ifBlank { UNKNOWN_CONTACT_NAME }
        if (from.isBlank() || publicKey.isBlank()) return

        scope.launch {
            mutex.withLock {
                saveContactInternal(from, displayName, publicKey, CONTACT_STATUS_ACCEPTED)
                sendLocalAvatarToContact(from)
            }
            notifyDataChanged()
        }
    }

    private fun handleLegacyKeyExchange(received: String, from: String) {
        val publicKey = chatCryptoService.getPublicKeyFromPayload(received)
        if (from.isBlank() || publicKey.isBlank()) return

        scope.launch {
            mutex.withLock {
                saveContactInternal(from, UNKNOWN_CONTACT_NAME, publicKey, CONTACT_STATUS_PENDING)
            }
            notifyDataChanged()
        }
    }

    private fun handleProfileAvatar(received: String, from: String) {
        val avatarBase64 = ChatPayloads.value(received, "avatarBase64")
        val updatedAt = ChatPayloads.value(received, "updatedAt").toLongOrNull() ?: System.currentTimeMillis()
        if (from.isBlank() || avatarBase64.isBlank() || avatarBase64.length > ImageBase64Encoder.MAX_AVATAR_BASE64_CHARS) return

        scope.launch {
            mutex.withLock {
                saveContactAvatarInternal(from, avatarBase64, updatedAt)
            }
            notifyDataChanged()
        }
    }

    private fun handleMediaMessage(received: String, from: String) {
        val to = ChatPayloads.value(received, "to")
        val messageId = ChatPayloads.value(received, "id").ifBlank { UUID.randomUUID().toString() }
        val fileId = ChatPayloads.value(received, "fileId")
        val ivBase64 = ChatPayloads.value(received, "iv")
        val mimeType = ChatPayloads.value(received, "mimeType").ifBlank { "application/octet-stream" }
        if (from.isBlank() || fileId.isBlank() || ivBase64.isBlank()) return

        scope.launch {
            mutex.withLock {
                val cipherBytes = ChatFileClient.download(fileId) ?: return@withLock
                val contacts = database.contactDao().getContactsOnce()
                val contactPublicKey = contacts.firstOrNull { it.username == from }?.publicKey ?: return@withLock
                val ivBytes = Base64.decode(ivBase64, Base64.NO_WRAP)
                val plainBytes = chatCryptoService.decryptFileBytes(cipherBytes, ivBytes, contactPublicKey) ?: return@withLock

                val ext = mimeType.substringAfter("/").substringBefore(";").takeIf { it.isNotBlank() } ?: "bin"
                val mediaDir = java.io.File(application.filesDir, "media").also { it.mkdirs() }
                val mediaFile = java.io.File(mediaDir, "$messageId.$ext")
                mediaFile.writeBytes(plainBytes)

                val contact = saveContactInternal(from, UNKNOWN_CONTACT_NAME, null, CONTACT_STATUS_PENDING)
                val uiMessage = UiMessage(
                    id = messageId, from = from, to = to,
                    text = "📎 Archivo adjunto",
                    mine = false, status = MESSAGE_STATUS_RECEIVED,
                    mediaLocalPath = mediaFile.absolutePath, mimeType = mimeType
                )
                saveMessageToDatabaseInternal(uiMessage, from, MESSAGE_STATUS_RECEIVED, isRead = false)
                notificationHelper.showMessageNotification(contact.displayName, "📎 Archivo adjunto")
            }
            notifyDataChanged()
        }
    }

    private fun handleChatMessage(received: String, from: String) {
        val to = ChatPayloads.value(received, "to")
        val messageId = ChatPayloads.value(received, "id").ifBlank { UUID.randomUUID().toString() }
        if (from.isBlank()) return

        scope.launch {
            mutex.withLock {
                val loadedContacts = database.contactDao().getContactsOnce()
                val text = chatCryptoService.readPlainTextFromPayload(received, from, loadedContacts)
                if (text.isBlank() || text == ChatCryptoService.DECRYPTION_ERROR_TEXT) return@withLock

                val contact = saveContactInternal(from, UNKNOWN_CONTACT_NAME, null, CONTACT_STATUS_PENDING)
                val uiMessage = UiMessage(messageId, from, to, text, mine = false, status = MESSAGE_STATUS_RECEIVED)
                saveMessageToDatabaseInternal(uiMessage, from, MESSAGE_STATUS_RECEIVED, isRead = false)
                notificationHelper.showMessageNotification(contact.displayName, text)
            }
            notifyDataChanged()
        }
    }

    fun sendMediaFile(to: String, fileBytes: ByteArray, mimeType: String) {
        val from = currentUserId ?: return
        scope.launch {
            val contact = database.contactDao().findByUsername(to) ?: return@launch
            val contactPublicKey = contact.publicKey ?: return@launch
            val encrypted = chatCryptoService.encryptFileBytes(fileBytes, contactPublicKey) ?: return@launch
            val fileId = ChatFileClient.upload(to, encrypted.cipherBytes) ?: return@launch
            val messageId = UUID.randomUUID().toString()
            val ivBase64 = Base64.encodeToString(encrypted.iv, Base64.NO_WRAP)
            val payload = ChatPayloads.mediaMessage(messageId, from, to, fileId, ivBase64, mimeType)
            chatClient.send(payload)

            // Guardar copia local para mostrar miniatura en el chat
            val ext = mimeType.substringAfter("/").substringBefore(";").takeIf { it.isNotBlank() } ?: "bin"
            val mediaDir = java.io.File(application.filesDir, "media").also { it.mkdirs() }
            val mediaFile = java.io.File(mediaDir, "$messageId.$ext")
            mediaFile.writeBytes(fileBytes)

            val uiMessage = UiMessage(
                id = messageId, from = from, to = to,
                text = "📎 Archivo adjunto",
                mine = true, status = MESSAGE_STATUS_SENT,
                mimeType = mimeType,
                mediaLocalPath = mediaFile.absolutePath
            )
            saveMessageToDatabaseInternal(uiMessage, to, MESSAGE_STATUS_SENT, isRead = true)
            notifyDataChanged()
        }
    }

    fun sendContactInvite(to: String, displayName: String, publicKey: String) {
        val from = currentUserId ?: return
        chatClient.send(ChatPayloads.contactInvite(from, to, displayName, publicKey))
    }

    fun sendContactAccept(to: String, displayName: String, publicKey: String) {
        val from = currentUserId ?: return
        chatClient.send(ChatPayloads.contactAccept(from, to, displayName, publicKey))
    }

    fun sendLocalAvatarToContact(contactUsername: String) {
        val from = currentUserId ?: return
        val avatarBase64 = preferences.getString("local_avatar_base64", null) ?: return
        if (avatarBase64.length <= ImageBase64Encoder.MAX_AVATAR_BASE64_CHARS) {
            chatClient.send(ChatPayloads.profileAvatar(from, contactUsername, avatarBase64, System.currentTimeMillis()))
        }
    }

    fun sendLocalAvatarToAcceptedContacts(avatarBase64: String) {
        if (avatarBase64.length > ImageBase64Encoder.MAX_AVATAR_BASE64_CHARS) return
        val from = currentUserId ?: return

        scope.launch {
            database.contactDao().getContactsOnce()
                .filter { it.username != from }
                .filter { it.status == CONTACT_STATUS_ACCEPTED }
                .distinctBy { it.username }
                .forEach { contact ->
                    chatClient.send(ChatPayloads.profileAvatar(from, contact.username, avatarBase64, System.currentTimeMillis()))
                }
        }
    }

    private suspend fun saveContactInternal(contactId: String, contactName: String, publicKey: String?, status: String): ContactEntity {
        val now = System.currentTimeMillis()
        val existingContact = database.contactDao().findByUsername(contactId)
        val nextDisplayName = when {
            existingContact == null -> contactName
            existingContact.displayName == UNKNOWN_CONTACT_NAME && contactName == UNKNOWN_CONTACT_NAME -> existingContact.displayName
            contactName == UNKNOWN_CONTACT_NAME -> existingContact.displayName
            else -> contactName
        }
        val nextStatus = when {
            existingContact?.status == CONTACT_STATUS_ACCEPTED -> CONTACT_STATUS_ACCEPTED
            status == CONTACT_STATUS_ACCEPTED -> CONTACT_STATUS_ACCEPTED
            else -> CONTACT_STATUS_PENDING
        }

        val contact = ContactEntity(
            id = existingContact?.id ?: 0,
            username = contactId,
            displayName = nextDisplayName,
            publicKey = publicKey ?: existingContact?.publicKey,
            avatarBase64 = existingContact?.avatarBase64,
            avatarUpdatedAt = existingContact?.avatarUpdatedAt,
            createdAt = existingContact?.createdAt ?: now,
            lastSeenAt = now,
            status = nextStatus
        )

        database.contactDao().save(contact)
        ensureChat(contactId)
        return database.contactDao().findByUsername(contactId) ?: contact
    }

    private suspend fun saveContactAvatarInternal(contactId: String, avatarBase64: String, updatedAt: Long): ContactEntity {
        val now = System.currentTimeMillis()
        val existingContact = database.contactDao().findByUsername(contactId)
        val contact = ContactEntity(
            id = existingContact?.id ?: 0,
            username = contactId,
            displayName = existingContact?.displayName ?: UNKNOWN_CONTACT_NAME,
            publicKey = existingContact?.publicKey,
            avatarBase64 = avatarBase64,
            avatarUpdatedAt = updatedAt,
            createdAt = existingContact?.createdAt ?: now,
            lastSeenAt = now,
            status = existingContact?.status ?: CONTACT_STATUS_PENDING
        )

        database.contactDao().save(contact)
        ensureChat(contactId)
        return database.contactDao().findByUsername(contactId) ?: contact
    }

    private suspend fun saveMessageToDatabaseInternal(message: UiMessage, contactUsername: String, status: String, isRead: Boolean) {
        val chat = ensureChat(contactUsername)
        database.chatMessageDao().saveChatMessage(
            MessageEntity(
                messageId = message.id,
                chatId = chat.id,
                senderUsername = message.from,
                receiverUsername = message.to,
                body = message.text,
                timestamp = message.timestamp,
                isMine = message.mine,
                deliveryStatus = status,
                isRead = isRead,
                mediaLocalPath = message.mediaLocalPath,
                mimeType = message.mimeType
            )
        )
        database.chatDao().save(chat.copy(updatedAt = message.timestamp, lastMessagePreview = message.text))
    }

    private suspend fun ensureChat(contactUsername: String): ChatEntity {
        val existingChat = database.chatDao().findByContact(contactUsername)
        if (existingChat != null) return existingChat

        val now = System.currentTimeMillis()
        val chatId = database.chatDao().save(ChatEntity(contactUsername = contactUsername, createdAt = now, updatedAt = now, lastMessagePreview = "Sin mensajes todavía"))
        return ChatEntity(id = chatId, contactUsername = contactUsername, createdAt = now, updatedAt = now, lastMessagePreview = "Sin mensajes todavía")
    }

    private fun notifyDataChanged() {
        _events.tryEmit(Unit)
    }
}
