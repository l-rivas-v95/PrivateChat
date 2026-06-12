package com.lrv.privatechat.data

data class ChatMessage(
    val from: String,
    val to: String,
    val text: String
)