package com.lrv.privatechat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lrv.privatechat.network.ChatWebSocketClient
import com.lrv.privatechat.ui.theme.PrivateChatTheme

private data class UiMessage(
    val from: String,
    val to: String,
    val text: String,
    val mine: Boolean
)

private data class Conversation(
    val username: String,
    val lastMessage: String
)

class MainActivity : ComponentActivity() {

    private lateinit var chatClient: ChatWebSocketClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            PrivateChatTheme {
                PrivateChatApp()
            }
        }
    }

    @Composable
    private fun PrivateChatApp() {
        val navController = rememberNavController()

        var username by remember { mutableStateOf("luis") }
        var status by remember { mutableStateOf("Desconectado") }
        var messages by remember { mutableStateOf(listOf<UiMessage>()) }
        val contacts = remember { listOf("ana", "carlos", "pepe") }

        LaunchedEffect(Unit) {
            chatClient = ChatWebSocketClient(
                onMessageReceived = { received ->
                    val from = extractValue(received, "from")
                    val to = extractValue(received, "to")
                    val text = extractValue(received, "text")

                    if (from.isNotBlank() && text.isNotBlank()) {
                        messages = messages + UiMessage(
                            from = from,
                            to = to,
                            text = text,
                            mine = false
                        )
                    }
                },
                onStatusChanged = { newStatus ->
                    status = newStatus
                }
            )
        }

        NavHost(
            navController = navController,
            startDestination = "chats"
        ) {
            composable("chats") {
                ChatsScreen(
                    username = username,
                    status = status,
                    contacts = contacts,
                    messages = messages,
                    onOpenChat = { contact ->
                        navController.navigate("chat/$contact")
                    },
                    onOpenProfile = {
                        navController.navigate("profile")
                    }
                )
            }

            composable(
                route = "chat/{contact}",
                arguments = listOf(navArgument("contact") { type = NavType.StringType })
            ) { backStackEntry ->
                val contact = backStackEntry.arguments?.getString("contact") ?: ""

                ChatDetailScreen(
                    username = username,
                    contact = contact,
                    messages = messages.filter {
                        (it.from == username && it.to == contact) ||
                                (it.from == contact && it.to == username)
                    },
                    onBack = {
                        navController.popBackStack()
                    },
                    onSend = { text ->
                        val json = "{\"from\":\"$username\",\"to\":\"$contact\",\"text\":\"$text\"}"

                        chatClient.send(json)

                        messages = messages + UiMessage(
                            from = username,
                            to = contact,
                            text = text,
                            mine = true
                        )
                    }
                )
            }

            composable("profile") {
                ProfileSettingsScreen(
                    username = username,
                    status = status,
                    onUsernameChange = { username = it },
                    onConnect = {
                        chatClient.connect(username)
                    },
                    onBack = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }

    @Composable
    private fun ChatsScreen(
        username: String,
        status: String,
        contacts: List<String>,
        messages: List<UiMessage>,
        onOpenChat: (String) -> Unit,
        onOpenProfile: () -> Unit
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Surface(tonalElevation = 4.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "PrivateChat",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$username · $status",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Button(onClick = onOpenProfile) {
                        Text("Perfil")
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(contacts) { contact ->
                    val lastMessage = messages
                        .lastOrNull {
                            (it.from == username && it.to == contact) ||
                                    (it.from == contact && it.to == username)
                        }
                        ?.text ?: "Sin mensajes todavía"

                    ConversationRow(
                        conversation = Conversation(
                            username = contact,
                            lastMessage = lastMessage
                        ),
                        onClick = {
                            onOpenChat(contact)
                        }
                    )
                }
            }
        }
    }

    @Composable
    private fun ConversationRow(
        conversation: Conversation,
        onClick: () -> Unit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = conversation.username.first().uppercase(),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = conversation.username,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = conversation.lastMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1
                )
            }
        }
    }

    @Composable
    private fun ChatDetailScreen(
        username: String,
        contact: String,
        messages: List<UiMessage>,
        onBack: () -> Unit,
        onSend: (String) -> Unit
    ) {
        var message by remember { mutableStateOf("") }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Surface(tonalElevation = 4.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onBack) {
                        Text("←")
                    }

                    Column {
                        Text(
                            text = contact,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Chat privado",
                            style = MaterialTheme.typography.bodySmall
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
                items(messages) { msg ->
                    MessageBubble(msg)
                }
            }

            Surface(tonalElevation = 4.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = message,
                        onValueChange = { message = it },
                        label = { Text("Mensaje") },
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            if (message.isNotBlank()) {
                                onSend(message)
                                message = ""
                            }
                        }
                    ) {
                        Text("Enviar")
                    }
                }
            }
        }
    }

    @Composable
    private fun MessageBubble(message: UiMessage) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (message.mine) Arrangement.End else Arrangement.Start
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 2.dp,
                color = if (message.mine)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.widthIn(max = 280.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = if (message.mine) "Tú" else message.from,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }

    @Composable
    private fun ProfileSettingsScreen(
        username: String,
        status: String,
        onUsernameChange: (String) -> Unit,
        onConnect: () -> Unit,
        onBack: () -> Unit
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) {
                    Text("←")
                }
                Text(
                    text = "Perfil y ajustes",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Surface(
                tonalElevation = 2.dp,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Identidad local",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = username,
                        onValueChange = onUsernameChange,
                        label = { Text("Tu usuario") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Estado: $status",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Text(
                        text = "Servidor: ws://10.0.2.2:8080/chat",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(onClick = onConnect) {
                        Text("Conectar")
                    }
                }
            }
        }
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
