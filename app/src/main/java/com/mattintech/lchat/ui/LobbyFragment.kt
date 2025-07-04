package com.mattintech.lchat.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.mattintech.lchat.R
import com.mattintech.lchat.databinding.FragmentLobbyBinding
import com.mattintech.lchat.viewmodel.LobbyEvent
import com.mattintech.lchat.viewmodel.LobbyState
import com.mattintech.lchat.viewmodel.LobbyViewModel
import com.mattintech.lchat.utils.LOG_PREFIX
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LobbyFragment : Fragment() {
    
    companion object {
        private const val TAG = LOG_PREFIX + "LobbyFragment:"
    }
    
    private var _binding: FragmentLobbyBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: LobbyViewModel by viewModels()
    
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
        
        setupUI()
        observeViewModel()
    }
    
    private fun setupUI() {
        binding.nameInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            
            override fun afterTextChanged(s: Editable?) {
                s?.toString()?.trim()?.let { name ->
                    viewModel.saveUserName(name)
                }
            }
        })
        
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
            val userName = binding.nameInput.text?.toString()?.trim() ?: ""
            
            when (binding.modeRadioGroup.checkedRadioButtonId) {
                R.id.hostRadio -> {
                    val roomName = binding.roomInput.text?.toString()?.trim() ?: ""
                    viewModel.startHostMode(roomName, userName)
                }
                R.id.clientRadio -> {
                    viewModel.startClientMode(userName)
                }
            }
        }
    }
    
    private fun observeViewModel() {
        // Collect saved user name
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.savedUserName.collect { savedName ->
                    if (!savedName.isNullOrEmpty() && binding.nameInput.text.isNullOrEmpty()) {
                        binding.nameInput.setText(savedName)
                    }
                }
            }
        }
        
        // Collect state
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    when (state) {
                        is LobbyState.Idle -> {
                            binding.noRoomsText.visibility = View.GONE
                        }
                        is LobbyState.Connecting -> {
                            if (binding.modeRadioGroup.checkedRadioButtonId == R.id.clientRadio) {
                                binding.noRoomsText.visibility = View.VISIBLE
                                binding.noRoomsText.text = getString(R.string.connecting)
                            }
                        }
                        is LobbyState.Connected -> {
                            if (binding.modeRadioGroup.checkedRadioButtonId == R.id.clientRadio) {
                                val userName = binding.nameInput.text?.toString()?.trim() ?: ""
                                viewModel.onConnectedToRoom(state.roomName, userName)
                            }
                        }
                        is LobbyState.Error -> {
                            binding.noRoomsText.visibility = View.VISIBLE
                            binding.noRoomsText.text = state.message
                            Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
        
        // Collect events
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is LobbyEvent.NavigateToChat -> {
                            navigateToChat(event.roomName, event.userName, event.isHost)
                        }
                        is LobbyEvent.ShowError -> {
                            Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
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