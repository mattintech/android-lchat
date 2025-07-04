package com.mattintech.lchat.data

data class Message(
    val id: String,
    val userId: String,
    val userName: String,
    val content: String,
    val timestamp: Long,
    val isOwnMessage: Boolean = false
)