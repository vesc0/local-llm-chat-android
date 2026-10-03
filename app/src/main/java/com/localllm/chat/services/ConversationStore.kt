package com.localllm.chat.services

import android.util.Log
import com.localllm.chat.models.AppJson
import com.localllm.chat.models.AppSettings
import com.localllm.chat.models.Conversation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

const val TAG = "LocalLLMChat"

interface ConversationStore {
    fun loadConversations(): List<Conversation>
    fun loadSettings(): AppSettings
    suspend fun save(conversations: List<Conversation>)
    suspend fun save(settings: AppSettings)
}

class FileStore(private val dir: File) : ConversationStore {
    private val writer = Dispatchers.IO.limitedParallelism(1)
    private val conversationsFile = File(dir, "conversations.json")
    private val settingsFile = File(dir, "settings.json")

    // A reply interrupted by process death would otherwise show as generating forever.
    override fun loadConversations() = read<List<Conversation>>(conversationsFile).orEmpty().map { conversation ->
        conversation.copy(messages = conversation.messages.map {
            if (it.isStreaming) it.copy(isStreaming = false, isCancelled = true) else it
        })
    }

    override fun loadSettings() = read<AppSettings>(settingsFile) ?: AppSettings()

    override suspend fun save(conversations: List<Conversation>) = write(conversationsFile) { AppJson.encodeToString(conversations) }

    override suspend fun save(settings: AppSettings) = write(settingsFile) { AppJson.encodeToString(settings) }

    private inline fun <reified T> read(file: File): T? = try {
        if (file.exists()) AppJson.decodeFromString<T>(file.readText()) else null
    } catch (e: Exception) {
        Log.e(TAG, "Failed to read ${file.name}", e)
        null
    }

    private suspend fun write(file: File, encode: () -> String): Unit = withContext(writer) {
        val temp = File(dir, "${file.name}.tmp")
        try {
            temp.writeText(encode())
            if (!temp.renameTo(file)) Log.e(TAG, "Failed to replace ${file.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write ${file.name}", e)
        }
    }
}
