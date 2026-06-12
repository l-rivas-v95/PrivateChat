package com.lrv.privatechat

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lrv.privatechat.data.PrivateChatDatabase
import com.lrv.privatechat.data.entity.ChatEntity
import com.lrv.privatechat.data.entity.ContactEntity
import com.lrv.privatechat.data.entity.MessageEntity
import com.lrv.privatechat.network.ChatWebSocketClient
import com.lrv.privatechat.ui.theme.PrivateChatTheme
import kotlinx.coroutines.launch
import java.util.UUID

private const val UNKNOWN_CONTACT_NAME = "Usuario desconocido"

private data class UiMessage(
    val id: String = UUID.randomUUID().toString(),
    val from: String,
    val to: String,
    val text: String,
    val mine: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = if (mine) "SENT" else "RECEIVED"
)

private data class Conversation(
    val username: String,
    val displayName: String,
    val lastMessage: String
)

private enum class AppColor(
    val label: String,
    val main: Color,
    val light: Color
) {
    GREEN("Verde", Color(0xFF075E54), Color(0xFFD9FDD3)),
    BLUE("Azul", Color(0xFF0B5CAD), Color(0xFFD8EAFE)),
    PURPLE("Morado", Color(0xFF6750A4), Color(0xFFEADDFF))
}

class MainActivity : ComponentActivity() {

    private lateinit var chatClient: ChatWebSocketClient
    private lateinit var database: PrivateChatDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = PrivateChatDatabase.getInstance(this)

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
        val initialUserId = remember {
            preferences.getString("local_user_id", null) ?: UUID.randomUUID().toString().also {
                preferences.edit().putString("local_user_id", it).apply()
            }
        }

        var localUserId by remember { mutableStateOf(initialUserId) }
        var connectedUserId by remember { mutableStateOf(localUserId) }
        var displayName by remember { mutableStateOf(preferences.getString("display_name", "Luis") ?: "Luis") }
        var selectedColor by remember {
            mutableStateOf(
                AppColor.valueOf(preferences.getString("color", AppColor.GREEN.name) ?: AppColor.GREEN.name)
            )
        }
        var status by remember { mutableStateOf("Desconectado") }
        var messages by remember { mutableStateOf(listOf<UiMessage>()) }
        var contacts by remember { mutableStateOf(listOf<ContactEntity>()) }
        var showNewChatDialog by remember { mutableStateOf(false) }

        fun reloadMessages() {
            loadMessagesFromDatabase { loaded -> messages = loaded }
        }

        fun reloadContacts() {
            loadContactsFromDatabase { loaded -> contacts = loaded }
        }

        fun connect() {
            connectedUserId = localUserId
            preferences.edit().putString("connected_user_id", localUserId).apply()
            chatClient.connect(localUserId)
        }

        fun disconnect() {
            chatClient.disconnect()
            status = "Desconectado"
        }

        LaunchedEffect(Unit) {
            reloadContacts()
            reloadMessages()

            chatClient = ChatWebSocketClient(
                onMessageReceived = { received ->
                    val from = extractValue(received, "from")
                    val to = extractValue(received, "to")
                    val text = extractValue(received, "text")
                    val messageId = extractValue(received, "id").ifBlank { UUID.randomUUID().toString() }

                    if (from.isNotBlank() && text.isNotBlank()) {
                        val uiMessage = UiMessage(
                            id = messageId,
                            from = from,
                            to = to,
                            text = text,
                            mine = false,
                            status = "RECEIVED"
                        )

                        messages = messages + uiMessage
                        saveMessageToDatabase(uiMessage, contactUsername = from, status = "RECEIVED")
                        if (contacts.none { it.username == from }) {
                            saveUnknownContact(from) { contacts = it }
                        }
                    }
                },
                onStatusChanged = { newStatus ->
                    status = newStatus
                }
            )

            connect()
        }

        if (showNewChatDialog) {
            NewChatDialog(
                appColor = selectedColor,
                onDismiss = { showNewChatDialog = false },
                onSaveManual = { contactId, contactName ->
                    saveContact(contactId, contactName) { contacts = it }
                    showNewChatDialog = false
                }
            )
        }

        val visibleContacts = contacts.filter { it.username != connectedUserId }
        val isConnected = status == "Conectado"

        NavHost(
            navController = navController,
            startDestination = "chats"
        ) {
            composable("chats") {
                ChatsScreen(
                    userId = connectedUserId,
                    displayName = displayName,
                    status = status,
                    isConnected = isConnected,
                    appColor = selectedColor,
                    contacts = visibleContacts,
                    messages = messages,
                    onToggleConnection = {
                        if (isConnected) disconnect() else connect()
                    },
                    onNewChat = { showNewChatDialog = true },
                    onOpenChat = { contact -> navController.navigate("chat/$contact") },
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
                    isUnknownContact = isUnknownContact,
                    appColor = selectedColor,
                    messages = messages.filter {
                        (it.from == connectedUserId && it.to == contact) ||
                                (it.from == contact && it.to == connectedUserId)
                    },
                    onBack = { navController.popBackStack() },
                    onSaveContact = { newName ->
                        saveContact(contact, newName) { contacts = it }
                    },
                    onSend = { text ->
                        val messageId = UUID.randomUUID().toString()
                        val json = "{\"id\":\"$messageId\",\"from\":\"$connectedUserId\",\"to\":\"$contact\",\"text\":\"$text\"}"

                        chatClient.send(json)

                        val uiMessage = UiMessage(
                            id = messageId,
                            from = connectedUserId,
                            to = contact,
                            text = text,
                            mine = true,
                            status = "SENT"
                        )

                        messages = messages + uiMessage
                        saveMessageToDatabase(uiMessage, contactUsername = contact, status = "SENT")
                    }
                )
            }

            composable("profile") {
                ProfileSettingsScreen(
                    userId = localUserId,
                    displayName = displayName,
                    appColor = selectedColor,
                    onDisplayNameChange = { newName ->
                        displayName = newName
                        preferences.edit().putString("display_name", newName).apply()
                    },
                    onColorChange = { color ->
                        selectedColor = color
                        preferences.edit().putString("color", color.name).apply()
                    },
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }

    @Composable
    private fun ChatsScreen(
        userId: String,
        displayName: String,
        status: String,
        isConnected: Boolean,
        appColor: AppColor,
        contacts: List<ContactEntity>,
        messages: List<UiMessage>,
        onToggleConnection: () -> Unit,
        onNewChat: () -> Unit,
        onOpenChat: (String) -> Unit,
        onOpenProfile: () -> Unit
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF7F7F7))
        ) {
            Surface(color = appColor.main) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "PrivateChat",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "$displayName · $status",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }

                        TextButton(onClick = onOpenProfile) {
                            Text("Perfil", color = Color.White)
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onToggleConnection,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                        ) {
                            Text(
                                text = if (isConnected) "Desconectar" else "Conectar",
                                color = appColor.main
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Button(
                            onClick = onNewChat,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                        ) {
                            Text("+ Nuevo chat", color = appColor.main)
                        }
                    }

                    Text(
                        text = "Mi ID: ${userId.take(8)}...",
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            if (contacts.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No tienes chats. Pulsa + Nuevo chat para añadir un contacto.",
                        color = Color(0xFF666666),
                        modifier = Modifier.padding(24.dp)
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(contacts) { contact ->
                        val lastMessage = messages
                            .lastOrNull {
                                (it.from == userId && it.to == contact.username) ||
                                        (it.from == contact.username && it.to == userId)
                            }
                            ?.text ?: "Sin mensajes todavía"

                        val displayName = if (contact.displayName == contact.username) {
                            UNKNOWN_CONTACT_NAME
                        } else {
                            contact.displayName
                        }

                        ConversationRow(
                            conversation = Conversation(contact.username, displayName, lastMessage),
                            appColor = appColor,
                            onClick = { onOpenChat(contact.username) }
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun ConversationRow(
        conversation: Conversation,
        appColor: AppColor,
        onClick: () -> Unit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = appColor.light,
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = conversation.displayName.firstOrNull()?.uppercase() ?: "?",
                        fontWeight = FontWeight.Bold,
                        color = appColor.main
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = conversation.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF111111)
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = conversation.username.take(8) + "... · " + conversation.lastMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF666666),
                    maxLines = 1
                )
            }

            Text(
                text = "ahora",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF777777)
            )
        }
    }

    @Composable
    private fun NewChatDialog(
        appColor: AppColor,
        onDismiss: () -> Unit,
        onSaveManual: (String, String) -> Unit
    ) {
        var contactId by remember { mutableStateOf("") }
        var contactName by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Nuevo chat") },
            text = {
                Column {
                    Text(
                        text = "Añade un contacto por ID manualmente. El escaneo QR se añadirá en el siguiente paso.",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = contactId,
                        onValueChange = { contactId = it.trim() },
                        label = { Text("ID del contacto") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = contactName,
                        onValueChange = { contactName = it },
                        label = { Text("Nombre para guardar") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Surface(
                        color = appColor.light,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "QR: pendiente de implementar escáner",
                            color = appColor.main,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (contactId.isNotBlank()) {
                            onSaveManual(contactId, contactName.ifBlank { contactId })
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = appColor.main)
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancelar")
                }
            }
        )
    }

    @Composable
    private fun SaveContactDialog(
        appColor: AppColor,
        contactId: String,
        onDismiss: () -> Unit,
        onSave: (String) -> Unit
    ) {
        var contactName by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Guardar contacto") },
            text = {
                Column {
                    Text("ID: ${contactId.take(12)}...")
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = contactName,
                        onValueChange = { contactName = it },
                        label = { Text("Nombre") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (contactName.isNotBlank()) {
                            onSave(contactName.trim())
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = appColor.main)
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancelar")
                }
            }
        )
    }

    @Composable
    private fun ChatDetailScreen(
        username: String,
        contact: String,
        contactName: String,
        isUnknownContact: Boolean,
        appColor: AppColor,
        messages: List<UiMessage>,
        onBack: () -> Unit,
        onSaveContact: (String) -> Unit,
        onSend: (String) -> Unit
    ) {
        var message by remember { mutableStateOf("") }
        var showSaveContactDialog by remember { mutableStateOf(false) }

        if (showSaveContactDialog) {
            SaveContactDialog(
                appColor = appColor,
                contactId = contact,
                onDismiss = { showSaveContactDialog = false },
                onSave = { newName ->
                    onSaveContact(newName)
                    showSaveContactDialog = false
                }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFECE5DD))
        ) {
            Surface(color = appColor.main) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onBack) {
                            Text("←", color = Color.White)
                        }

                        Surface(
                            shape = CircleShape,
                            color = appColor.light,
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = contactName.firstOrNull()?.uppercase() ?: "?",
                                    fontWeight = FontWeight.Bold,
                                    color = appColor.main
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = contactName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = contact.take(12) + "...",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }

                    if (isUnknownContact) {
                        Surface(
                            color = Color.White.copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Este usuario no está guardado",
                                    color = Color.White,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { showSaveContactDialog = true }) {
                                    Text("Guardar contacto", color = Color.White)
                                }
                            }
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { msg -> MessageBubble(msg, appColor) }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    placeholder = { Text("Mensaje") },
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier
                        .weight(1f)
                        .background(Color.White, RoundedCornerShape(28.dp))
                )

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = {
                        if (message.isNotBlank()) {
                            onSend(message)
                            message = ""
                        }
                    },
                    shape = CircleShape,
                    modifier = Modifier.size(54.dp),
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = appColor.main)
                ) {
                    Text("➤", color = Color.White)
                }
            }
        }
    }

    @Composable
    private fun MessageBubble(
        message: UiMessage,
        appColor: AppColor
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (message.mine) Arrangement.End else Arrangement.Start
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 1.dp,
                color = if (message.mine) appColor.light else Color.White,
                modifier = Modifier.widthIn(max = 290.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFF111111)
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = if (message.mine) "enviado" else message.from.take(8) + "...",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF777777),
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }
        }
    }

    @Composable
    private fun ProfileSettingsScreen(
        userId: String,
        displayName: String,
        appColor: AppColor,
        onDisplayNameChange: (String) -> Unit,
        onColorChange: (AppColor) -> Unit,
        onBack: () -> Unit
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF7F7F7))
        ) {
            Surface(color = appColor.main) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onBack) {
                        Text("←", color = Color.White)
                    }
                    Text(
                        text = "Perfil",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Column(modifier = Modifier.padding(16.dp)) {
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(18.dp),
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Mi identidad",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = displayName,
                            onValueChange = onDisplayNameChange,
                            label = { Text("Nombre visible") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Mi ID fijo",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )

                        Text(
                            text = userId,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF555555)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Surface(
                            color = appColor.light,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "QR",
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = appColor.main
                                    )
                                    Text(
                                        text = "Pendiente de generar",
                                        color = appColor.main
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(18.dp),
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Color de la aplicación",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        AppColor.values().forEach { color ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onColorChange(color) }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = color.main,
                                    modifier = Modifier.size(28.dp)
                                ) {}

                                Spacer(modifier = Modifier.width(12.dp))

                                Text(
                                    text = color.label,
                                    modifier = Modifier.weight(1f)
                                )

                                if (color == appColor) {
                                    Text("Seleccionado", color = color.main)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun loadContactsFromDatabase(onLoaded: (List<ContactEntity>) -> Unit) {
        lifecycleScope.launch {
            onLoaded(database.contactDao().getContactsOnce())
        }
    }

    private fun saveUnknownContact(contactId: String, onLoaded: (List<ContactEntity>) -> Unit) {
        saveContact(contactId, UNKNOWN_CONTACT_NAME, onLoaded)
    }

    private fun saveContact(contactId: String, contactName: String, onLoaded: (List<ContactEntity>) -> Unit) {
        lifecycleScope.launch {
            val now = System.currentTimeMillis()
            val existingContact = database.contactDao().findByUsername(contactId)

            database.contactDao().save(
                ContactEntity(
                    id = existingContact?.id ?: 0,
                    username = contactId,
                    displayName = contactName,
                    createdAt = existingContact?.createdAt ?: now,
                    lastSeenAt = existingContact?.lastSeenAt
                )
            )
            ensureChat(contactId)
            onLoaded(database.contactDao().getContactsOnce())
        }
    }

    private fun loadMessagesFromDatabase(onLoaded: (List<UiMessage>) -> Unit) {
        lifecycleScope.launch {
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

            onLoaded(loadedMessages.sortedBy { it.timestamp })
        }
    }

    private fun saveMessageToDatabase(
        message: UiMessage,
        contactUsername: String,
        status: String
    ) {
        lifecycleScope.launch {
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
    }

    private suspend fun ensureChat(contactUsername: String): ChatEntity {
        val existingChat = database.chatDao().findByContact(contactUsername)

        if (existingChat != null) {
            return existingChat
        }

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
