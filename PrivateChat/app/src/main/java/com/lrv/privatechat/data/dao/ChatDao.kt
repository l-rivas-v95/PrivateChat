package com.lrv.privatechat.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lrv.privatechat.data.entity.ChatEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    @Query("SELECT * FROM chats ORDER BY updatedAt DESC")
    fun observeChats(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats ORDER BY updatedAt DESC")
    suspend fun getAllChatsOnce(): List<ChatEntity>

    @Query("SELECT * FROM chats WHERE contactUsername = :contactUsername LIMIT 1")
    suspend fun findByContact(contactUsername: String): ChatEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(chat: ChatEntity): Long
}
