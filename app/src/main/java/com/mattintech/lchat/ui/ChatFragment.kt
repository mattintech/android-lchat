package com.mattintech.lchat.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.mattintech.lchat.data.Message
import com.mattintech.lchat.databinding.FragmentChatBinding
import com.mattintech.lchat.network.WifiAwareManager
import com.mattintech.lchat.network.WifiAwareManagerSingleton
import com.mattintech.lchat.ui.adapters.MessageAdapter
import com.mattintech.lchat.utils.LOG_PREFIX
import java.util.UUID

class ChatFragment : Fragment() {
    
    companion object {
        private const val TAG = LOG_PREFIX + "ChatFragment:"
    }
    
    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!
    
    private val args: ChatFragmentArgs by navArgs()
    private lateinit var wifiAwareManager: WifiAwareManager
    private lateinit var messageAdapter: MessageAdapter
    private val messages = mutableListOf<Message>()
    private val userId = UUID.randomUUID().toString()
    
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
        
        setupUI()
        setupWifiAware()
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
    
    private fun setupWifiAware() {
        wifiAwareManager = WifiAwareManagerSingleton.getInstance(requireContext())
        
        wifiAwareManager.setMessageCallback { senderId, senderName, content ->
            Log.d(TAG, "Message received - from: $senderName, content: $content")
            val message = Message(
                id = UUID.randomUUID().toString(),
                senderId = senderId,
                senderName = senderName,
                content = content,
                timestamp = System.currentTimeMillis(),
                isLocal = senderId == userId
            )
            
            activity?.runOnUiThread {
                messages.add(message)
                messageAdapter.submitList(messages.toList())
                binding.messagesRecyclerView.smoothScrollToPosition(messages.size - 1)
            }
        }
        
        // No need to start host mode here - already started in LobbyFragment
        Log.d(TAG, "Chat setup complete - isHost: ${args.isHost}, room: ${args.roomName}")
    }
    
    private fun sendMessage() {
        val content = binding.messageInput.text?.toString()?.trim()
        if (content.isNullOrEmpty()) return
        
        val message = Message(
            id = UUID.randomUUID().toString(),
            senderId = userId,
            senderName = args.userName,
            content = content,
            timestamp = System.currentTimeMillis(),
            isLocal = true
        )
        
        messages.add(message)
        messageAdapter.submitList(messages.toList())
        binding.messagesRecyclerView.smoothScrollToPosition(messages.size - 1)
        
        Log.d(TAG, "Sending message: $content")
        wifiAwareManager.sendMessage(userId, args.userName, content)
        
        binding.messageInput.text?.clear()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "onDestroyView")
        // Don't stop WifiAwareManager here - it's shared across fragments
        _binding = null
    }
}