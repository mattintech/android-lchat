package com.mattintech.lchat.repository

import android.content.Context
import com.mattintech.lchat.data.Message
import com.mattintech.lchat.data.db.dao.MessageDao
import com.mattintech.lchat.data.db.mappers.toEntity
import com.mattintech.lchat.data.db.mappers.toMessage
import com.mattintech.lchat.network.WifiAwareManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val wifiAwareManager: WifiAwareManager,
    private val messageDao: MessageDao
) {
    
    private var currentRoomName: String = ""
    
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()
    
    // Flow that combines in-memory and database messages
    fun getMessagesFlow(roomName: String): Flow<List<Message>> {
        return messageDao.getMessagesForRoom(roomName)
            .map { entities -> entities.map { it.toMessage() } }
            .onStart { loadMessagesFromDatabase(roomName) }
    }
    
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    private val _connectedUsers = MutableStateFlow<List<String>>(emptyList())
    val connectedUsers: StateFlow<List<String>> = _connectedUsers.asStateFlow()
    
    private var messageCallback: ((String, String, String) -> Unit)? = null
    private var connectionCallback: ((String, Boolean) -> Unit)? = null
    
    private var lastActivityTime = System.currentTimeMillis()
    private var connectionCheckJob: Job? = null
    private val connectionTimeout = 30000L // 30 seconds
    
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
            
            // Update last activity time
            lastActivityTime = System.currentTimeMillis()
            
            // If we're receiving messages, we must be connected
            if (_connectionState.value !is ConnectionState.Connected && 
                _connectionState.value !is ConnectionState.Hosting) {
                when (_connectionState.value) {
                    is ConnectionState.Hosting -> {} // Keep hosting state
                    else -> _connectionState.value = ConnectionState.Connected("Active")
                }
            }
            
            messageCallback?.invoke(userId, userName, content)
        }
    }
    
    fun startHostMode(roomName: String) {
        currentRoomName = roomName
        wifiAwareManager.startHostMode(roomName)
        _connectionState.value = ConnectionState.Hosting(roomName)
        startConnectionMonitoring()
        loadMessagesFromDatabase(roomName)
    }
    
    private fun loadMessagesFromDatabase(roomName: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val storedMessages = messageDao.getMessagesForRoomOnce(roomName)
                .map { it.toMessage() }
            _messages.value = storedMessages
        }
    }
    
    fun startClientMode() {
        wifiAwareManager.startClientMode()
        _connectionState.value = ConnectionState.Searching
        startConnectionMonitoring()
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
        
        // Update last activity time
        lastActivityTime = System.currentTimeMillis()
        
        // If we can send messages, update connection state if needed
        if (_connectionState.value is ConnectionState.Disconnected || 
            _connectionState.value is ConnectionState.Error) {
            _connectionState.value = ConnectionState.Connected("Active")
        }
    }
    
    private fun addMessage(message: Message) {
        _messages.value = _messages.value + message
        
        // Save to database
        CoroutineScope(Dispatchers.IO).launch {
            messageDao.insertMessage(message.toEntity(currentRoomName))
        }
    }
    
    fun clearMessages() {
        _messages.value = emptyList()
    }
    
    fun setMessageCallback(callback: (String, String, String) -> Unit) {
        messageCallback = callback
    }
    
    fun setConnectionCallback(callback: (String, Boolean) -> Unit) {
        connectionCallback = callback
        wifiAwareManager.setConnectionCallback { roomName, isConnected ->
            if (isConnected) {
                currentRoomName = roomName
                _connectionState.value = ConnectionState.Connected(roomName)
                loadMessagesFromDatabase(roomName)
            } else {
                _connectionState.value = ConnectionState.Disconnected
            }
            callback(roomName, isConnected)
        }
    }
    
    fun stop() {
        stopConnectionMonitoring()
        wifiAwareManager.stop()
        _connectionState.value = ConnectionState.Disconnected
        _connectedUsers.value = emptyList()
    }
    
    private fun startConnectionMonitoring() {
        connectionCheckJob?.cancel()
        connectionCheckJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                delay(5000) // Check every 5 seconds
                val timeSinceLastActivity = System.currentTimeMillis() - lastActivityTime
                
                // If no activity for 30 seconds and we think we're connected, mark as disconnected
                if (timeSinceLastActivity > connectionTimeout) {
                    when (_connectionState.value) {
                        is ConnectionState.Connected,
                        is ConnectionState.Hosting -> {
                            _connectionState.value = ConnectionState.Disconnected
                        }
                        else -> {} // Keep current state
                    }
                }
            }
        }
    }
    
    private fun stopConnectionMonitoring() {
        connectionCheckJob?.cancel()
        connectionCheckJob = null
    }
    
    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Searching : ConnectionState()
        data class Hosting(val roomName: String) : ConnectionState()
        data class Connected(val roomName: String) : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }
}