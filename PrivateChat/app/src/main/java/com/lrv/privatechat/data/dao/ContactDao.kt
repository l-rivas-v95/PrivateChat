package com.lrv.privatechat.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lrv.privatechat.data.entity.ContactEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {

    @Query("SELECT * FROM contacts ORDER BY displayName ASC")
    fun observeContacts(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts ORDER BY displayName ASC")
    suspend fun getContactsOnce(): List<ContactEntity>

    @Query("SELECT * FROM contacts WHERE username = :username LIMIT 1")
    suspend fun findByUsername(username: String): ContactEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(contact: ContactEntity)
}
