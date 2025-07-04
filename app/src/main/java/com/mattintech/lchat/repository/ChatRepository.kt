package com.mattintech.lchat.repository

import android.content.Context
import com.mattintech.lchat.data.Message
import com.mattintech.lchat.network.WifiAwareManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class ChatRepository private constructor(context: Context) {
    
    companion object {
        @Volatile
        private var INSTANCE: ChatRepository? = null
        
        fun getInstance(context: Context): ChatRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ChatRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val wifiAwareManager = WifiAwareManager(context)
    
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()
    
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    private val _connectedUsers = MutableStateFlow<List<String>>(emptyList())
    val connectedUsers: StateFlow<List<String>> = _connectedUsers.asStateFlow()
    
    private var messageCallback: ((String, String, String) -> Unit)? = null
    private var connectionCallback: ((String, Boolean) -> Unit)? = null
    
    init {
        wifiAwareManager.initialize()
        setupWifiAwareCallbacks()
    }
    
    private fun setupWifiAwareCallbacks() {
        wifiAwareManager.setMessageCallback { userId, userName, content ->
            val message = Message(
                id = UUID.randomUUID().toString(),
                userId = userId,
                userName = userName,
                content = content,
                timestamp = System.currentTimeMillis(),
                isOwnMessage = false
            )
            addMessage(message)
            messageCallback?.invoke(userId, userName, content)
        }
    }
    
    fun startHostMode(roomName: String) {
        wifiAwareManager.startHostMode(roomName)
        _connectionState.value = ConnectionState.Hosting(roomName)
    }
    
    fun startClientMode() {
        wifiAwareManager.startClientMode()
        _connectionState.value = ConnectionState.Searching
    }
    
    fun sendMessage(userId: String, userName: String, content: String) {
        val message = Message(
            id = UUID.randomUUID().toString(),
            userId = userId,
            userName = userName,
            content = content,
            timestamp = System.currentTimeMillis(),
            isOwnMessage = true
        )
        addMessage(message)
        wifiAwareManager.sendMessage(userId, userName, content)
    }
    
    private fun addMessage(message: Message) {
        _messages.value = _messages.value + message
    }
    
    fun clearMessages() {
        _messages.value = emptyList()
    }
    
    fun setMessageCallback(callback: (String, String, String) -> Unit) {
        messageCallback = callback
    }
    
    fun setConnectionCallback(callback: (String, Boolean) -> Unit) {
        connectionCallback = callback
        wifiAwareManager.setConnectionCallback(callback)
    }
    
    fun stop() {
        wifiAwareManager.stop()
        _connectionState.value = ConnectionState.Disconnected
        _connectedUsers.value = emptyList()
    }
    
    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Searching : ConnectionState()
        data class Hosting(val roomName: String) : ConnectionState()
        data class Connected(val roomName: String) : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }
}