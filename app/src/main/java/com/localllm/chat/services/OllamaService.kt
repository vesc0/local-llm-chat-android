package com.localllm.chat.services

import com.localllm.chat.models.AppJson
import com.localllm.chat.models.Message
import com.localllm.chat.models.ThoughtTagger
import com.localllm.chat.models.prompt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

class OllamaService(
    private val host: HttpUrl,
    private val model: String,
    private val attachments: AttachmentStore,
) : ChatService {

    override fun stream(messages: List<Message>): Flow<String> = callbackFlow {
        val request = Request.Builder()
            .url(host.endpoint("api/chat"))
            .post(payload(messages).toRequestBody("application/json".toMediaType()))
            .build()
        val call = httpClient.newCall(request)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                close(e)
            }

            override fun onResponse(call: Call, response: Response) {
                close(runCatching { response.use { read(it) { token -> trySendBlocking(token) } } }.exceptionOrNull())
            }
        })
        awaitClose { call.cancel() }
    }.flowOn(Dispatchers.IO)

    private fun payload(messages: List<Message>) = buildJsonObject {
        put("model", model)
        put("stream", true)
        putJsonArray("messages") {
            for (message in messages) addJsonObject {
                put("role", message.role.name.lowercase())
                put("content", message.prompt)
                val images = attachments.base64Images(message.attachments)
                if (images.isNotEmpty()) putJsonArray("images") { images.forEach { add(it) } }
            }
        }
    }.toString()

    private fun read(response: Response, emit: (String) -> Unit) {
        validate(response)
        val tagger = ThoughtTagger()
        val source = response.body.source()
        while (true) {
            val line = source.readUtf8Line() ?: break
            val chunk = runCatching { AppJson.decodeFromString<StreamChunk>(line) }.getOrNull() ?: continue
            chunk.error?.let { error(it) }
            emit(tagger.render(chunk.message?.thinking, chunk.message?.content))
        }
        emit(tagger.finish())
    }

    companion object {
        private val discoveryClient = httpClient.newBuilder().callTimeout(5, TimeUnit.SECONDS).build()

        fun url(host: String) = host.trim().toHttpUrlOrNull()
            ?: error("\"$host\" is not a valid Ollama host. Use a form like http://192.168.1.10:11434.")

        suspend fun fetchModels(host: String): List<String> = withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url(host).endpoint("api/tags")).build()
            discoveryClient.newCall(request).execute().use { response ->
                validate(response)
                AppJson.decodeFromString<TagsResponse>(response.body.string()).models.map { it.name }
            }
        }

        private fun HttpUrl.endpoint(path: String) = newBuilder().addPathSegments(path).build()

        private fun validate(response: Response) {
            check(response.isSuccessful) { "The server responded with status ${response.code}." }
        }
    }

    @Serializable
    private data class StreamChunk(val message: MessageChunk? = null, val error: String? = null)

    @Serializable
    private data class MessageChunk(val content: String? = null, val thinking: String? = null)

    @Serializable
    private data class TagsResponse(val models: List<Tag>) {
        @Serializable
        data class Tag(val name: String)
    }
}
