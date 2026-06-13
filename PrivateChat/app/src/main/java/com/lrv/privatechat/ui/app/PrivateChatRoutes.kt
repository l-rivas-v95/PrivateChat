package com.lrv.privatechat.ui.app

sealed class PrivateChatRoutes(val route: String) {
    data object Chats : PrivateChatRoutes("chats")
    data object Profile : PrivateChatRoutes("profile")
    data object ScanQr : PrivateChatRoutes("scan_qr")

    data object ChatDetail : PrivateChatRoutes("chat/{contact}") {
        const val ARG_CONTACT = "contact"
    }

    companion object {
        fun chatDetail(contact: String): String = "chat/$contact"
    }
}
