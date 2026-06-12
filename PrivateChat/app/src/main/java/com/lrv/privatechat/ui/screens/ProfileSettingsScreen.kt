package com.lrv.privatechat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lrv.privatechat.model.AppColor
import com.lrv.privatechat.ui.components.AvatarView

@Composable
fun ProfileSettingsScreen(
    userId: String,
    displayName: String,
    publicKey: String,
    avatarBase64: String?,
    appColor: AppColor,
    onDisplayNameChange: (String) -> Unit,
    onAvatarClick: () -> Unit,
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
                        text = "Mi identidad",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AvatarView(
                            displayName = displayName,
                            avatarBase64 = avatarBase64,
                            appColor = appColor,
                            size = 72.dp
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Foto de perfil",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Se enviará a tus contactos.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF666666)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = onAvatarClick,
                                colors = ButtonDefaults.buttonColors(containerColor = appColor.main)
                            ) {
                                Text("Cambiar foto", color = Color.White)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = displayName,
                        onValueChange = onDisplayNameChange,
                        label = { Text("Nombre visible") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Mi ID fijo",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        text = userId,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF555555)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Mi clave pública",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        text = publicKey,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF555555),
                        maxLines = 5
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Surface(
                        color = appColor.light,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "QR",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = appColor.main
                                )
                                Text(
                                    text = "Pendiente: ID + clave pública",
                                    color = appColor.main
                                )
                            }
                        }
                    }
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
