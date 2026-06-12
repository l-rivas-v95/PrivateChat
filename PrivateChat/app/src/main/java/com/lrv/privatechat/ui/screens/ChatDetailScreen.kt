package com.lrv.privatechat.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lrv.privatechat.model.AppColor
import com.lrv.privatechat.model.UiMessage
import com.lrv.privatechat.ui.components.MessageBubble
import com.lrv.privatechat.ui.dialogs.SaveContactDialog

@Composable
fun ChatDetailScreen(
    username: String,
    contact: String,
    contactName: String,
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
            items(messages) { msg ->
                MessageBubble(
                    message = msg,
                    appColor = appColor,
                    onDelete = onDeleteMessage
                )
            }
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
