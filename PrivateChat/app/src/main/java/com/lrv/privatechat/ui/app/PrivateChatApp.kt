package com.lrv.privatechat.ui.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
                messages = uiState.messages,
                onToggleConnection = viewModel::toggleConnection,
                onNewChat = viewModel::showNewChatDialog,
                onOpenChat = { contact -> navController.navigate(PrivateChatRoutes.chatDetail(contact)) },
                onDeleteConversation = viewModel::deleteConversation,
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
                onDeleteMessage = viewModel::deleteMessage,
                onSend = { text -> viewModel.sendMessage(contact, text) }
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
