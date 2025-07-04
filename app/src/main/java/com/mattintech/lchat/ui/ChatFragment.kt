package com.mattintech.lchat.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.mattintech.lchat.databinding.FragmentChatBinding
import com.mattintech.lchat.ui.adapters.MessageAdapter
import com.mattintech.lchat.utils.LOG_PREFIX
import com.mattintech.lchat.viewmodel.ChatViewModel
import com.mattintech.lchat.viewmodel.ViewModelFactory
import kotlinx.coroutines.launch

class ChatFragment : Fragment() {
    
    companion object {
        private const val TAG = LOG_PREFIX + "ChatFragment:"
    }
    
    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!
    
    private val args: ChatFragmentArgs by navArgs()
    private lateinit var viewModel: ChatViewModel
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
        
        val factory = ViewModelFactory(requireContext())
        viewModel = ViewModelProvider(this, factory)[ChatViewModel::class.java]
        viewModel.initialize(args.roomName, args.userName, args.isHost)
        
        setupUI()
        observeViewModel()
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
                // Handle connection state changes if needed
            }
        }
    }
    
    private fun sendMessage() {
        val content = binding.messageInput.text?.toString()?.trim()
        if (content.isNullOrEmpty()) return
        
        viewModel.sendMessage(content)
        binding.messageInput.text?.clear()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "onDestroyView")
        _binding = null
    }
}