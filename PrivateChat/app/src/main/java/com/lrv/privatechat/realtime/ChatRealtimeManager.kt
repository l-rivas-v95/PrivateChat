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
                isRead = isRead
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
