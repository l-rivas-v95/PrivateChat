package com.lrv.privatechat.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lrv.privatechat.data.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {

    @Query("SELECT * FROM chat_messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    fun observeChatMessages(chatId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    suspend fun getChatMessagesOnce(chatId: Long): List<MessageEntity>

    @Query("SELECT * FROM chat_messages WHERE deliveryStatus = 'PENDING' ORDER BY timestamp ASC")
    suspend fun getPendingMessagesOnce(): List<MessageEntity>

    @Query("SELECT * FROM chat_messages WHERE isMine = 1 AND deliveryStatus = 'SENT' ORDER BY timestamp DESC")
    suspend fun getPendingCandidateMessagesOnce(): List<MessageEntity>

    @Query("UPDATE chat_messages SET deliveryStatus = :status WHERE id = :id")
    suspend fun updateDeliveryStatus(id: Long, status: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveChatMessage(entity: MessageEntity): Long
}
