package com.localllm.chat.services

import android.content.Context
import com.localllm.chat.models.AppSettings
import com.localllm.chat.models.Conversation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class StorageService(private val context: Context) {
    private val conversationsFile = File(context.filesDir, "conversations.json")
    private val settingsFile = File(context.filesDir, "settings.json")

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun saveConversations(conversations: List<Conversation>) = withContext(Dispatchers.IO) {
        try {
            val data = json.encodeToString(conversations)
            conversationsFile.writeText(data)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    fun loadConversations(): List<Conversation> {
        if (!conversationsFile.exists()) return emptyList()
        return try {
            val data = conversationsFile.readText()
            json.decodeFromString<List<Conversation>>(data)
        } catch (e: Throwable) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun saveSettings(settings: AppSettings) = withContext(Dispatchers.IO) {
        try {
            val data = json.encodeToString(settings)
            settingsFile.writeText(data)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    fun loadSettings(): AppSettings {
        if (!settingsFile.exists()) return AppSettings()
        return try {
            val data = settingsFile.readText()
            json.decodeFromString<AppSettings>(data)
        } catch (e: Throwable) {
            e.printStackTrace()
            AppSettings()
        }
    }

    companion object {
        @Volatile
        private var instance: StorageService? = null

        fun getInstance(context: Context): StorageService {
            return instance ?: synchronized(this) {
                instance ?: StorageService(context.applicationContext).also { instance = it }
            }
        }
    }
}
