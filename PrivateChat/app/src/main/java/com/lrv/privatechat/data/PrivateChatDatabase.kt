package com.lrv.privatechat.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 5,
    exportSchema = false
)
abstract class PrivateChatDatabase : RoomDatabase() {

    abstract fun contactDao(): ContactDao
    abstract fun chatDao(): ChatDao
    abstract fun chatMessageDao(): ChatMessageDao

    companion object {
        @Volatile
        private var INSTANCE: PrivateChatDatabase? = null

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE contacts ADD COLUMN avatarBase64 TEXT")
                database.execSQL("ALTER TABLE contacts ADD COLUMN avatarUpdatedAt INTEGER")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE contacts ADD COLUMN status TEXT NOT NULL DEFAULT 'ACCEPTED'")
            }
        }

        fun getInstance(context: Context): PrivateChatDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PrivateChatDatabase::class.java,
                    "private_chat.db"
                )
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
                    .fallbackToDestructiveMigration(false)
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}
