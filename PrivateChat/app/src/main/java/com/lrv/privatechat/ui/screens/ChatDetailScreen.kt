package com.lrv.privatechat.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    onDeleteContact: () -> Unit,
    onDeleteMessage: (UiMessage) -> Unit,
    onSend: (String) -> Unit,
    onAttachImage: () -> Unit = {},
    onTakePhoto: () -> Unit = {},
    onMicClick: () -> Unit = {},
    onCancelRecording: () -> Unit = {},
    isRecording: Boolean = false,
    recordingSeconds: Int = 0,
    isContactAccepted: Boolean = true
) {
    var message by remember { mutableStateOf("") }
    var showSaveContactDialog by remember { mutableStateOf(false) }
    var showEditNameDialog by remember { mutableStateOf(false) }
    var showClearChatDialog by remember { mutableStateOf(false) }
    var showContactMenu by remember { mutableStateOf(false) }
    var showDeleteContactDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(contact, messages.size, messages.lastOrNull()?.id) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

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
                ) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { showEditNameDialog = false }) { Text("Cancelar") } }
        )
    }

    if (showClearChatDialog) {
        AlertDialog(
            onDismissRequest = { showClearChatDialog = false },
            title = { Text("Borrar mensajes") },
            text = { Text("Se borrarán todos los mensajes de esta conversación solo en este dispositivo.") },
            confirmButton = {
                TextButton(onClick = { onClearChat(); showClearChatDialog = false }) { Text("Borrar") }
            },
            dismissButton = { TextButton(onClick = { showClearChatDialog = false }) { Text("Cancelar") } }
        )
    }

    if (showDeleteContactDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteContactDialog = false },
            title = { Text("Eliminar contacto") },
            text = { Text("Se eliminará el contacto y todos sus mensajes de este dispositivo.") },
            confirmButton = {
                TextButton(onClick = { onDeleteContact(); showDeleteContactDialog = false }) {
                    Text("Eliminar", color = androidx.compose.ui.graphics.Color(0xFFE53935))
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteContactDialog = false }) { Text("Cancelar") } }
        )
    }

    if (showContactMenu) {
        AlertDialog(
            onDismissRequest = { showContactMenu = false },
            title = { Text(contactName) },
            text = {
                Column {
                    TextButton(
                        onClick = { showContactMenu = false; showClearChatDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("🗑️  Borrar mensajes", modifier = Modifier.fillMaxWidth()) }
                    TextButton(
                        onClick = { showContactMenu = false; showDeleteContactDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("❌  Eliminar contacto", color = androidx.compose.ui.graphics.Color(0xFFE53935), modifier = Modifier.fillMaxWidth()) }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showContactMenu = false }) { Text("Cancelar") } }
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
            onLongPress = { showContactMenu = true },
            onSaveUnknownContact = { showSaveContactDialog = true }
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFFECE5DD))
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 8.dp)
        ) {
            items(messages) { msg ->
                MessageBubble(message = msg, appColor = appColor, onDelete = onDeleteMessage)
            }
        }

        if (!isContactAccepted) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFFF8E1))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "⏳ Solicitud pendiente de aceptación",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF795548)
                )
            }
        } else {
            MessageInputBar(
                message = message,
                appColor = appColor,
                onMessageChange = { message = it },
                onSendClick = {
                    if (message.isNotBlank()) {
                        onSend(message)
                        message = ""
                    }
                },
                onAttachImage = onAttachImage,
                onTakePhoto = onTakePhoto,
                onMicClick = onMicClick,
                onCancelRecording = onCancelRecording,
                isRecording = isRecording,
                recordingSeconds = recordingSeconds
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatHeader(
    contactName: String,
    contactAvatarBase64: String?,
    isUnknownContact: Boolean,
    appColor: AppColor,
    onBack: () -> Unit,
    onEditName: () -> Unit,
    onLongPress: () -> Unit,
    onSaveUnknownContact: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(color = appColor.main, modifier = modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) { Text("←", color = Color.White) }
                Surface(shape = CircleShape, color = Color.Transparent, modifier = Modifier.clickable { onEditName() }) {
                    AvatarView(displayName = contactName, avatarBase64 = contactAvatarBase64, appColor = appColor, size = 42.dp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .combinedClickable(onClick = onEditName, onLongClick = onLongPress)
                ) {
                    Text(text = contactName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(text = "Mantén pulsado para opciones", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.75f))
                }
            }

            if (isUnknownContact) {
                Surface(color = Color.White.copy(alpha = 0.12f), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "Este usuario no está guardado", color = Color.White, modifier = Modifier.weight(1f))
                        TextButton(onClick = onSaveUnknownContact) { Text("Guardar contacto", color = Color.White) }
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
    isRecording: Boolean,
    recordingSeconds: Int,
    onMessageChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onAttachImage: () -> Unit = {},
    onTakePhoto: () -> Unit = {},
    onMicClick: () -> Unit = {},
    onCancelRecording: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFFECE5DD))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Campo con iconos dentro
        Row(
            modifier = Modifier
                .weight(1f)
                .background(Color.White, RoundedCornerShape(24.dp))
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isRecording) {
                // Cancelar grabación
                TextButton(
                    onClick = onCancelRecording,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(40.dp)
                ) { Text("✕", fontSize = 18.sp, color = Color(0xFF777777)) }
                // Punto rojo
                Box(modifier = Modifier.size(8.dp).background(Color.Red, CircleShape))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Grabando  ${recordingSeconds}s",
                    modifier = Modifier.weight(1f).padding(vertical = 14.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF333333)
                )
            } else {
                // Icono cámara
                TextButton(
                    onClick = onTakePhoto,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(40.dp)
                ) { Text("📷", fontSize = 18.sp) }

                // Campo texto
                OutlinedTextField(
                    value = message,
                    onValueChange = onMessageChange,
                    placeholder = { Text("Mensaje") },
                    modifier = Modifier.weight(1f),
                    singleLine = false,
                    maxLines = 4,
                    shape = RoundedCornerShape(0.dp),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = Color.Transparent
                    )
                )

                // Icono adjuntar (cualquier archivo)
                TextButton(
                    onClick = onAttachImage,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(40.dp)
                ) { Text("📎", fontSize = 18.sp) }
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Botón derecho: enviar / micrófono / parar grabación
        Button(
            onClick = {
                when {
                    isRecording -> onMicClick()
                    message.isNotBlank() -> onSendClick()
                    else -> onMicClick()
                }
            },
            shape = CircleShape,
            modifier = Modifier.size(54.dp),
            contentPadding = PaddingValues(0.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRecording) Color(0xFFE53935) else appColor.main
            )
        ) {
            Text(
                text = when {
                    isRecording -> "⏹"
                    message.isNotBlank() -> "➤"
                    else -> "🎤"
                },
                fontSize = 20.sp
            )
        }
    }
}
