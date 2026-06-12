package com.lrv.privatechat.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val username: String,
    val displayName: String,
    val publicKey: String? = null,
    val createdAt: Long,
    val lastSeenAt: Long? = null
)
