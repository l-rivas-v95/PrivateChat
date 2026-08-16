package com.lrv.privatechat.model

import java.util.UUID

data class UiMessage(
    val id: String = UUID.randomUUID().toString(),
    val from: String,
    val to: String,
    val text: String,
    val mine: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = if (mine) MESSAGE_STATUS_SENT else MESSAGE_STATUS_RECEIVED,
    // Para mensajes con fichero adjunto
    val mediaLocalPath: String? = null,
    val mimeType: String? = null
) {
    val isMedia: Boolean get() = mediaLocalPath != null
}
