package com.lrv.privatechat.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val chatId: Long,
    val senderUsername: String,
    val receiverUsername: String,
    val body: String,
    val timestamp: Long,
    val isMine: Boolean,
    val deliveryStatus: String
)
