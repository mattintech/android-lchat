package com.mattintech.lchat.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.mattintech.lchat.data.db.dao.MessageDao
import com.mattintech.lchat.data.db.entities.MessageEntity

@Database(
    entities = [MessageEntity::class],
    version = 1,
    exportSchema = false
)
abstract class LChatDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
}