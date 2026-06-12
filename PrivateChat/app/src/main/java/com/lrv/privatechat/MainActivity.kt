package com.lrv.privatechat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lrv.privatechat.network.ChatWebSocketClient
import com.lrv.privatechat.ui.theme.PrivateChatTheme

data class UiMessage(
    val from: String,
    val text: String,
    val mine: Boolean
)

class MainActivity : ComponentActivity() {

    private lateinit var chatClient: ChatWebSocketClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            PrivateChatTheme {
                ChatScreen()
            }
        }
    }

    @Composable
    fun ChatScreen() {
        var username by remember { mutableStateOf("luis") }
        var to by remember { mutableStateOf("ana") }
        var message by remember { mutableStateOf("") }
        var status by remember { mutableStateOf("Desconectado") }
        var messages by remember { mutableStateOf(listOf<UiMessage>()) }

        LaunchedEffect(Unit) {
            chatClient = ChatWebSocketClient(
                onMessageReceived = { received ->
                    val from = extractValue(received, "from")
                    val text = extractValue(received, "text")

                    messages = messages + UiMessage(
                        from = from,
                        text = text,
                        mine = false
                    )
                },
                onStatusChanged = { newStatus ->
                    status = newStatus
                }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            TopBar(status)

            UserConfig(
                username = username,
                to = to,
                onUsernameChange = { username = it },
                onToChange = { to = it },
                onConnect = { chatClient.connect(username) }
            )

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

            MessageInput(
                message = message,
                onMessageChange = { message = it },
                onSend = {
                    if (message.isNotBlank()) {
                        val json = "{\"from\":\"$username\",\"to\":\"$to\",\"text\":\"$message\"}"

                        chatClient.send(json)

                        messages = messages + UiMessage(
                            from = username,
                            text = message,
                            mine = true
                        )

                        message = ""
                    }
                }
            )
        }
    }

    @Composable
    fun TopBar(status: String) {
        Surface(
            tonalElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "PrivateChat",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "Estado: $status",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }

    @Composable
    fun UserConfig(
        username: String,
        to: String,
        onUsernameChange: (String) -> Unit,
        onToChange: (String) -> Unit,
        onConnect: () -> Unit
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            OutlinedTextField(
                value = username,
                onValueChange = onUsernameChange,
                label = { Text("Tu usuario") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = to,
                onValueChange = onToChange,
                label = { Text("Destinatario") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(onClick = onConnect) {
                Text("Conectar")
            }
        }
    }

    @Composable
    fun MessageBubble(message: UiMessage) {
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
                        style = MaterialTheme.typography.labelSmall
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
    fun MessageInput(
        message: String,
        onMessageChange: (String) -> Unit,
        onSend: () -> Unit
    ) {
        Surface(
            tonalElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = message,
                    onValueChange = onMessageChange,
                    label = { Text("Mensaje") },
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Button(onClick = onSend) {
                    Text("Enviar")
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