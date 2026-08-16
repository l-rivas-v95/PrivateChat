package com.lrv.privatechat.ui.app

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lrv.privatechat.model.UNKNOWN_CONTACT_NAME
import com.lrv.privatechat.presentation.PrivateChatViewModel
import com.lrv.privatechat.ui.dialogs.NewChatDialog
import com.lrv.privatechat.ui.screens.ChatDetailScreen
import com.lrv.privatechat.ui.screens.ChatsScreen
import com.lrv.privatechat.ui.screens.ProfileSettingsScreen
import com.lrv.privatechat.ui.screens.QrScannerScreen
import java.io.File

@Composable
fun PrivateChatApp(viewModel: PrivateChatViewModel = viewModel()) {
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsState()

    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) viewModel.updateAvatar(uri)
    }

    if (uiState.showNewChatDialog) {
        NewChatDialog(
            appColor = uiState.selectedColor,
            scannedQrContent = uiState.scannedQrContent,
            onDismiss = viewModel::hideNewChatDialog,
            onScanQr = {
                viewModel.startQrScan()
                navController.navigate(PrivateChatRoutes.ScanQr.route)
            },
            onSaveManual = viewModel::saveManualContact
        )
    }

    NavHost(navController = navController, startDestination = PrivateChatRoutes.Chats.route) {
        composable(PrivateChatRoutes.Chats.route) {
            ChatsScreen(
                userId = uiState.connectedUserId,
                displayName = uiState.displayName,
                status = uiState.status,
                isConnected = uiState.isConnected,
                appColor = uiState.selectedColor,
                contacts = uiState.visibleContacts,
                pendingContacts = uiState.pendingContacts,
                messages = uiState.messages,
                unreadCounts = uiState.unreadCounts,
                onToggleConnection = viewModel::toggleConnection,
                onNewChat = viewModel::showNewChatDialog,
                onOpenChat = { contact -> navController.navigate(PrivateChatRoutes.chatDetail(contact)) },
                onDeleteConversation = viewModel::deleteConversation,
                onAcceptContact = viewModel::acceptPendingContact,
                onOpenProfile = { navController.navigate(PrivateChatRoutes.Profile.route) }
            )
        }

        composable(
            route = PrivateChatRoutes.ChatDetail.route,
            arguments = listOf(navArgument(PrivateChatRoutes.ChatDetail.ARG_CONTACT) { type = NavType.StringType })
        ) { backStackEntry ->
            val contact = backStackEntry.arguments?.getString(PrivateChatRoutes.ChatDetail.ARG_CONTACT) ?: ""
            val storedContact = uiState.contacts.firstOrNull { it.username == contact }
            val rawContactName = storedContact?.displayName ?: UNKNOWN_CONTACT_NAME
            val isUnknownContact = rawContactName == UNKNOWN_CONTACT_NAME || rawContactName == contact
            val contactName = if (isUnknownContact) UNKNOWN_CONTACT_NAME else rawContactName
            val isContactAccepted = storedContact?.status == "ACCEPTED"
            val context = LocalContext.current

            // Galería
            val imagePicker = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.GetContent()
            ) { uri -> if (uri != null) viewModel.sendMediaMessage(contact, uri) }

            // Cámara
            var photoUri by remember { mutableStateOf<Uri?>(null) }
            val cameraLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.TakePicture()
            ) { success -> if (success) photoUri?.let { viewModel.sendMediaMessage(contact, it) } }

            // Grabación de audio
            var isRecording by remember { mutableStateOf(false) }
            var recordingSeconds by remember { mutableStateOf(0) }
            var mediaRecorder by remember { mutableStateOf<android.media.MediaRecorder?>(null) }
            var audioFile by remember { mutableStateOf<File?>(null) }
            var pendingRecord by remember { mutableStateOf(false) }

            val micPermission = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted -> if (granted) pendingRecord = true }

            LaunchedEffect(pendingRecord) {
                if (pendingRecord) {
                    pendingRecord = false
                    // el onClick ya gestiona el inicio; marcamos para que se reintente
                }
            }

            LaunchedEffect(isRecording) {
                if (isRecording) {
                    recordingSeconds = 0
                    while (isRecording) {
                        kotlinx.coroutines.delay(1000)
                        recordingSeconds++
                    }
                }
            }

            LaunchedEffect(contact) {
                viewModel.markChatAsRead(contact)
            }

            ChatDetailScreen(
                username = uiState.connectedUserId,
                contact = contact,
                contactName = contactName,
                contactAvatarBase64 = storedContact?.avatarBase64,
                isUnknownContact = isUnknownContact,
                appColor = uiState.selectedColor,
                messages = uiState.messages.filter {
                    (it.from == uiState.connectedUserId && it.to == contact) ||
                        (it.from == contact && it.to == uiState.connectedUserId)
                },
                onBack = { navController.popBackStack() },
                onSaveContact = { newName, publicKey -> viewModel.saveContact(contact, newName, publicKey) },
                onClearChat = { viewModel.clearChatMessages(contact) },
                onDeleteContact = {
                    viewModel.deleteConversation(contact)
                    navController.popBackStack()
                },
                onDeleteMessage = viewModel::deleteMessage,
                onSend = { text -> viewModel.sendMessage(contact, text) },
                onAttachImage = { imagePicker.launch("*/*") },
                onTakePhoto = {
                    val tmpFile = File.createTempFile("photo_", ".jpg", context.cacheDir)
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tmpFile)
                    photoUri = uri
                    cameraLauncher.launch(uri)
                },
                onMicClick = {
                    if (!isRecording) {
                        // Verificar permiso antes de grabar
                        val granted = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.RECORD_AUDIO
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                        if (!granted) {
                            micPermission.launch(Manifest.permission.RECORD_AUDIO)
                        } else {
                            // Iniciar grabación
                            val file = File(context.cacheDir, "audio_${System.currentTimeMillis()}.m4a")
                            audioFile = file
                            @Suppress("DEPRECATION")
                            val recorder = android.media.MediaRecorder().apply {
                                setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
                                setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4)
                                setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC)
                                setOutputFile(file.absolutePath)
                                prepare()
                                start()
                            }
                            mediaRecorder = recorder
                            isRecording = true
                        }
                    } else {
                        // Parar y enviar
                        try {
                            mediaRecorder?.stop()
                            mediaRecorder?.release()
                        } catch (_: Exception) {}
                        mediaRecorder = null
                        isRecording = false
                        audioFile?.let { file ->
                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                context, "${context.packageName}.fileprovider", file
                            )
                            viewModel.sendMediaMessage(contact, uri, "audio/mp4")
                        }
                        audioFile = null
                    }
                },
                onCancelRecording = {
                    try {
                        mediaRecorder?.stop()
                        mediaRecorder?.release()
                    } catch (_: Exception) {}
                    mediaRecorder = null
                    isRecording = false
                    audioFile?.delete()
                    audioFile = null
                },
                isRecording = isRecording,
                recordingSeconds = recordingSeconds,
                isContactAccepted = isContactAccepted
            )
        }

        composable(PrivateChatRoutes.Profile.route) {
            ProfileSettingsScreen(
                userId = uiState.localUserId,
                displayName = uiState.displayName,
                publicKey = uiState.localPublicKey,
                avatarBase64 = uiState.localAvatarBase64,
                appColor = uiState.selectedColor,
                onDisplayNameChange = viewModel::updateDisplayName,
                onAvatarClick = { avatarPicker.launch("image/*") },
                onColorChange = viewModel::updateColor,
                onBack = { navController.popBackStack() }
            )
        }

        composable(PrivateChatRoutes.ScanQr.route) {
            QrScannerScreen(
                appColor = uiState.selectedColor,
                onQrScanned = { qrContent ->
                    viewModel.onQrScanned(qrContent)
                    navController.popBackStack()
                },
                onBack = {
                    viewModel.reopenNewChatDialog()
                    navController.popBackStack()
                }
            )
        }
    }
}
