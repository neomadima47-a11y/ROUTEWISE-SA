package com.routewise.sa.ui.chat

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import com.routewise.sa.R
import com.routewise.sa.databinding.ItemChatMessageBinding

class ChatAdapter(
    private val onSpeakClick: ((String) -> Unit)? = null
) : RecyclerView.Adapter<ChatAdapter.ViewHolder>() {

    private val messages = mutableListOf<ChatMessage>()

    fun submitList(list: List<ChatMessage>) {
        messages.clear()
        messages.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemChatMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(messages[position], onSpeakClick)
    }

    override fun getItemCount(): Int = messages.size

    class ViewHolder(private val binding: ItemChatMessageBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(msg: ChatMessage, onSpeakClick: ((String) -> Unit)?) {
            binding.tvChatText.text = msg.text
            
            val params = binding.cardMsg.layoutParams as LinearLayout.LayoutParams
            if (msg.sender == "user") {
                binding.layoutChatMsg.gravity = Gravity.END
                binding.cardMsg.setBackgroundResource(R.drawable.bg_rounded_emerald)
                binding.tvChatText.setTextColor(binding.root.context.getColor(R.color.background_slate_dark))
                binding.btnSpeakBubble.visibility = View.GONE
                params.setMargins(60, 0, 0, 0)
            } else {
                binding.layoutChatMsg.gravity = Gravity.START
                binding.cardMsg.setBackgroundResource(R.drawable.bg_card_unselected)
                binding.tvChatText.setTextColor(binding.root.context.getColor(R.color.text_primary_white))
                binding.btnSpeakBubble.visibility = View.VISIBLE
                binding.btnSpeakBubble.setOnClickListener {
                    onSpeakClick?.invoke(msg.text)
                }
                params.setMargins(0, 0, 60, 0)
            }
            binding.cardMsg.layoutParams = params
        }
    }
}
