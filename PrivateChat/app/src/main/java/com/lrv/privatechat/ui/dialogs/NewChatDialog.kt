package com.lrv.privatechat.ui.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import com.lrv.privatechat.model.AppColor

@Composable
fun NewChatDialog(
    appColor: AppColor,
    onDismiss: () -> Unit,
    onSaveManual: (String, String, String?) -> Unit
) {
    var contactId by remember { mutableStateOf("") }
    var contactName by remember { mutableStateOf("") }
    var publicKey by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo chat") },
        text = {
            Column {
                Text(
                    text = "Añade el ID y, si lo tienes, la clave pública del contacto.",
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

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = publicKey,
                    onValueChange = { publicKey = it.trim() },
                    label = { Text("Clave pública opcional") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
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
                        onSaveManual(contactId, contactName.ifBlank { contactId }, publicKey.ifBlank { null })
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
