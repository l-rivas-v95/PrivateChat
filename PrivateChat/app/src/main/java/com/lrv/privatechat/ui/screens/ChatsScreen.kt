package com.lrv.privatechat.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lrv.privatechat.data.entity.ContactEntity
import com.lrv.privatechat.model.AppColor
import com.lrv.privatechat.model.ChatItemUiModel
import com.lrv.privatechat.model.UNKNOWN_CONTACT_NAME
import com.lrv.privatechat.model.UiMessage
import com.lrv.privatechat.ui.components.AvatarView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatsScreen(
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
    onDeleteConversation: (String) -> Unit,
    onOpenProfile: () -> Unit
) {
    var conversationToDelete by remember { mutableStateOf<ChatItemUiModel?>(null) }

    if (conversationToDelete != null) {
        AlertDialog(
            onDismissRequest = { conversationToDelete = null },
            title = { Text("Borrar conversación") },
            text = { Text("Se borrará esta conversación y sus mensajes solo en este dispositivo.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        conversationToDelete?.let { onDeleteConversation(it.username) }
                        conversationToDelete = null
                    }
                ) {
                    Text("Borrar")
                }
            },
            dismissButton = {
                TextButton(onClick = { conversationToDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

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

                    val visibleName = if (contact.displayName == contact.username) {
                        UNKNOWN_CONTACT_NAME
                    } else {
                        contact.displayName
                    }

                    val chatItem = ChatItemUiModel(
                        username = contact.username,
                        displayName = visibleName,
                        lastMessage = lastMessage?.text ?: "Sin mensajes todavía",
                        timeText = lastMessage?.timestamp?.let { formatChatTime(it) } ?: "",
                        avatarBase64 = contact.avatarBase64
                    )

                    ChatListRow(
                        chatItem = chatItem,
                        appColor = appColor,
                        onClick = { onOpenChat(contact.username) },
                        onLongClick = { conversationToDelete = chatItem }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatListRow(
    chatItem: ChatItemUiModel,
    appColor: AppColor,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AvatarView(
            displayName = chatItem.displayName,
            avatarBase64 = chatItem.avatarBase64,
            appColor = appColor,
            size = 52.dp
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = chatItem.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF111111)
            )
            Spacer(modifier = Modifier.size(3.dp))
            Text(
                text = chatItem.lastMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF666666),
                maxLines = 1
            )
        }

        Text(
            text = chatItem.timeText,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF777777)
        )
    }
}

private fun formatChatTime(timestamp: Long): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
}
