package com.zenembed.hackathonapplication

data class ChatMessage(
    val type: MessageType,
    val message: String
)

enum class MessageType {
    User, Remote, Macros
}