package com.mattintech.lchat.data.db.dao

import androidx.room.*
import com.mattintech.lchat.data.db.entities.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE roomName = :roomName ORDER BY timestamp ASC")
    fun getMessagesForRoom(roomName: String): Flow<List<MessageEntity>>
    
    @Query("SELECT * FROM messages WHERE roomName = :roomName ORDER BY timestamp ASC")
    suspend fun getMessagesForRoomOnce(roomName: String): List<MessageEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)
    
    @Query("DELETE FROM messages WHERE roomName = :roomName")
    suspend fun deleteMessagesForRoom(roomName: String)
    
    @Query("DELETE FROM messages")
    suspend fun deleteAllMessages()
    
    @Query("SELECT COUNT(*) FROM messages WHERE roomName = :roomName")
    suspend fun getMessageCountForRoom(roomName: String): Int
}