package com.zenembed.hackathonapplication

import android.content.res.Resources
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.ColorRes
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

class ChatAdapter : RecyclerView.Adapter<ChatAdapter.ViewHolder>() {

    private val messages = mutableListOf<ChatMessage>()

    fun addMessage(message: ChatMessage) {
        messages.add(message)
        notifyItemInserted(messages.size)
    }

    override fun getItemCount(): Int = messages.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(messages[position])

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_chat_message, parent, false)
        )
    }

    inner class ViewHolder(private val view: View) : RecyclerView.ViewHolder(view) {
        fun bind(item: ChatMessage) {
            val container: MaterialCardView = view.findViewById(R.id.vMessage)
            val tvMessage: AppCompatTextView = view.findViewById(R.id.tvMessage)
            tvMessage.text = item.message

            when (item.type) {
                MessageType.User -> {
                    container.layoutParams = (container.layoutParams as FrameLayout.LayoutParams).apply {
                        marginEnd = 50.dp()
                        marginStart = 15.dp()
                    }
                }

                MessageType.Remote -> {
                    container.setCardBackgroundColor(getColor(R.color.lightGrey))
                    tvMessage.setTextColor(getColor(R.color.darkGrey))
                    container.layoutParams = (container.layoutParams as FrameLayout.LayoutParams).apply {
                        gravity = Gravity.END
                        marginEnd = 15.dp()
                        marginStart = 50.dp()
                    }
                }
                MessageType.Macros -> {
                    container.setCardBackgroundColor(getColor(R.color.orange))
                    tvMessage.setTextColor(getColor(R.color.white))
                    container.layoutParams = (container.layoutParams as FrameLayout.LayoutParams).apply {
                        marginEnd = 50.dp()
                        marginStart = 15.dp()
                    }
                }

            }
        }

        private fun Int.dp() = this * (Resources.getSystem().displayMetrics.densityDpi) / DisplayMetrics.DENSITY_DEFAULT
        private fun getColor(@ColorRes color: Int) = ContextCompat.getColor(view.context, color)
    }

}