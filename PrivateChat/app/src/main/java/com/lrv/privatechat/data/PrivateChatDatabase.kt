package com.lrv.privatechat.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.lrv.privatechat.data.dao.ChatDao
import com.lrv.privatechat.data.dao.ChatMessageDao
import com.lrv.privatechat.data.dao.ContactDao
import com.lrv.privatechat.data.entity.ChatEntity
import com.lrv.privatechat.data.entity.ContactEntity
import com.lrv.privatechat.data.entity.MessageEntity

@Database(
    entities = [
        ContactEntity::class,
        ChatEntity::class,
        MessageEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class PrivateChatDatabase : RoomDatabase() {

    abstract fun contactDao(): ContactDao
    abstract fun chatDao(): ChatDao
    abstract fun chatMessageDao(): ChatMessageDao

    companion object {
        @Volatile
        private var INSTANCE: PrivateChatDatabase? = null

        fun getInstance(context: Context): PrivateChatDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PrivateChatDatabase::class.java,
                    "private_chat.db"
                ).build()

                INSTANCE = instance
                instance
            }
        }
    }
}
