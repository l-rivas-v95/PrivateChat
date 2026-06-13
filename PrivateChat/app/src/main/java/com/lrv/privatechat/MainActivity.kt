package com.lrv.privatechat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lrv.privatechat.crypto.ChatCryptoService
import com.lrv.privatechat.crypto.KeyPairManager
import com.lrv.privatechat.data.PrivateChatDatabase
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
import com.lrv.privatechat.ui.dialogs.NewChatDialog
import com.lrv.privatechat.ui.screens.ChatDetailScreen
import com.lrv.privatechat.ui.screens.ChatsScreen
import com.lrv.privatechat.ui.screens.ProfileSettingsScreen
import com.lrv.privatechat.ui.theme.PrivateChatTheme
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.UUID

class MainActivity : ComponentActivity() {

    private lateinit var chatClient: ChatWebSocketClient
    private lateinit var database: PrivateChatDatabase
    private lateinit var keyPairManager: KeyPairManager
    private lateinit var chatCryptoService: ChatCryptoService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = PrivateChatDatabase.getInstance(this)
        keyPairManager = KeyPairManager(this)
        keyPairManager.getOrCreateKeyPair()
        chatCryptoService = ChatCryptoService(keyPairManager)

        setContent {
            PrivateChatTheme {
                PrivateChatApp()
            }
        }
    }

    @Composable
    private fun PrivateChatApp() {
        val navController = rememberNavController()
        val preferences = remember { getSharedPreferences("private_chat_settings", Context.MODE_PRIVATE) }
        val localPublicKey = remember { keyPairManager.getPublicKeyText() }
        val initialUserId = remember {
            preferences.getString("local_user_id", null) ?: UUID.randomUUID().toString().also {
                preferences.edit().putString("local_user_id", it).apply()
            }
        }

        var localUserId by remember { mutableStateOf(initialUserId) }
        var connectedUserId by remember { mutableStateOf(localUserId) }
        var displayName by remember { mutableStateOf(preferences.getString("display_name", "User") ?: "User") }
        var localAvatarBase64 by remember { mutableStateOf(preferences.getString("local_avatar_base64", null)) }
        var selectedColor by remember {
            mutableStateOf(
                AppColor.valueOf(preferences.getString("color", AppColor.GREEN.name) ?: AppColor.GREEN.name)
            )
        }
        var status by remember { mutableStateOf("Desconectado") }
        var messages by remember { mutableStateOf(listOf<UiMessage>()) }
        var contacts by remember { mutableStateOf(listOf<ContactEntity>()) }
        var showNewChatDialog by remember { mutableStateOf(false) }
        var scannedQrContent by remember { mutableStateOf<String?>(null) }

        fun normalizeContacts(loaded: List<ContactEntity>): List<ContactEntity> {
            return loaded
                .groupBy { it.username }
                .map { (_, items) ->
                    items.maxWith(
                        compareBy<ContactEntity> { it.displayName != UNKNOWN_CONTACT_NAME && it.displayName != it.username }
                            .thenBy { it.publicKey != null }
                            .thenBy { it.avatarBase64 != null }
                            .thenBy { it.lastSeenAt ?: 0L }
                            .thenBy { it.createdAt }
                    )
                }
                .sortedByDescending { it.lastSeenAt ?: it.createdAt }
        }

        fun updateContacts(loaded: List<ContactEntity>) {
            contacts = normalizeContacts(loaded)
        }

        fun reloadMessages() {
            loadMessagesFromDatabase { loaded -> messages = loaded }
        }

        fun reloadContacts() {
            loadContactsFromDatabase { loaded -> updateContacts(loaded) }
        }

        fun sendKeyExchange(to: String) {
            chatClient.send(chatCryptoService.buildKeyExchangePayload(connectedUserId, to))
        }

        fun sendAvatarToContact(contactUsername: String, avatarBase64: String) {
            chatClient.send(buildProfileAvatarPayload(connectedUserId, contactUsername, avatarBase64))
        }

        fun sendAvatarToContacts(avatarBase64: String) {
            contacts
                .filter { it.username != connectedUserId }
                .distinctBy { it.username }
                .forEach { contact -> sendAvatarToContact(contact.username, avatarBase64) }
        }

        fun connect() {
            connectedUserId = localUserId
            preferences.edit().putString("connected_user_id", localUserId).apply()
            chatClient.connect(localUserId)
            localAvatarBase64?.let { sendAvatarToContacts(it) }
        }

        fun disconnect() {
            chatClient.disconnect()
            status = "Desconectado"
        }

        fun addSystemMessage(contact: String, text: String) {
            messages = messages + UiMessage(
                from = connectedUserId,
                to = contact,
                text = text,
                mine = true,
                status = MESSAGE_STATUS_SENT
            )
        }

        fun refreshLocalState() {
            lifecycleScope.launch {
                updateContacts(database.contactDao().getContactsOnce())
                messages = loadMessagesFromDatabaseInternal()
            }
        }

        val avatarPicker = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri ->
            if (uri != null) {
                encodeAvatarToBase64(uri)?.let { encoded ->
                    localAvatarBase64 = encoded
                    preferences.edit().putString("local_avatar_base64", encoded).apply()
                    if (status == "Conectado") sendAvatarToContacts(encoded)
                }
            }
        }

        LaunchedEffect(Unit) {
            reloadContacts()
            reloadMessages()

            chatClient = ChatWebSocketClient(
                onMessageReceived = { received ->
                    val type = extractValue(received, "type")
                    val from = extractValue(received, "from")

                    when (type) {
                        "ack" -> {
                            val messageId = extractValue(received, "messageId")
                            if (messageId.isNotBlank()) {
                                messages = messages.map { message ->
                                    if (message.id == messageId) message.copy(status = MESSAGE_STATUS_DELIVERED) else message
                                }
                                updateMessageStatus(messageId, MESSAGE_STATUS_DELIVERED) { reloadMessages() }
                            }
                            return@ChatWebSocketClient
                        }

                        "key_exchange" -> {
                            val publicKey = chatCryptoService.getPublicKeyFromPayload(received)
                            if (from.isNotBlank() && publicKey.isNotBlank()) {
                                lifecycleScope.launch {
                                    saveContactInternal(from, UNKNOWN_CONTACT_NAME, publicKey)
                                    updateContacts(database.contactDao().getContactsOnce())
                                    sendKeyExchange(from)
                                    localAvatarBase64?.let { sendAvatarToContact(from, it) }
                                }
                            }
                            return@ChatWebSocketClient
                        }

                        "profile_avatar" -> {
                            val avatarBase64 = extractValue(received, "avatarBase64")
                            val updatedAt = extractValue(received, "updatedAt").toLongOrNull() ?: System.currentTimeMillis()
                            if (from.isNotBlank() && avatarBase64.isNotBlank()) {
                                lifecycleScope.launch {
                                    saveContactAvatarInternal(from, avatarBase64, updatedAt)
                                    updateContacts(database.contactDao().getContactsOnce())
                                }
                            }
                            return@ChatWebSocketClient
                        }
                    }

                    val to = extractValue(received, "to")
                    val text = chatCryptoService.readPlainTextFromPayload(received, from, contacts)
                    val messageId = extractValue(received, "id").ifBlank { UUID.randomUUID().toString() }

                    if (from.isNotBlank() && text.isNotBlank()) {
                        val uiMessage = UiMessage(
                            id = messageId,
                            from = from,
                            to = to,
                            text = text,
                            mine = false,
                            status = MESSAGE_STATUS_RECEIVED
                        )

                        messages = messages + uiMessage

                        lifecycleScope.launch {
                            saveContactInternal(from, UNKNOWN_CONTACT_NAME, null)
                            saveMessageToDatabaseInternal(uiMessage, from, MESSAGE_STATUS_RECEIVED)
                            updateContacts(database.contactDao().getContactsOnce())
                            messages = loadMessagesFromDatabaseInternal()
                        }
                    }
                },
                onStatusChanged = { newStatus -> status = newStatus }
            )

            connect()
        }

        if (showNewChatDialog) {
            NewChatDialog(
                appColor = selectedColor,
                scannedQrContent = scannedQrContent,
                onDismiss = { showNewChatDialog = false },
                onScanQr = {
                    scannedQrContent = null
                    // TODO: abrir pantalla de escaneo QR con cámara.
                },
                onSaveManual = { contactId, contactName, publicKey ->
                    saveContact(contactId, contactName, publicKey) { loaded -> updateContacts(loaded) }
                    sendKeyExchange(contactId)
                    localAvatarBase64?.let { sendAvatarToContact(contactId, it) }
                    scannedQrContent = null
                    showNewChatDialog = false
                }
            )
        }

        val visibleContacts = contacts
            .filter { it.username != connectedUserId }
            .distinctBy { it.username }
        val isConnected = status == "Conectado"

        NavHost(navController = navController, startDestination = "chats") {
            composable("chats") {
                ChatsScreen(
                    userId = connectedUserId,
                    displayName = displayName,
                    status = status,
                    isConnected = isConnected,
                    appColor = selectedColor,
                    contacts = visibleContacts,
                    messages = messages,
                    onToggleConnection = { if (isConnected) disconnect() else connect() },
                    onNewChat = { showNewChatDialog = true },
                    onOpenChat = { contact -> navController.navigate("chat/$contact") },
                    onDeleteConversation = { contact ->
                        deleteConversation(contact) {
                            contacts = contacts.filterNot { it.username == contact }
                            messages = messages.filterNot {
                                (it.from == connectedUserId && it.to == contact) ||
                                        (it.from == contact && it.to == connectedUserId)
                            }
                        }
                    },
                    onOpenProfile = { navController.navigate("profile") }
                )
            }

            composable(
                route = "chat/{contact}",
                arguments = listOf(navArgument("contact") { type = NavType.StringType })
            ) { backStackEntry ->
                val contact = backStackEntry.arguments?.getString("contact") ?: ""
                val storedContact = contacts.firstOrNull { it.username == contact }
                val rawContactName = storedContact?.displayName ?: UNKNOWN_CONTACT_NAME
                val isUnknownContact = rawContactName == UNKNOWN_CONTACT_NAME || rawContactName == contact
                val contactName = if (isUnknownContact) UNKNOWN_CONTACT_NAME else rawContactName

                ChatDetailScreen(
                    username = connectedUserId,
                    contact = contact,
                    contactName = contactName,
                    contactAvatarBase64 = storedContact?.avatarBase64,
                    isUnknownContact = isUnknownContact,
                    appColor = selectedColor,
                    messages = messages.filter {
                        (it.from == connectedUserId && it.to == contact) ||
                                (it.from == contact && it.to == connectedUserId)
                    },
                    onBack = { navController.popBackStack() },
                    onSaveContact = { newName, publicKey ->
                        saveContact(contact, newName, publicKey) { loaded -> updateContacts(loaded) }
                        sendKeyExchange(contact)
                        localAvatarBase64?.let { sendAvatarToContact(contact, it) }
                    },
                    onClearChat = {
                        clearChatMessages(contact) {
                            messages = messages.filterNot {
                                (it.from == connectedUserId && it.to == contact) ||
                                        (it.from == contact && it.to == connectedUserId)
                            }
                        }
                    },
                    onDeleteMessage = { message ->
                        deleteMessage(message.id) { messages = messages.filterNot { it.id == message.id } }
                    },
                    onSend = { text ->
                        if (storedContact?.publicKey.isNullOrBlank()) {
                            sendKeyExchange(contact)
                            addSystemMessage(contact, "Intercambio de claves iniciado. Espera a que el contacto responda antes de enviar mensajes.")
                            refreshLocalState()
                            return@ChatDetailScreen
                        }

                        val messageId = UUID.randomUUID().toString()
                        val json = chatCryptoService.buildOutgoingPayload(
                            messageId = messageId,
                            from = connectedUserId,
                            to = contact,
                            plainText = text,
                            contact = storedContact
                        )

                        chatClient.send(json)

                        val uiMessage = UiMessage(
                            id = messageId,
                            from = connectedUserId,
                            to = contact,
                            text = text,
                            mine = true,
                            status = MESSAGE_STATUS_SENT
                        )

                        messages = messages + uiMessage
                        lifecycleScope.launch {
                            saveContactInternal(contact, storedContact.displayName, storedContact.publicKey)
                            saveMessageToDatabaseInternal(uiMessage, contact, MESSAGE_STATUS_SENT)
                            updateContacts(database.contactDao().getContactsOnce())
                            messages = loadMessagesFromDatabaseInternal()
                        }
                    }
                )
            }

            composable("profile") {
                ProfileSettingsScreen(
                    userId = localUserId,
                    displayName = displayName,
                    publicKey = localPublicKey,
                    avatarBase64 = localAvatarBase64,
                    appColor = selectedColor,
                    onDisplayNameChange = { newName ->
                        displayName = newName
                        preferences.edit().putString("display_name", newName).apply()
                    },
                    onAvatarClick = { avatarPicker.launch("image/*") },
                    onColorChange = { color ->
                        selectedColor = color
                        preferences.edit().putString("color", color.name).apply()
                    },
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }

    private fun loadContactsFromDatabase(onLoaded: (List<ContactEntity>) -> Unit) {
        lifecycleScope.launch { onLoaded(database.contactDao().getContactsOnce()) }
    }

    private fun saveUnknownContact(contactId: String, onLoaded: (List<ContactEntity>) -> Unit) {
        saveContact(contactId, UNKNOWN_CONTACT_NAME, null, onLoaded)
    }

    private fun saveContact(contactId: String, contactName: String, publicKey: String?, onLoaded: (List<ContactEntity>) -> Unit) {
        lifecycleScope.launch {
            saveContactInternal(contactId, contactName, publicKey)
            onLoaded(database.contactDao().getContactsOnce())
        }
    }

    private suspend fun saveContactInternal(contactId: String, contactName: String, publicKey: String?): ContactEntity {
        val now = System.currentTimeMillis()
        val existingContact = database.contactDao().findByUsername(contactId)
        val nextDisplayName = when {
            existingContact == null -> contactName
            existingContact.displayName == UNKNOWN_CONTACT_NAME && contactName == UNKNOWN_CONTACT_NAME -> existingContact.displayName
            contactName == UNKNOWN_CONTACT_NAME -> existingContact.displayName
            else -> contactName
        }

        val contact = ContactEntity(
            id = existingContact?.id ?: 0,
            username = contactId,
            displayName = nextDisplayName,
            publicKey = publicKey ?: existingContact?.publicKey,
            avatarBase64 = existingContact?.avatarBase64,
            avatarUpdatedAt = existingContact?.avatarUpdatedAt,
            createdAt = existingContact?.createdAt ?: now,
            lastSeenAt = now
        )

        database.contactDao().save(contact)
        ensureChat(contactId)
        return database.contactDao().findByUsername(contactId) ?: contact
    }

    private fun saveContactAvatar(
        contactId: String,
        avatarBase64: String,
        updatedAt: Long,
        onLoaded: (List<ContactEntity>) -> Unit
    ) {
        lifecycleScope.launch {
            saveContactAvatarInternal(contactId, avatarBase64, updatedAt)
            onLoaded(database.contactDao().getContactsOnce())
        }
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
            lastSeenAt = now
        )

        database.contactDao().save(contact)
        ensureChat(contactId)
        return database.contactDao().findByUsername(contactId) ?: contact
    }

    private fun loadMessagesFromDatabase(onLoaded: (List<UiMessage>) -> Unit) {
        lifecycleScope.launch { onLoaded(loadMessagesFromDatabaseInternal()) }
    }

    private suspend fun loadMessagesFromDatabaseInternal(): List<UiMessage> {
        val chats = database.chatDao().getAllChatsOnce()
        val loadedMessages = mutableListOf<UiMessage>()

        chats.forEach { chat ->
            val chatMessages = database.chatMessageDao().getChatMessagesOnce(chat.id)
                .map { entity ->
                    UiMessage(
                        id = entity.messageId,
                        from = entity.senderUsername,
                        to = entity.receiverUsername,
                        text = entity.body,
                        mine = entity.isMine,
                        timestamp = entity.timestamp,
                        status = entity.deliveryStatus
                    )
                }
            loadedMessages.addAll(chatMessages)
        }

        return loadedMessages.sortedBy { it.timestamp }
    }

    private fun saveMessageToDatabase(message: UiMessage, contactUsername: String, status: String) {
        lifecycleScope.launch { saveMessageToDatabaseInternal(message, contactUsername, status) }
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

        database.chatDao().save(
            chat.copy(
                updatedAt = message.timestamp,
                lastMessagePreview = message.text
            )
        )
    }

    private fun clearChatMessages(contactUsername: String, onDone: () -> Unit) {
        lifecycleScope.launch {
            val chat = database.chatDao().findByContact(contactUsername) ?: return@launch
            database.chatMessageDao().deleteMessagesByChatId(chat.id)
            database.chatDao().save(
                chat.copy(
                    updatedAt = System.currentTimeMillis(),
                    lastMessagePreview = "Sin mensajes todavía"
                )
            )
            onDone()
        }
    }

    private fun deleteMessage(messageId: String, onDone: () -> Unit) {
        lifecycleScope.launch {
            database.chatMessageDao().deleteMessageByMessageId(messageId)
            onDone()
        }
    }

    private fun deleteConversation(contactUsername: String, onDone: () -> Unit) {
        lifecycleScope.launch {
            val chat = database.chatDao().findByContact(contactUsername)
            if (chat != null) {
                database.chatMessageDao().deleteMessagesByChatId(chat.id)
                database.chatDao().deleteChatByContact(contactUsername)
            }
            onDone()
        }
    }

    private fun updateMessageStatus(messageId: String, status: String, onDone: () -> Unit) {
        lifecycleScope.launch {
            database.chatMessageDao().updateDeliveryStatusByMessageId(messageId, status)
            onDone()
        }
    }

    private fun encodeAvatarToBase64(uri: Uri): String? {
        return runCatching {
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val originalBitmap = BitmapFactory.decodeStream(inputStream) ?: return null
            val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, 256, 256, true)
            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
            Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
        }.getOrNull()
    }

    private fun buildProfileAvatarPayload(from: String, to: String, avatarBase64: String): String {
        return """
            {"type":"profile_avatar","from":"$from","to":"$to","avatarBase64":"$avatarBase64","updatedAt":"${System.currentTimeMillis()}"}
        """.trimIndent()
    }

    private suspend fun ensureChat(contactUsername: String): ChatEntity {
        val existingChat = database.chatDao().findByContact(contactUsername)
        if (existingChat != null) return existingChat

        val now = System.currentTimeMillis()
        val chatId = database.chatDao().save(
            ChatEntity(
                contactUsername = contactUsername,
                createdAt = now,
                updatedAt = now,
                lastMessagePreview = "Sin mensajes todavía"
            )
        )

        return ChatEntity(
            id = chatId,
            contactUsername = contactUsername,
            createdAt = now,
            updatedAt = now,
            lastMessagePreview = "Sin mensajes todavía"
        )
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

    override fun onDestroy() {
        super.onDestroy()
        try {
            chatClient.disconnect()
        } catch (_: Exception) {
        }
    }
}
