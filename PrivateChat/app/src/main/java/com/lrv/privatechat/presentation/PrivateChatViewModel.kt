package com.lrv.privatechat.presentation

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lrv.privatechat.crypto.ChatCryptoService
import com.lrv.privatechat.crypto.KeyPairManager
import com.lrv.privatechat.data.PrivateChatDatabase
import com.lrv.privatechat.data.entity.CONTACT_STATUS_ACCEPTED
import com.lrv.privatechat.data.entity.CONTACT_STATUS_PENDING
import com.lrv.privatechat.data.entity.ChatEntity
import com.lrv.privatechat.data.entity.ContactEntity
import com.lrv.privatechat.data.entity.MessageEntity
import com.lrv.privatechat.model.AppColor
import com.lrv.privatechat.model.MESSAGE_STATUS_DELIVERED
import com.lrv.privatechat.model.MESSAGE_STATUS_RECEIVED
import com.lrv.privatechat.model.MESSAGE_STATUS_SENT
import com.lrv.privatechat.model.UNKNOWN_CONTACT_NAME
import com.lrv.privatechat.model.UiMessage
import com.lrv.privatechat.network.ChatWebSocketClient
import com.lrv.privatechat.network.payload.ChatPayloadTypes
import com.lrv.privatechat.network.payload.ChatPayloads
import com.lrv.privatechat.util.ImageBase64Encoder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class PrivateChatViewModel(application: Application) : AndroidViewModel(application) {

    private val database = PrivateChatDatabase.getInstance(application)
    private val preferences = application.getSharedPreferences("private_chat_settings", Application.MODE_PRIVATE)
    private val keyPairManager = KeyPairManager(application)
    private val chatCryptoService: ChatCryptoService
    private val imageBase64Encoder = ImageBase64Encoder(application)

    private val chatClient = ChatWebSocketClient(
        onMessageReceived = ::handleIncomingPayload,
        onStatusChanged = { newStatus -> _uiState.update { it.copy(status = newStatus) } }
    )

    private val initialUserId: String = preferences.getString("local_user_id", null)
        ?: UUID.randomUUID().toString().also {
            preferences.edit().putString("local_user_id", it).apply()
        }

    private val _uiState = MutableStateFlow(
        PrivateChatUiState(
            localUserId = initialUserId,
            connectedUserId = initialUserId,
            displayName = preferences.getString("display_name", "User") ?: "User",
            localPublicKey = "",
            localAvatarBase64 = preferences.getString("local_avatar_base64", null),
            selectedColor = runCatching {
                AppColor.valueOf(preferences.getString("color", AppColor.GREEN.name) ?: AppColor.GREEN.name)
            }.getOrDefault(AppColor.GREEN)
        )
    )

    val uiState: StateFlow<PrivateChatUiState> = _uiState

    init {
        keyPairManager.getOrCreateKeyPair()
        chatCryptoService = ChatCryptoService(keyPairManager)
        _uiState.update { it.copy(localPublicKey = keyPairManager.getPublicKeyText()) }
        reloadLocalState()
        connect()
    }

    fun connect() {
        val localUserId = _uiState.value.localUserId
        preferences.edit().putString("connected_user_id", localUserId).apply()
        _uiState.update { it.copy(connectedUserId = localUserId) }
        chatClient.connect(localUserId)
    }

    fun disconnect() {
        chatClient.disconnect()
        _uiState.update { it.copy(status = "Desconectado") }
    }

    fun toggleConnection() { if (_uiState.value.isConnected) disconnect() else connect() }
    fun showNewChatDialog() { _uiState.update { it.copy(showNewChatDialog = true) } }
    fun hideNewChatDialog() { _uiState.update { it.copy(showNewChatDialog = false) } }
    fun startQrScan() { _uiState.update { it.copy(scannedQrContent = null, showNewChatDialog = false) } }
    fun onQrScanned(qrContent: String) { _uiState.update { it.copy(scannedQrContent = qrContent, showNewChatDialog = true) } }
    fun reopenNewChatDialog() { _uiState.update { it.copy(showNewChatDialog = true) } }

    fun saveManualContact(contactId: String, contactName: String, publicKey: String?) {
        viewModelScope.launch {
            saveContactInternal(contactId, contactName, publicKey, CONTACT_STATUS_ACCEPTED)
            updateContacts(database.contactDao().getContactsOnce())
            sendContactInvite(contactId)
            sendLocalAvatarToContact(contactId)
            _uiState.update { it.copy(scannedQrContent = null, showNewChatDialog = false) }
        }
    }

    fun saveContact(contactId: String, contactName: String, publicKey: String?) {
        viewModelScope.launch {
            saveContactInternal(contactId, contactName, publicKey, CONTACT_STATUS_ACCEPTED)
            updateContacts(database.contactDao().getContactsOnce())
            sendContactInvite(contactId)
            sendLocalAvatarToContact(contactId)
        }
    }

    fun acceptPendingContact(contactUsername: String) {
        viewModelScope.launch {
            val existing = database.contactDao().findByUsername(contactUsername) ?: return@launch
            saveContactInternal(existing.username, existing.displayName, existing.publicKey, CONTACT_STATUS_ACCEPTED)
            updateContacts(database.contactDao().getContactsOnce())
            sendContactAccept(contactUsername)
            sendLocalAvatarToContact(contactUsername)
        }
    }

    fun deleteConversation(contactUsername: String) {
        viewModelScope.launch {
            val chat = database.chatDao().findByContact(contactUsername)
            if (chat != null) {
                database.chatMessageDao().deleteMessagesByChatId(chat.id)
                database.chatDao().deleteChatByContact(contactUsername)
            }
            _uiState.update { state ->
                state.copy(
                    contacts = state.contacts.filterNot { it.username == contactUsername },
                    messages = state.messages.filterNot {
                        (it.from == state.connectedUserId && it.to == contactUsername) ||
                            (it.from == contactUsername && it.to == state.connectedUserId)
                    }
                )
            }
        }
    }

    fun clearChatMessages(contactUsername: String) {
        viewModelScope.launch {
            val chat = database.chatDao().findByContact(contactUsername) ?: return@launch
            database.chatMessageDao().deleteMessagesByChatId(chat.id)
            database.chatDao().save(chat.copy(updatedAt = System.currentTimeMillis(), lastMessagePreview = "Sin mensajes todavía"))
            _uiState.update { state ->
                state.copy(messages = state.messages.filterNot {
                    (it.from == state.connectedUserId && it.to == contactUsername) ||
                        (it.from == contactUsername && it.to == state.connectedUserId)
                })
            }
        }
    }

    fun deleteMessage(message: UiMessage) {
        viewModelScope.launch {
            database.chatMessageDao().deleteMessageByMessageId(message.id)
            _uiState.update { state -> state.copy(messages = state.messages.filterNot { it.id == message.id }) }
        }
    }

    fun sendMessage(contactUsername: String, text: String) {
        val state = _uiState.value
        val storedContact = state.contacts.firstOrNull { it.username == contactUsername }

        if (storedContact?.publicKey.isNullOrBlank()) {
            sendContactInvite(contactUsername)
            addSystemMessage(contactUsername, "Invitación enviada. Espera a que el contacto responda antes de enviar mensajes cifrados.")
            reloadLocalState()
            return
        }

        val messageId = UUID.randomUUID().toString()
        val payload = chatCryptoService.buildOutgoingPayload(messageId, state.connectedUserId, contactUsername, text, storedContact)
        chatClient.send(payload)

        val uiMessage = UiMessage(messageId, state.connectedUserId, contactUsername, text, mine = true, status = MESSAGE_STATUS_SENT)
        _uiState.update { it.copy(messages = it.messages + uiMessage) }

        viewModelScope.launch {
            saveContactInternal(contactUsername, storedContact.displayName, storedContact.publicKey, CONTACT_STATUS_ACCEPTED)
            saveMessageToDatabaseInternal(uiMessage, contactUsername, MESSAGE_STATUS_SENT)
            updateContacts(database.contactDao().getContactsOnce())
            reloadMessages()
        }
    }

    fun updateDisplayName(newName: String) {
        preferences.edit().putString("display_name", newName).apply()
        _uiState.update { it.copy(displayName = newName) }
    }

    fun updateColor(color: AppColor) {
        preferences.edit().putString("color", color.name).apply()
        _uiState.update { it.copy(selectedColor = color) }
    }

    fun updateAvatar(uri: Uri) {
        imageBase64Encoder.encodeAvatarToBase64(uri)?.let { encoded ->
            preferences.edit().putString("local_avatar_base64", encoded).apply()
            _uiState.update { it.copy(localAvatarBase64 = encoded) }
            sendLocalAvatarToAcceptedContacts(encoded)
        }
    }

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

        _uiState.update { state ->
            state.copy(messages = state.messages.map { message -> if (message.id == messageId) message.copy(status = MESSAGE_STATUS_DELIVERED) else message })
        }
        viewModelScope.launch {
            database.chatMessageDao().updateDeliveryStatusByMessageId(messageId, MESSAGE_STATUS_DELIVERED)
            reloadMessages()
        }
    }

    private fun handleContactInvite(received: String, from: String) {
        val publicKey = ChatPayloads.value(received, "publicKey")
        val displayName = ChatPayloads.value(received, "displayName").ifBlank { UNKNOWN_CONTACT_NAME }
        if (from.isBlank() || publicKey.isBlank()) return

        viewModelScope.launch {
            saveContactInternal(from, displayName, publicKey, CONTACT_STATUS_PENDING)
            updateContacts(database.contactDao().getContactsOnce())
        }
    }

    private fun handleContactAccept(received: String, from: String) {
        val publicKey = ChatPayloads.value(received, "publicKey")
        val displayName = ChatPayloads.value(received, "displayName").ifBlank { UNKNOWN_CONTACT_NAME }
        if (from.isBlank() || publicKey.isBlank()) return

        viewModelScope.launch {
            saveContactInternal(from, displayName, publicKey, CONTACT_STATUS_ACCEPTED)
            updateContacts(database.contactDao().getContactsOnce())
            sendLocalAvatarToContact(from)
        }
    }

    private fun handleLegacyKeyExchange(received: String, from: String) {
        val publicKey = chatCryptoService.getPublicKeyFromPayload(received)
        if (from.isBlank() || publicKey.isBlank()) return

        viewModelScope.launch {
            saveContactInternal(from, UNKNOWN_CONTACT_NAME, publicKey, CONTACT_STATUS_PENDING)
            updateContacts(database.contactDao().getContactsOnce())
        }
    }

    private fun handleProfileAvatar(received: String, from: String) {
        val avatarBase64 = ChatPayloads.value(received, "avatarBase64")
        val updatedAt = ChatPayloads.value(received, "updatedAt").toLongOrNull() ?: System.currentTimeMillis()
        if (from.isBlank() || avatarBase64.isBlank() || avatarBase64.length > ImageBase64Encoder.MAX_AVATAR_BASE64_CHARS) return

        viewModelScope.launch {
            saveContactAvatarInternal(from, avatarBase64, updatedAt)
            updateContacts(database.contactDao().getContactsOnce())
        }
    }

    private fun handleChatMessage(received: String, from: String) {
        val state = _uiState.value
        val to = ChatPayloads.value(received, "to")
        val text = chatCryptoService.readPlainTextFromPayload(received, from, state.contacts)
        val messageId = ChatPayloads.value(received, "id").ifBlank { UUID.randomUUID().toString() }

        if (from.isBlank() || text.isBlank()) return

        val uiMessage = UiMessage(messageId, from, to, text, mine = false, status = MESSAGE_STATUS_RECEIVED)
        _uiState.update { it.copy(messages = it.messages + uiMessage) }

        viewModelScope.launch {
            saveContactInternal(from, UNKNOWN_CONTACT_NAME, null, CONTACT_STATUS_PENDING)
            saveMessageToDatabaseInternal(uiMessage, from, MESSAGE_STATUS_RECEIVED)
            updateContacts(database.contactDao().getContactsOnce())
            reloadMessages()
        }
    }

    private fun addSystemMessage(contact: String, text: String) {
        val state = _uiState.value
        _uiState.update { it.copy(messages = it.messages + UiMessage(from = state.connectedUserId, to = contact, text = text, mine = true, status = MESSAGE_STATUS_SENT)) }
    }

    private fun reloadLocalState() {
        viewModelScope.launch {
            updateContacts(database.contactDao().getContactsOnce())
            reloadMessages()
        }
    }

    private suspend fun reloadMessages() { _uiState.update { it.copy(messages = loadMessagesFromDatabaseInternal()) } }
    private fun updateContacts(loaded: List<ContactEntity>) { _uiState.update { it.copy(contacts = normalizeContacts(loaded)) } }

    private fun normalizeContacts(loaded: List<ContactEntity>): List<ContactEntity> {
        return loaded
            .groupBy { it.username }
            .map { (_, items) ->
                items.maxWith(
                    compareBy<ContactEntity> { it.status == CONTACT_STATUS_ACCEPTED }
                        .thenBy { it.displayName != UNKNOWN_CONTACT_NAME && it.displayName != it.username }
                        .thenBy { it.publicKey != null }
                        .thenBy { it.avatarBase64 != null }
                        .thenBy { it.lastSeenAt ?: 0L }
                        .thenBy { it.createdAt }
                )
            }
            .sortedByDescending { it.lastSeenAt ?: it.createdAt }
    }

    private fun sendContactInvite(to: String) {
        val state = _uiState.value
        chatClient.send(ChatPayloads.contactInvite(state.connectedUserId, to, state.displayName, state.localPublicKey))
    }

    private fun sendContactAccept(to: String) {
        val state = _uiState.value
        chatClient.send(ChatPayloads.contactAccept(state.connectedUserId, to, state.displayName, state.localPublicKey))
    }

    private fun sendLocalAvatarToContact(contactUsername: String) {
        _uiState.value.localAvatarBase64?.let { avatarBase64 ->
            if (avatarBase64.length <= ImageBase64Encoder.MAX_AVATAR_BASE64_CHARS) {
                chatClient.send(ChatPayloads.profileAvatar(_uiState.value.connectedUserId, contactUsername, avatarBase64, System.currentTimeMillis()))
            }
        }
    }

    private fun sendLocalAvatarToAcceptedContacts(avatarBase64: String) {
        if (avatarBase64.length > ImageBase64Encoder.MAX_AVATAR_BASE64_CHARS) return

        _uiState.value.contacts
            .filter { it.username != _uiState.value.connectedUserId }
            .filter { it.status == CONTACT_STATUS_ACCEPTED }
            .distinctBy { it.username }
            .forEach { contact ->
                chatClient.send(ChatPayloads.profileAvatar(_uiState.value.connectedUserId, contact.username, avatarBase64, System.currentTimeMillis()))
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

    private suspend fun loadMessagesFromDatabaseInternal(): List<UiMessage> {
        val chats = database.chatDao().getAllChatsOnce()
        val loadedMessages = mutableListOf<UiMessage>()

        chats.forEach { chat ->
            val chatMessages = database.chatMessageDao().getChatMessagesOnce(chat.id)
                .map { entity -> UiMessage(entity.messageId, entity.senderUsername, entity.receiverUsername, entity.body, entity.isMine, entity.timestamp, entity.deliveryStatus) }
            loadedMessages.addAll(chatMessages)
        }

        return loadedMessages.sortedBy { it.timestamp }
    }

    private suspend fun saveMessageToDatabaseInternal(message: UiMessage, contactUsername: String, status: String) {
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
                deliveryStatus = status
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

    override fun onCleared() {
        super.onCleared()
        chatClient.disconnect()
    }
}
