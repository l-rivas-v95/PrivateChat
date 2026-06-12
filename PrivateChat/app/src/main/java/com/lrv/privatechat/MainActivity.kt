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

private data class UiMessage(
    val from: String,
    val to: String,
    val text: String,
    val mine: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

private data class Conversation(
    val username: String,
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

        var username by remember { mutableStateOf(preferences.getString("username", "luis") ?: "luis") }
        var connectedUsername by remember { mutableStateOf(preferences.getString("connected_username", "luis") ?: "luis") }
        var selectedColor by remember {
            mutableStateOf(
                AppColor.valueOf(preferences.getString("color", AppColor.GREEN.name) ?: AppColor.GREEN.name)
            )
        }
        var status by remember { mutableStateOf("Desconectado") }
        var messages by remember { mutableStateOf(listOf<UiMessage>()) }
        val contacts = remember { listOf("ana", "carlos", "pepe", "luis") }

        LaunchedEffect(Unit) {
            seedContacts(contacts)
            loadMessagesFromDatabase { loaded -> messages = loaded }

            chatClient = ChatWebSocketClient(
                onMessageReceived = { received ->
                    val from = extractValue(received, "from")
                    val to = extractValue(received, "to")
                    val text = extractValue(received, "text")

                    if (from.isNotBlank() && text.isNotBlank()) {
                        val uiMessage = UiMessage(
                            from = from,
                            to = to,
                            text = text,
                            mine = false
                        )

                        messages = messages + uiMessage
                        saveMessageToDatabase(uiMessage, contactUsername = from)
                    }
                },
                onStatusChanged = { newStatus ->
                    status = newStatus
                }
            )
        }

        val visibleContacts = contacts.filter { it != connectedUsername }

        NavHost(
            navController = navController,
            startDestination = "chats"
        ) {
            composable("chats") {
                ChatsScreen(
                    username = connectedUsername,
                    status = status,
                    appColor = selectedColor,
                    contacts = visibleContacts,
                    messages = messages,
                    onConnect = {
                        connectedUsername = username
                        preferences.edit().putString("connected_username", username).apply()
                        chatClient.connect(username)
                    },
                    onOpenChat = { contact -> navController.navigate("chat/$contact") },
                    onOpenProfile = { navController.navigate("profile") }
                )
            }

            composable(
                route = "chat/{contact}",
                arguments = listOf(navArgument("contact") { type = NavType.StringType })
            ) { backStackEntry ->
                val contact = backStackEntry.arguments?.getString("contact") ?: ""

                ChatDetailScreen(
                    username = connectedUsername,
                    contact = contact,
                    appColor = selectedColor,
                    messages = messages.filter {
                        (it.from == connectedUsername && it.to == contact) ||
                                (it.from == contact && it.to == connectedUsername)
                    },
                    onBack = { navController.popBackStack() },
                    onSend = { text ->
                        val json = "{\"from\":\"$connectedUsername\",\"to\":\"$contact\",\"text\":\"$text\"}"

                        chatClient.send(json)

                        val uiMessage = UiMessage(
                            from = connectedUsername,
                            to = contact,
                            text = text,
                            mine = true
                        )

                        messages = messages + uiMessage
                        saveMessageToDatabase(uiMessage, contactUsername = contact)
                    }
                )
            }

            composable("profile") {
                ProfileSettingsScreen(
                    username = username,
                    appColor = selectedColor,
                    onUsernameChange = { newName ->
                        username = newName
                        preferences.edit().putString("username", newName).apply()
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
        username: String,
        status: String,
        appColor: AppColor,
        contacts: List<String>,
        messages: List<UiMessage>,
        onConnect: () -> Unit,
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
                                text = "$username · $status",
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
                            onClick = onConnect,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                        ) {
                            Text("Conectar", color = appColor.main)
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = if (status == "Conectado") "Listo para chatear" else "Pulsa conectar para entrar",
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(contacts) { contact ->
                    val lastMessage = messages
                        .lastOrNull {
                            (it.from == username && it.to == contact) ||
                                    (it.from == contact && it.to == username)
                        }
                        ?.text ?: "Sin mensajes todavía"

                    ConversationRow(
                        conversation = Conversation(contact, lastMessage),
                        appColor = appColor,
                        onClick = { onOpenChat(contact) }
                    )
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
                        text = conversation.username.first().uppercase(),
                        fontWeight = FontWeight.Bold,
                        color = appColor.main
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = conversation.username.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF111111)
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = conversation.lastMessage,
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
    private fun ChatDetailScreen(
        username: String,
        contact: String,
        appColor: AppColor,
        messages: List<UiMessage>,
        onBack: () -> Unit,
        onSend: (String) -> Unit
    ) {
        var message by remember { mutableStateOf("") }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFECE5DD))
        ) {
            Surface(color = appColor.main) {
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
                                text = contact.first().uppercase(),
                                fontWeight = FontWeight.Bold,
                                color = appColor.main
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = contact.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "En línea",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f)
                        )
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
                        text = if (message.mine) "enviado" else message.from,
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
        username: String,
        appColor: AppColor,
        onUsernameChange: (String) -> Unit,
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
                            text = "Nombre",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = username,
                            onValueChange = onUsernameChange,
                            label = { Text("Tu nombre") },
                            modifier = Modifier.fillMaxWidth()
                        )
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

    private fun seedContacts(contacts: List<String>) {
        lifecycleScope.launch {
            val now = System.currentTimeMillis()
            contacts.forEach { contact ->
                database.contactDao().save(
                    ContactEntity(
                        username = contact,
                        displayName = contact.replaceFirstChar { it.uppercase() },
                        createdAt = now
                    )
                )
                ensureChat(contact)
            }
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
                            from = entity.senderUsername,
                            to = entity.receiverUsername,
                            text = entity.body,
                            mine = entity.isMine,
                            timestamp = entity.timestamp
                        )
                    }

                loadedMessages.addAll(chatMessages)
            }

            onLoaded(loadedMessages.sortedBy { it.timestamp })
        }
    }

    private fun saveMessageToDatabase(message: UiMessage, contactUsername: String) {
        lifecycleScope.launch {
            val chat = ensureChat(contactUsername)

            database.chatMessageDao().saveChatMessage(
                MessageEntity(
                    chatId = chat.id,
                    senderUsername = message.from,
                    receiverUsername = message.to,
                    body = message.text,
                    timestamp = message.timestamp,
                    isMine = message.mine,
                    deliveryStatus = if (message.mine) "SENT" else "RECEIVED"
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
