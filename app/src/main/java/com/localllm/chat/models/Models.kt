package com.localllm.chat.models

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class MessageRole {
    user,
    assistant,
    system
}

@Serializable
data class Message(
    val id: String = UUID.randomUUID().toString(),
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false
)

@Serializable
data class Conversation(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val messages: List<Message> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val model: String = ""
)

@Serializable
enum class ThemeMode {
    Dark,
    Light,
    Auto
}

@Serializable
data class AppSettings(
    var ollamaHost: String = "https://unknowable-logan-unprofitable.ngrok-free.dev",
    var selectedModel: String = "llama3.1:latest",
    var themeMode: ThemeMode = ThemeMode.Auto
)

@Serializable
data class OllamaStreamChunk(
    val model: String,
    val created_at: String,
    val message: OllamaMessageChunk? = null,
    val done: Boolean
)

@Serializable
data class OllamaMessageChunk(
    val role: String,
    val content: String
)
