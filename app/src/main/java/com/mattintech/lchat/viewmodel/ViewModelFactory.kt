package com.mattintech.lchat.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.mattintech.lchat.repository.ChatRepository

class ViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    
    private val chatRepository by lazy { ChatRepository.getInstance(context) }
    
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(LobbyViewModel::class.java) -> {
                LobbyViewModel(chatRepository) as T
            }
            modelClass.isAssignableFrom(ChatViewModel::class.java) -> {
                ChatViewModel(chatRepository) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}