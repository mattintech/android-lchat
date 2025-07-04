package com.mattintech.lchat.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mattintech.lchat.R
import com.mattintech.lchat.data.Message
import com.mattintech.lchat.databinding.ItemMessageBinding
import java.text.SimpleDateFormat
import java.util.*

class MessageAdapter : ListAdapter<Message, MessageAdapter.MessageViewHolder>(MessageDiffCallback()) {
    
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val binding = ItemMessageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MessageViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    inner class MessageViewHolder(
        private val binding: ItemMessageBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(message: Message) {
            binding.senderName.text = message.userName
            binding.messageContent.text = message.content
            binding.timestamp.text = timeFormat.format(Date(message.timestamp))
            
            val layoutParams = binding.messageCard.layoutParams as ConstraintLayout.LayoutParams
            if (message.isOwnMessage) {
                layoutParams.startToStart = ConstraintLayout.LayoutParams.UNSET
                layoutParams.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                binding.messageCard.setCardBackgroundColor(
                    ContextCompat.getColor(binding.root.context, R.color.purple_200)
                )
            } else {
                layoutParams.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                layoutParams.endToEnd = ConstraintLayout.LayoutParams.UNSET
                binding.messageCard.setCardBackgroundColor(
                    ContextCompat.getColor(binding.root.context, R.color.teal_200)
                )
            }
            binding.messageCard.layoutParams = layoutParams
        }
    }
    
    class MessageDiffCallback : DiffUtil.ItemCallback<Message>() {
        override fun areItemsTheSame(oldItem: Message, newItem: Message): Boolean {
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(oldItem: Message, newItem: Message): Boolean {
            return oldItem == newItem
        }
    }
}