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

    @Query("SELECT COUNT(*) FROM chat_messages WHERE chatId = :chatId AND isMine = 0 AND isRead = 0")
    suspend fun countUnreadIncomingMessages(chatId: Long): Int

    @Query("UPDATE chat_messages SET isRead = 1 WHERE chatId = :chatId AND isMine = 0")
    suspend fun markIncomingMessagesAsRead(chatId: Long)

    @Query("UPDATE chat_messages SET deliveryStatus = :status WHERE messageId = :messageId")
    suspend fun updateDeliveryStatusByMessageId(messageId: String, status: String)

    @Query("DELETE FROM chat_messages WHERE chatId = :chatId")
    suspend fun deleteMessagesByChatId(chatId: Long)

    @Query("DELETE FROM chat_messages WHERE messageId = :messageId")
    suspend fun deleteMessageByMessageId(messageId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveChatMessage(entity: MessageEntity): Long
}
