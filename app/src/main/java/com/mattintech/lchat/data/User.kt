package com.mattintech.lchat.data

data class User(
    val id: String,
    val name: String,
    val isHost: Boolean = false,
    val lastSeen: Long = System.currentTimeMillis()
)