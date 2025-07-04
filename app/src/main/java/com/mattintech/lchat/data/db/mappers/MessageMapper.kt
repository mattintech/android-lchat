package com.mattintech.lchat.data.db.mappers

import com.mattintech.lchat.data.Message
import com.mattintech.lchat.data.db.entities.MessageEntity

fun MessageEntity.toMessage(): Message {
    return Message(
        id = id,
        userId = userId,
        userName = userName,
        content = content,
        timestamp = timestamp,
        isOwnMessage = isOwnMessage
    )
}

fun Message.toEntity(roomName: String): MessageEntity {
    return MessageEntity(
        id = id,
        roomName = roomName,
        userId = userId,
        userName = userName,
        content = content,
        timestamp = timestamp,
        isOwnMessage = isOwnMessage
    )
}