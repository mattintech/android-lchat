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
    // Exception handler for repository operations
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        android.util.Log.e("ChatRepository", "Repository coroutine exception: ", throwable)
        _connectionState.value = ConnectionState.Error(throwable.message ?: "Unknown error")
    }
    
    // Repository scope for background operations
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)
    
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
    
    // Keep-alive is now handled by WifiAwareManager
    
    init {
        wifiAwareManager.initialize()
        // Only use Flow-based collection, not callbacks
        collectWifiAwareFlows()
    }
    
    private fun collectWifiAwareFlows() {
        // Collect messages from Flow
        repositoryScope.launch {
            try {
                wifiAwareManager.messageFlow.collect { (userId, userName, content) ->
                val message = Message(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    userName = userName,
                    content = content,
                    timestamp = System.currentTimeMillis(),
                    isOwnMessage = false
                )
                addMessage(message)
                
                // If we're receiving messages, we must be connected
                if (_connectionState.value !is ConnectionState.Connected && 
                    _connectionState.value !is ConnectionState.Hosting) {
                    when (_connectionState.value) {
                        is ConnectionState.Hosting -> {} // Keep hosting state
                        else -> _connectionState.value = ConnectionState.Connected("Active")
                    }
                }
                }
            } catch (e: Exception) {
                android.util.Log.e("ChatRepository", "Error collecting message flow", e)
            }
        }
        
        // Collect connection state from Flow
        repositoryScope.launch {
            try {
                wifiAwareManager.connectionFlow.collect { (roomName, isConnected) ->
                    if (isConnected) {
                        currentRoomName = roomName
                        _connectionState.value = ConnectionState.Connected(roomName)
                        loadMessagesFromDatabase(roomName)
                    } else {
                        _connectionState.value = ConnectionState.Disconnected
                    }
                    // Call the legacy callback if set
                    connectionCallback?.invoke(roomName, isConnected)
                }
            } catch (e: Exception) {
                android.util.Log.e("ChatRepository", "Error collecting connection flow", e)
                _connectionState.value = ConnectionState.Error(e.message ?: "Connection error")
            }
        }
    }
    
    // Removed setupWifiAwareCallbacks - now using Flow-based collection only
    
    fun startHostMode(roomName: String) {
        currentRoomName = roomName
        // Ensure WifiAwareManager is initialized before starting
        wifiAwareManager.initialize()
        wifiAwareManager.startHostMode(roomName)
        _connectionState.value = ConnectionState.Hosting(roomName)
        loadMessagesFromDatabase(roomName)
    }
    
    private fun loadMessagesFromDatabase(roomName: String) {
        repositoryScope.launch {
            try {
                val storedMessages = messageDao.getMessagesForRoomOnce(roomName)
                    .map { it.toMessage() }
                _messages.value = storedMessages
            } catch (e: Exception) {
                android.util.Log.e("ChatRepository", "Error loading messages from database", e)
                // Don't crash, just continue with empty messages
                _messages.value = emptyList()
            }
        }
    }
    
    fun startClientMode() {
        // Reset state for fresh start
        _messages.value = emptyList()
        currentRoomName = ""
        
        // Ensure WifiAwareManager is initialized before starting
        repositoryScope.launch {
            // Give Wi-Fi Aware time to stabilize after network changes
            delay(500)
            wifiAwareManager.initialize()
            // Small delay to ensure initialization completes
            delay(100)
            wifiAwareManager.startClientMode()
            _connectionState.value = ConnectionState.Searching
        }
    }
    
    fun sendMessage(userId: String, userName: String, content: String) {
        // Only allow sending messages if connected or hosting
        when (_connectionState.value) {
            is ConnectionState.Connected, is ConnectionState.Hosting -> {
                val message = Message(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    userName = userName,
                    content = content,
                    timestamp = System.currentTimeMillis(),
                    isOwnMessage = true
                )
                val sent = wifiAwareManager.sendMessage(userId, userName, content)
                if (sent) {
                    addMessage(message)
                } else {
                    android.util.Log.e("ChatRepository", "Failed to send message - no active connection")
                    _connectionState.value = ConnectionState.Disconnected
                }
            }
            else -> {
                android.util.Log.w("ChatRepository", "Cannot send message - not connected. State: ${_connectionState.value}")
            }
        }
    }
    
    private fun addMessage(message: Message) {
        // Add message and sort by timestamp to ensure proper order
        _messages.value = (_messages.value + message).sortedBy { it.timestamp }
        
        // Save to database
        repositoryScope.launch {
            try {
                messageDao.insertMessage(message.toEntity(currentRoomName))
            } catch (e: Exception) {
                android.util.Log.e("ChatRepository", "Error saving message to database", e)
                // Don't crash, message is already in memory
            }
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
        // Connection state is now handled by Flow collection
    }
    
    fun stop() {
        wifiAwareManager.stop()
        _connectionState.value = ConnectionState.Disconnected
        _connectedUsers.value = emptyList()
        // Don't cancel the repository scope - we need it for future operations
        // Clear messages when stopping
        _messages.value = emptyList()
        currentRoomName = ""
    }
    
    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Searching : ConnectionState()
        data class Hosting(val roomName: String) : ConnectionState()
        data class Connected(val roomName: String) : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }
}