package com.mattintech.lchat.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mattintech.lchat.repository.ChatRepository
import com.mattintech.lchat.utils.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class LobbyState {
    object Idle : LobbyState()
    object Connecting : LobbyState()
    data class Connected(val roomName: String) : LobbyState()
    data class Error(val message: String) : LobbyState()
}

sealed class LobbyEvent {
    data class NavigateToChat(
        val roomName: String,
        val userName: String,
        val isHost: Boolean
    ) : LobbyEvent()
    data class ShowError(val message: String) : LobbyEvent()
}

@HiltViewModel
class LobbyViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {
    
    private val _state = MutableStateFlow<LobbyState>(LobbyState.Idle)
    val state: StateFlow<LobbyState> = _state.asStateFlow()
    
    private val _events = MutableSharedFlow<LobbyEvent>()
    val events: SharedFlow<LobbyEvent> = _events.asSharedFlow()
    
    private val _savedUserName = MutableStateFlow<String?>(null)
    val savedUserName: StateFlow<String?> = _savedUserName.asStateFlow()
    
    init {
        setupConnectionCallback()
        loadSavedUserName()
    }
    
    private fun loadSavedUserName() {
        _savedUserName.value = preferencesManager.getUserName()
    }
    
    private fun setupConnectionCallback() {
        chatRepository.setConnectionCallback { roomName, isConnected ->
            viewModelScope.launch {
                if (isConnected) {
                    _state.value = LobbyState.Connected(roomName)
                } else {
                    _state.value = LobbyState.Error("Failed to connect to $roomName. Ensure Wi-Fi is enabled on both devices.")
                }
            }
        }
    }
    
    fun startHostMode(roomName: String, userName: String) {
        if (roomName.isBlank()) {
            viewModelScope.launch {
                _events.emit(LobbyEvent.ShowError("Please enter a room name"))
            }
            return
        }
        
        if (userName.isBlank()) {
            viewModelScope.launch {
                _events.emit(LobbyEvent.ShowError("Please enter your name"))
            }
            return
        }
        
        viewModelScope.launch {
            _state.value = LobbyState.Connecting
            preferencesManager.saveUserName(userName)
            chatRepository.startHostMode(roomName)
            _events.emit(LobbyEvent.NavigateToChat(roomName, userName, true))
        }
    }
    
    fun startClientMode(userName: String) {
        if (userName.isBlank()) {
            viewModelScope.launch {
                _events.emit(LobbyEvent.ShowError("Please enter your name"))
            }
            return
        }
        
        viewModelScope.launch {
            _state.value = LobbyState.Connecting
            preferencesManager.saveUserName(userName)
            chatRepository.startClientMode()
        }
    }
    
    fun onConnectedToRoom(roomName: String, userName: String) {
        preferencesManager.saveUserName(userName)
        viewModelScope.launch {
            _events.emit(LobbyEvent.NavigateToChat(roomName, userName, false))
        }
    }
    
    fun clearEvent() {
        // SharedFlow doesn't need clearing, events are consumed once
    }
    
    fun saveUserName(name: String) {
        if (name.isNotBlank()) {
            preferencesManager.saveUserName(name)
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        // Clean up resources if needed
    }
}