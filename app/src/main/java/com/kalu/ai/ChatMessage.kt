package com.kalu.ai

data class ChatMessage(
    val id: Long,
    val fromUser: Boolean,
    val text: String,
    val time: Long
)
