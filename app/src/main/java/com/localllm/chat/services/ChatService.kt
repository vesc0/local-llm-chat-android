package com.localllm.chat.services

import com.localllm.chat.models.AppSettings
import com.localllm.chat.models.InferenceEngine
import com.localllm.chat.models.Message
import kotlinx.coroutines.flow.Flow
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/** A backend capable of continuing a conversation, one token at a time. */
fun interface ChatService {
    fun stream(messages: List<Message>): Flow<String>
}

/** Collects a full response. Used for non-interactive work such as titling. */
suspend fun ChatService.generate(messages: List<Message>) =
    buildString { stream(messages).collect { append(it) } }.trim()

val httpClient: OkHttpClient = OkHttpClient.Builder().readTimeout(2, TimeUnit.MINUTES).build()

/** Builds the service matching the user's current engine selection. */
fun makeChatService(
    settings: AppSettings,
    attachments: AttachmentStore,
    models: ModelManager,
    cacheDir: File,
): ChatService = when (settings.engine) {
    InferenceEngine.Ollama -> {
        if (settings.selectedModel.isEmpty()) {
            error("No Ollama model selected. Choose one in Settings › Select Ollama Model.")
        }
        OllamaService(OllamaService.url(settings.ollamaHost), settings.selectedModel, attachments)
    }
    InferenceEngine.LiteRt -> LiteRtService(
        models.file(settings.localModel)
            ?: error("No on-device model selected. Choose one in Settings › Manage Models."),
        cacheDir,
    )
    InferenceEngine.GeminiNano -> GeminiNanoService()
}
