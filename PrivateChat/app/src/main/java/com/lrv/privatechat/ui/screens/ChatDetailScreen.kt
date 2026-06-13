package com.lrv.privatechat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lrv.privatechat.model.AppColor
import com.lrv.privatechat.model.UiMessage
import com.lrv.privatechat.ui.components.AvatarView
import com.lrv.privatechat.ui.components.MessageBubble
import com.lrv.privatechat.ui.dialogs.SaveContactDialog

@Composable
fun ChatDetailScreen(
    username: String,
    contact: String,
    contactName: String,
    contactAvatarBase64: String?,
    isUnknownContact: Boolean,
    appColor: AppColor,
    messages: List<UiMessage>,
    onBack: () -> Unit,
    onSaveContact: (String, String?) -> Unit,
    onClearChat: () -> Unit,
    onDeleteMessage: (UiMessage) -> Unit,
    onSend: (String) -> Unit
) {
    var message by remember { mutableStateOf("") }
    var showSaveContactDialog by remember { mutableStateOf(false) }
    var showEditNameDialog by remember { mutableStateOf(false) }
    var showClearChatDialog by remember { mutableStateOf(false) }

    if (showSaveContactDialog) {
        SaveContactDialog(
            appColor = appColor,
            contactId = contact,
            onDismiss = { showSaveContactDialog = false },
            onSave = { newName, publicKey ->
                onSaveContact(newName, publicKey)
                showSaveContactDialog = false
            }
        )
    }

    if (showEditNameDialog) {
        var editedName by remember(showEditNameDialog) { mutableStateOf(contactName) }

        AlertDialog(
            onDismissRequest = { showEditNameDialog = false },
            title = { Text("Editar contacto") },
            text = {
                OutlinedTextField(
                    value = editedName,
                    onValueChange = { editedName = it },
                    label = { Text("Nombre") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (editedName.isNotBlank()) {
                            onSaveContact(editedName.trim(), null)
                            showEditNameDialog = false
                        }
                    }
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditNameDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showClearChatDialog) {
        AlertDialog(
            onDismissRequest = { showClearChatDialog = false },
            title = { Text("Vaciar chat") },
            text = { Text("Se borrarán todos los mensajes de esta conversación solo en este dispositivo.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearChat()
                        showClearChatDialog = false
                    }
                ) {
                    Text("Vaciar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearChatDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFECE5DD))
    ) {
        ChatHeader(
            contactName = contactName,
            contactAvatarBase64 = contactAvatarBase64,
            isUnknownContact = isUnknownContact,
            appColor = appColor,
            onBack = onBack,
            onEditName = { showEditNameDialog = true },
            onClearChat = { showClearChatDialog = true },
            onSaveUnknownContact = { showSaveContactDialog = true }
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFFECE5DD))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { msg ->
                MessageBubble(
                    message = msg,
                    appColor = appColor,
                    onDelete = onDeleteMessage
                )
            }
        }

        MessageInputBar(
            message = message,
            appColor = appColor,
            onMessageChange = { message = it },
            onSendClick = {
                if (message.isNotBlank()) {
                    onSend(message)
                    message = ""
                }
            }
        )
    }
}

@Composable
private fun ChatHeader(
    contactName: String,
    contactAvatarBase64: String?,
    isUnknownContact: Boolean,
    appColor: AppColor,
    onBack: () -> Unit,
    onEditName: () -> Unit,
    onClearChat: () -> Unit,
    onSaveUnknownContact: () -> Unit
) {
    Surface(color = appColor.main) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) {
                    Text("←", color = Color.White)
                }

                Surface(
                    shape = CircleShape,
                    color = Color.Transparent,
                    modifier = Modifier.clickable { onEditName() }
                ) {
                    AvatarView(
                        displayName = contactName,
                        avatarBase64 = contactAvatarBase64,
                        appColor = appColor,
                        size = 42.dp
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onEditName() }
                ) {
                    Text(
                        text = contactName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Toca para editar",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }

                TextButton(onClick = onClearChat) {
                    Text("Vaciar", color = Color.White)
                }
            }

            if (isUnknownContact) {
                Surface(
                    color = Color.White.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Este usuario no está guardado",
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onSaveUnknownContact) {
                            Text("Guardar contacto", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageInputBar(
    message: String,
    appColor: AppColor,
    onMessageChange: (String) -> Unit,
    onSendClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFECE5DD))
            .padding(8.dp)
            .imePadding(),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = message,
            onValueChange = onMessageChange,
            placeholder = { Text("Mensaje") },
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .weight(1f)
                .background(Color.White, RoundedCornerShape(28.dp))
        )

        Spacer(modifier = Modifier.width(8.dp))

        Button(
            onClick = onSendClick,
            shape = CircleShape,
            modifier = Modifier.size(54.dp),
            contentPadding = PaddingValues(0.dp),
            colors = ButtonDefaults.buttonColors(containerColor = appColor.main)
        ) {
            Text("➤", color = Color.White)
        }
    }
}
