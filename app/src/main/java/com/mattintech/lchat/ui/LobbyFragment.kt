package com.mattintech.lchat.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.mattintech.lchat.R
import com.mattintech.lchat.databinding.FragmentLobbyBinding
import com.mattintech.lchat.network.WifiAwareManager
import com.mattintech.lchat.network.WifiAwareManagerSingleton
import com.mattintech.lchat.utils.LOG_PREFIX

class LobbyFragment : Fragment() {
    
    companion object {
        private const val TAG = LOG_PREFIX + "LobbyFragment:"
    }
    
    private var _binding: FragmentLobbyBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var wifiAwareManager: WifiAwareManager
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d(TAG, "onCreateView")
        _binding = FragmentLobbyBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated")
        
        Log.d(TAG, "Getting WifiAwareManager singleton")
        wifiAwareManager = WifiAwareManagerSingleton.getInstance(requireContext())
        
        setupUI()
    }
    
    private fun setupUI() {
        binding.modeRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.hostRadio -> {
                    binding.roomLayout.visibility = View.VISIBLE
                    binding.actionButton.text = getString(R.string.start_hosting)
                    binding.roomsRecyclerView.visibility = View.GONE
                    binding.noRoomsText.visibility = View.GONE
                }
                R.id.clientRadio -> {
                    binding.roomLayout.visibility = View.GONE
                    binding.actionButton.text = getString(R.string.search_rooms)
                    binding.roomsRecyclerView.visibility = View.VISIBLE
                }
            }
        }
        
        binding.actionButton.setOnClickListener {
            val userName = binding.nameInput.text?.toString()?.trim()
            
            if (userName.isNullOrEmpty()) {
                Toast.makeText(context, "Please enter your name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            when (binding.modeRadioGroup.checkedRadioButtonId) {
                R.id.hostRadio -> {
                    val roomName = binding.roomInput.text?.toString()?.trim()
                    if (roomName.isNullOrEmpty()) {
                        Toast.makeText(context, "Please enter a room name", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    startHostMode(roomName, userName)
                }
                R.id.clientRadio -> {
                    startClientMode(userName)
                }
            }
        }
        
        wifiAwareManager.setConnectionCallback { roomName, isConnected ->
            Log.d(TAG, "Connection callback - room: $roomName, connected: $isConnected")
            activity?.runOnUiThread {
                if (isConnected && binding.modeRadioGroup.checkedRadioButtonId == R.id.clientRadio) {
                    val userName = binding.nameInput.text?.toString()?.trim() ?: ""
                    navigateToChat(roomName, userName, false)
                } else if (!isConnected && binding.modeRadioGroup.checkedRadioButtonId == R.id.clientRadio) {
                    binding.noRoomsText.text = "Failed to connect to $roomName. Ensure Wi-Fi is enabled on both devices."
                    Toast.makeText(context, "Connection failed. Check Wi-Fi is enabled.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private fun startHostMode(roomName: String, userName: String) {
        Log.d(TAG, "Starting host mode - room: $roomName, user: $userName")
        wifiAwareManager.startHostMode(roomName)
        navigateToChat(roomName, userName, true)
    }
    
    private fun startClientMode(userName: String) {
        Log.d(TAG, "Starting client mode - user: $userName")
        binding.noRoomsText.visibility = View.VISIBLE
        binding.noRoomsText.text = getString(R.string.connecting)
        wifiAwareManager.startClientMode()
    }
    
    private fun navigateToChat(roomName: String, userName: String, isHost: Boolean) {
        Log.d(TAG, "Navigating to chat - room: $roomName, user: $userName, isHost: $isHost")
        val action = LobbyFragmentDirections.actionLobbyToChat(
            roomName = roomName,
            userName = userName,
            isHost = isHost
        )
        findNavController().navigate(action)
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}