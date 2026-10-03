package com.localllm.chat.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

val AppJson = Json { ignoreUnknownKeys = true }

private fun newId() = UUID.randomUUID().toString()

@Serializable
enum class Role {
    @SerialName("user") User,
    @SerialName("assistant") Assistant,
    @SerialName("system") System,
}

@Serializable
enum class AttachmentType { Image, Pdf, Text }

@Serializable
data class Attachment(
    val id: String = newId(),
    val type: AttachmentType,
    /** Name of the file inside the attachments directory. */
    val filename: String? = null,
    val extractedText: String? = null,
)

@Serializable
data class Message(
    val id: String = newId(),
    val role: Role,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false,
    val isCancelled: Boolean = false,
    val thoughtMillis: Long? = null,
    val errorMessage: String? = null,
    val attachments: List<Attachment> = emptyList(),
)

/** The text sent to a model: the typed content followed by any attached documents. */
val Message.prompt: String
    get() {
        val documents = attachments.mapNotNull { it.extractedText }
        if (documents.isEmpty()) return content
        val body = documents.joinToString("\n\n---\n\n")
        return if (content.isEmpty()) "Here are the attached documents:\n\n$body"
        else "$content\n\nAttached documents:\n\n$body"
    }

@Serializable
data class Conversation(
    val id: String = newId(),
    val title: String,
    val messages: List<Message> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val attachmentFilenames get() = messages.flatMap { it.attachments }.mapNotNull { it.filename }
}

@Serializable
enum class ThemeMode { Dark, Light, Auto }

@Serializable
enum class InferenceEngine(val label: String) {
    Ollama("Ollama"),
    LiteRt("LiteRT"),
    GeminiNano("Gemini Nano"),
}

@Serializable
data class AppSettings(
    val ollamaHost: String = "",
    val selectedModel: String = "",
    val localModel: String = "",
    val engine: InferenceEngine = InferenceEngine.LiteRt,
    val themeMode: ThemeMode = ThemeMode.Auto,
) {
    /** The active model's short name, without its Hugging Face namespace. */
    val activeModelName: String?
        get() = when (engine) {
            InferenceEngine.Ollama -> selectedModel
            InferenceEngine.LiteRt -> localModel
            InferenceEngine.GeminiNano -> "Gemini Nano"
        }.substringAfterLast('/').ifEmpty { null }
}
