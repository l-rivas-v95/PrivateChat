package com.lrv.privatechat.ui.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
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
fun SaveContactDialog(
    appColor: AppColor,
    contactId: String,
    onDismiss: () -> Unit,
    onSave: (String, String?) -> Unit
) {
    var contactName by remember { mutableStateOf("") }
    var publicKey by remember { mutableStateOf("") }

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
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = publicKey,
                    onValueChange = { publicKey = it.trim() },
                    label = { Text("Clave pública opcional") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (contactName.isNotBlank()) {
                        onSave(contactName.trim(), publicKey.ifBlank { null })
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
