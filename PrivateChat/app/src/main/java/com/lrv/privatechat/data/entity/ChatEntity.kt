package com.lrv.privatechat.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val contactUsername: String,
    val createdAt: Long,
    val updatedAt: Long,
    val lastMessagePreview: String,
    val unreadCount: Int = 0
)
