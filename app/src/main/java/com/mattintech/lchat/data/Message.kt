package com.mattintech.lchat.data

data class Message(
    val id: String,
    val senderId: String,
    val senderName: String,
    val content: String,
    val timestamp: Long,
    val isLocal: Boolean = false
)