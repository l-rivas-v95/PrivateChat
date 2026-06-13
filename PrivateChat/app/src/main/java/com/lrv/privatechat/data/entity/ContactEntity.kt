package com.lrv.privatechat.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

const val CONTACT_STATUS_PENDING = "PENDING"
const val CONTACT_STATUS_ACCEPTED = "ACCEPTED"
const val CONTACT_STATUS_BLOCKED = "BLOCKED"

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val username: String,
    val displayName: String,
    val publicKey: String? = null,
    val avatarBase64: String? = null,
    val avatarUpdatedAt: Long? = null,
    val createdAt: Long,
    val lastSeenAt: Long? = null,
    val status: String = CONTACT_STATUS_ACCEPTED
)
