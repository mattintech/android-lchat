package com.mattintech.lchat.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey
    val id: String,
    val roomName: String,
    val userId: String,
    val userName: String,
    val content: String,
    val timestamp: Long,
    val isOwnMessage: Boolean = false
)