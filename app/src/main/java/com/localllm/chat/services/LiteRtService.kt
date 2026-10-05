package com.localllm.chat.services

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.localllm.chat.models.Message
import com.localllm.chat.models.Role
import com.localllm.chat.models.ThoughtParser
import com.localllm.chat.models.ThoughtTagger
import com.localllm.chat.models.prompt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import com.google.ai.edge.litertlm.Message as LlmMessage

/** Runs `.litertlm` models on-device with LiteRT-LM. */
class LiteRtService(private val model: File, private val cacheDir: File) : ChatService {

    override fun stream(messages: List<Message>): Flow<String> = flow {
        LiteRtRuntime.withEngine(model, cacheDir) { engine ->
            val config = ConversationConfig(initialMessages = messages.dropLast(1).map { it.toLiteRt() })
            engine.createConversation(config).use { conversation ->
                val tagger = ThoughtTagger()
                try {
                    conversation.sendMessageAsync(messages.last().prompt).collect {
                        emit(tagger.render(it.channels["thought"], it.toString()))
                    }
                    emit(tagger.finish())
                } catch (e: CancellationException) {
                    conversation.cancelProcess()
                    throw e
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun Message.toLiteRt() = when (role) {
        Role.User -> LlmMessage.user(prompt)
        Role.Assistant -> LlmMessage.model(ThoughtParser.parse(content).answer)
        Role.System -> LlmMessage.system(prompt)
    }
}

/** Owns the loaded model, which is expensive to build and must not be released mid-inference. */
object LiteRtRuntime {
    private val mutex = Mutex()
    private var engine: Engine? = null
    private var loadedPath: String? = null

    suspend fun <T> withEngine(model: File, cacheDir: File, block: suspend (Engine) -> T): T = mutex.withLock {
        val engine = engine?.takeIf { loadedPath == model.path } ?: load(model, cacheDir)
        block(engine)
    }

    suspend fun unload() = withContext(Dispatchers.IO) { mutex.withLock { release() } }

    private fun load(model: File, cacheDir: File): Engine {
        release()
        // Not every device or model supports the GPU, so fall back to the CPU.
        val loaded = runCatching { start(model, cacheDir, Backend.GPU()) }
            .getOrElse { start(model, cacheDir, Backend.CPU()) }
        engine = loaded
        loadedPath = model.path
        return loaded
    }

    private fun start(model: File, cacheDir: File, backend: Backend): Engine {
        val engine = Engine(EngineConfig(modelPath = model.path, backend = backend, cacheDir = cacheDir.path))
        engine.initialize()
        try {
            // Missing GPU support (such as no OpenCL) only surfaces once decoding starts.
            if (backend is Backend.GPU) engine.createConversation().use { it.sendMessage("Hi", maxOutputToken = 1) }
        } catch (e: Exception) {
            engine.close()
            throw e
        }
        return engine
    }

    private fun release() {
        engine?.close()
        engine = null
        loadedPath = null
    }
}
