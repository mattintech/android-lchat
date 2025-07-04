package com.mattintech.lchat.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.mattintech.lchat.R
import com.mattintech.lchat.databinding.FragmentChatBinding
import com.mattintech.lchat.repository.ChatRepository
import com.mattintech.lchat.ui.adapters.MessageAdapter
import com.mattintech.lchat.utils.LOG_PREFIX
import com.mattintech.lchat.viewmodel.ChatViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ChatFragment : Fragment() {
    
    companion object {
        private const val TAG = LOG_PREFIX + "ChatFragment:"
    }
    
    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!
    
    private val args: ChatFragmentArgs by navArgs()
    private val viewModel: ChatViewModel by viewModels()
    private lateinit var messageAdapter: MessageAdapter
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d(TAG, "onCreateView")
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated - room: ${args.roomName}, user: ${args.userName}, isHost: ${args.isHost}")
        
        viewModel.initialize(args.roomName, args.userName, args.isHost)
        
        setupUI()
        observeViewModel()
        updateRoomInfo()
    }
    
    private fun setupUI() {
        messageAdapter = MessageAdapter()
        binding.messagesRecyclerView.apply {
            adapter = messageAdapter
            layoutManager = LinearLayoutManager(context).apply {
                stackFromEnd = true
            }
        }
        
        binding.sendButton.setOnClickListener {
            sendMessage()
        }
        
        binding.messageInput.setOnEditorActionListener { _, _, _ ->
            sendMessage()
            true
        }
    }
    
    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.messages.collect { messages ->
                messageAdapter.submitList(messages)
                if (messages.isNotEmpty()) {
                    binding.messagesRecyclerView.smoothScrollToPosition(messages.size - 1)
                }
            }
        }
        
        lifecycleScope.launch {
            viewModel.connectionState.collect { state ->
                Log.d(TAG, "Connection state: $state")
                updateConnectionStatus(state)
            }
        }
    }
    
    private fun sendMessage() {
        val content = binding.messageInput.text?.toString()?.trim()
        if (content.isNullOrEmpty()) return
        
        viewModel.sendMessage(content)
        binding.messageInput.text?.clear()
    }
    
    private fun updateRoomInfo() {
        val (roomName, _, isHost) = viewModel.getRoomInfo()
        binding.roomNameText.text = if (isHost) "Hosting: $roomName" else "Room: $roomName"
    }
    
    private fun updateConnectionStatus(state: ChatRepository.ConnectionState) {
        when (state) {
            is ChatRepository.ConnectionState.Disconnected -> {
                binding.connectionStatusText.text = "Disconnected"
                binding.connectionIndicator.backgroundTintList = 
                    ContextCompat.getColorStateList(requireContext(), R.color.disconnected_color)
            }
            is ChatRepository.ConnectionState.Searching -> {
                binding.connectionStatusText.text = "Searching..."
                binding.connectionIndicator.backgroundTintList = 
                    ContextCompat.getColorStateList(requireContext(), R.color.connecting_color)
            }
            is ChatRepository.ConnectionState.Hosting -> {
                binding.connectionStatusText.text = "Hosting"
                binding.connectionIndicator.backgroundTintList = 
                    ContextCompat.getColorStateList(requireContext(), R.color.hosting_color)
            }
            is ChatRepository.ConnectionState.Connected -> {
                binding.connectionStatusText.text = "Connected"
                binding.connectionIndicator.backgroundTintList = 
                    ContextCompat.getColorStateList(requireContext(), R.color.connected_color)
            }
            is ChatRepository.ConnectionState.Error -> {
                binding.connectionStatusText.text = "Error: ${state.message}"
                binding.connectionIndicator.backgroundTintList = 
                    ContextCompat.getColorStateList(requireContext(), R.color.disconnected_color)
            }
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "onDestroyView")
        // Disconnect when leaving the chat screen
        viewModel.disconnect()
        _binding = null
    }
}