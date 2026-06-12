package com.lrv.privatechat.model

data class ChatItemUiModel(
    val username: String,
    val displayName: String,
    val lastMessage: String,
    val timeText: String,
    val avatarBase64: String? = null
)
