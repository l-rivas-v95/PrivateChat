package com.lrv.privatechat.presentation

import com.lrv.privatechat.data.entity.CONTACT_STATUS_ACCEPTED
import com.lrv.privatechat.data.entity.CONTACT_STATUS_PENDING
import com.lrv.privatechat.data.entity.ContactEntity
import com.lrv.privatechat.model.AppColor
import com.lrv.privatechat.model.UiMessage

data class PrivateChatUiState(
    val localUserId: String = "",
    val connectedUserId: String = "",
    val displayName: String = "User",
    val localPublicKey: String = "",
    val localAvatarBase64: String? = null,
    val selectedColor: AppColor = AppColor.GREEN,
    val status: String = "Desconectado",
    val messages: List<UiMessage> = emptyList(),
    val contacts: List<ContactEntity> = emptyList(),
    val unreadCounts: Map<String, Int> = emptyMap(),
    val showNewChatDialog: Boolean = false,
    val scannedQrContent: String? = null
) {
    val isConnected: Boolean
        get() = status == "Conectado"

    val visibleContacts: List<ContactEntity>
        get() = contacts
            .filter { it.username != connectedUserId }
            .filter { it.status == CONTACT_STATUS_ACCEPTED }
            .distinctBy { it.username }

    val pendingContacts: List<ContactEntity>
        get() = contacts
            .filter { it.username != connectedUserId }
            .filter { it.status == CONTACT_STATUS_PENDING }
            .distinctBy { it.username }
}
