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
import com.mattintech.lchat.viewmodel.LobbyEvent
import com.mattintech.lchat.viewmodel.LobbyState
import com.mattintech.lchat.viewmodel.LobbyViewModel
import com.mattintech.lchat.viewmodel.ViewModelFactory
import com.mattintech.lchat.utils.LOG_PREFIX

class LobbyFragment : Fragment() {
    
    companion object {
        private const val TAG = LOG_PREFIX + "LobbyFragment:"
    }
    
    private var _binding: FragmentLobbyBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var viewModel: LobbyViewModel
    
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
        
        val factory = ViewModelFactory(requireContext())
        viewModel = ViewModelProvider(this, factory)[LobbyViewModel::class.java]
        
        setupUI()
        observeViewModel()
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
        viewModel.state.observe(viewLifecycleOwner) { state ->
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
        
        viewModel.events.observe(viewLifecycleOwner) { event ->
            when (event) {
                is LobbyEvent.NavigateToChat -> {
                    navigateToChat(event.roomName, event.userName, event.isHost)
                    viewModel.clearEvent()
                }
                is LobbyEvent.ShowError -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                    viewModel.clearEvent()
                }
                null -> {}
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