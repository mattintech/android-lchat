package com.mattintech.lchat.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mattintech.lchat.data.Message
import com.mattintech.lchat.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

sealed class ChatState {
    object Connected : ChatState()
    object Disconnected : ChatState()
    data class Error(val message: String) : ChatState()
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository
) : ViewModel() {
    
    private val _state = MutableStateFlow<ChatState>(ChatState.Connected)
    val state: StateFlow<ChatState> = _state.asStateFlow()
    
    private val _messagesFlow = MutableStateFlow<Flow<List<Message>>>(flowOf(emptyList()))
    
    val messages: StateFlow<List<Message>> = _messagesFlow
        .flatMapLatest { it }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    val connectionState = chatRepository.connectionState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ChatRepository.ConnectionState.Disconnected
        )
    
    val connectedUsers = chatRepository.connectedUsers
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    private var currentUserId: String = ""
    private var currentUserName: String = ""
    private var currentRoomName: String = ""
    private var isHost: Boolean = false
    
    fun initialize(roomName: String, userName: String, isHost: Boolean) {
        this.currentRoomName = roomName
        this.currentUserName = userName
        this.isHost = isHost
        this.currentUserId = UUID.randomUUID().toString()
        
        // Set up messages flow for this room
        _messagesFlow.value = chatRepository.getMessagesFlow(roomName)
        
        // Setup message callback if needed for additional processing
        chatRepository.setMessageCallback { userId, userName, content ->
            // Can add additional message processing here if needed
        }
    }
    
    fun sendMessage(content: String) {
        if (content.isBlank()) return
        
        viewModelScope.launch {
            chatRepository.sendMessage(currentUserId, currentUserName, content)
        }
    }
    
    fun getRoomInfo(): Triple<String, String, Boolean> {
        return Triple(currentRoomName, currentUserName, isHost)
    }
    
    fun disconnect() {
        chatRepository.stop()
        _state.value = ChatState.Disconnected
    }
    
    override fun onCleared() {
        super.onCleared()
        disconnect()
    }
}