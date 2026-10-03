package com.localllm.chat.services

import com.localllm.chat.models.AppSettings
import com.localllm.chat.models.Message
import com.localllm.chat.models.OllamaStreamChunk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class OllamaService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // 0 means no timeout for streaming
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class ApiMessage(val role: String, val content: String)
    
    @Serializable
    private data class RequestBody(val model: String, val messages: List<ApiMessage>, val stream: Boolean)

    @Serializable
    private data class OllamaResponse(val message: com.localllm.chat.models.OllamaMessageChunk? = null)

    suspend fun streamChat(messages: List<Message>, settings: AppSettings, onToken: (String) -> Unit) = withContext(Dispatchers.IO) {
        val endpoint = "${settings.ollamaHost}/api/chat"
        
        val apiMessages = messages.map { ApiMessage(it.role.name, it.content) }
        val body = RequestBody(settings.selectedModel, apiMessages, stream = true)
        val jsonBody = json.encodeToString(body)

        val request = Request.Builder()
            .url(endpoint)
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Unexpected code $response")
            }

            val source = response.body?.source() ?: throw IOException("Empty response body")
            
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: continue
                if (line.isBlank()) continue
                
                try {
                    val chunk = json.decodeFromString<OllamaStreamChunk>(line)
                    chunk.message?.content?.let { content ->
                        withContext(Dispatchers.Main) {
                            onToken(content)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    suspend fun generateChat(messages: List<Message>, settings: AppSettings): String = withContext(Dispatchers.IO) {
        val endpoint = "${settings.ollamaHost}/api/chat"
        
        val apiMessages = messages.map { ApiMessage(it.role.name, it.content) }
        val body = RequestBody(settings.selectedModel, apiMessages, stream = false)
        val jsonBody = json.encodeToString(body)

        val request = Request.Builder()
            .url(endpoint)
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Unexpected code $response")
            }

            val responseBodyString = response.body?.string() ?: return@use ""
            val result = json.decodeFromString<OllamaResponse>(responseBodyString)
            result.message?.content?.trim() ?: ""
        }
    }

    companion object {
        val shared = OllamaService()
    }
}
