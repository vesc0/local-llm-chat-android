package com.localllm.chat.services

import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.localllm.chat.models.Message
import com.localllm.chat.models.prompt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Android's built-in model, served by AICore through ML Kit. */
class GeminiNanoService : ChatService {

    override fun stream(messages: List<Message>): Flow<String> = flow {
        val model = Generation.getClient()
        try {
            when (model.checkStatus()) {
                FeatureStatus.UNAVAILABLE -> error(UNAVAILABLE)
                FeatureStatus.DOWNLOADABLE, FeatureStatus.DOWNLOADING -> model.download().collect {
                    if (it is DownloadStatus.DownloadFailed) throw it.e
                }
            }
            model.generateContentStream(prompt(messages)).collect { response ->
                response.candidates.firstOrNull()?.let { emit(it.text) }
            }
        } finally {
            model.close()
        }
    }

    // The prompt API is single-turn, so earlier turns are flattened into a transcript.
    private fun prompt(messages: List<Message>) = messages.singleOrNull()?.prompt
        ?: messages.joinToString("\n\n", postfix = "\n\nAssistant:") { "${it.role.name}: ${it.prompt}" }

    companion object {
        private const val UNAVAILABLE = "Gemini Nano isn't available on this device."

        /** Whether Gemini Nano can be used, with a note on its state. */
        suspend fun status(): Pair<Boolean, String?> {
            val model = Generation.getClient()
            return try {
                when (model.checkStatus()) {
                    FeatureStatus.AVAILABLE -> true to null
                    FeatureStatus.UNAVAILABLE -> false to UNAVAILABLE
                    else -> true to "Gemini Nano downloads on first use, which can take a few minutes."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false to (e.message ?: UNAVAILABLE)
            } finally {
                model.close()
            }
        }
    }
}
