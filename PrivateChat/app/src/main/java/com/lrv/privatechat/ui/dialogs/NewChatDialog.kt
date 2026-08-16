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
import com.lrv.privatechat.network.payload.ChatPayloads

@Composable
fun NewChatDialog(
    appColor: AppColor,
    scannedQrContent: String?,
    onDismiss: () -> Unit,
    onScanQr: () -> Unit,
    onSaveManual: (String, String, String?) -> Unit
) {
    var contactId by remember { mutableStateOf("") }
    var contactName by remember { mutableStateOf("") }
    var publicKey by remember { mutableStateOf("") }
    var importedQr by remember { mutableStateOf<String?>(null) }

    if (!scannedQrContent.isNullOrBlank() && scannedQrContent != importedQr) {
        ChatPayloads.parseContactQr(scannedQrContent)?.let { contact ->
            contactId = contact.userId
            contactName = contact.displayName
            publicKey = contact.publicKey
            importedQr = scannedQrContent
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo chat") },
        text = {
            Column {
                Text(
                    text = "Escanea el QR del contacto o introduce su ID manualmente.",
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
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = if (importedQr == null) "QR: importa los datos del contacto" else "QR importado correctamente",
                            color = appColor.main,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = onScanQr,
                            colors = ButtonDefaults.buttonColors(containerColor = appColor.main),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Escanear QR")
                        }
                    }
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
