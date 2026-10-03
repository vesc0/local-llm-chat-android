package com.localllm.chat.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.localllm.chat.models.AppSettings
import com.localllm.chat.models.Conversation
import com.localllm.chat.models.Message
import com.localllm.chat.models.MessageRole
import com.localllm.chat.services.OllamaService
import com.localllm.chat.services.StorageService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val storageService = StorageService.getInstance(application)
    
    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    private val _activeConversationId = MutableStateFlow<String?>(null)
    val activeConversationId: StateFlow<String?> = _activeConversationId.asStateFlow()

    private val _generatingConversations = MutableStateFlow<Set<String>>(emptySet())
    val generatingConversations: StateFlow<Set<String>> = _generatingConversations.asStateFlow()

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val streamingJobs = mutableMapOf<String, Job>()

    init {
        viewModelScope.launch {
            val convs = withContext(Dispatchers.IO) {
                storageService.loadConversations()
            }
            val sets = withContext(Dispatchers.IO) {
                storageService.loadSettings()
            }
            _conversations.value = convs
            _settings.value = sets
            if (_activeConversationId.value == null) {
                _activeConversationId.value = convs.firstOrNull()?.id
            }
        }
    }

    val activeConversation: Conversation?
        get() = _conversations.value.firstOrNull { it.id == _activeConversationId.value }

    fun createConversation() {
        val newConv = Conversation(
            title = "New Chat",
            messages = emptyList(),
            model = _settings.value.selectedModel
        )
        _conversations.value = listOf(newConv) + _conversations.value
        _activeConversationId.value = newConv.id
        save()
    }

    fun deleteConversation(id: String) {
        val newList = _conversations.value.filterNot { it.id == id }
        _conversations.value = newList
        if (_activeConversationId.value == id) {
            _activeConversationId.value = newList.firstOrNull()?.id
        }
        save()
    }

    fun renameConversation(id: String, newTitle: String) {
        _conversations.value = _conversations.value.map {
            if (it.id == id) it.copy(title = newTitle) else it
        }
        save()
    }

    fun selectConversation(id: String) {
        _activeConversationId.value = id
    }

    fun updateSettings(newSettings: AppSettings) {
        _settings.value = newSettings
        viewModelScope.launch {
            storageService.saveSettings(newSettings)
        }
    }

    fun sendMessage(content: String) {
        if (content.trim().isEmpty()) return

        if (_activeConversationId.value == null) {
            createConversation()
        }

        val id = _activeConversationId.value ?: return
        
        val conversationIndex = _conversations.value.indexOfFirst { it.id == id }
        if (conversationIndex == -1) return
        
        val currentConv = _conversations.value[conversationIndex]
        val isFirstMessage = currentConv.messages.isEmpty()
        
        val userMessage = Message(role = MessageRole.user, content = content)
        val assistantMessage = Message(role = MessageRole.assistant, content = "", isStreaming = true)
        
        val title = if (isFirstMessage) {
            content.take(30) + if (content.length > 30) "..." else ""
        } else {
            currentConv.title
        }

        val updatedConv = currentConv.copy(
            title = title,
            messages = currentConv.messages + userMessage + assistantMessage
        )
        
        _conversations.value = _conversations.value.toMutableList().apply {
            set(conversationIndex, updatedConv)
        }
        
        _generatingConversations.value = _generatingConversations.value + id
        save()

        // Messages to send to the API — exclude the empty assistant placeholder
        val messagesToSend = updatedConv.messages.dropLast(1)

        streamingJobs[id] = viewModelScope.launch {
            try {
                OllamaService.shared.streamChat(messagesToSend, _settings.value) { token ->
                    appendTokenToActiveMessage(id, token)
                }

                finishStreamingMessage(id)

                if (isFirstMessage) {
                    generateTitle(id)
                }
            } catch (e: Throwable) {
                appendTokenToActiveMessage(id, "\n\n**Error**: ${e.localizedMessage}")
                finishStreamingMessage(id)
            } finally {
                _generatingConversations.value = _generatingConversations.value - id
                streamingJobs.remove(id)
                save()
            }
        }
    }

    private fun appendTokenToActiveMessage(id: String, token: String) {
        val convs = _conversations.value
        val index = convs.indexOfFirst { it.id == id }
        if (index == -1) return
        val conv = convs[index]
        if (conv.messages.isEmpty()) return
        
        val lastMsg = conv.messages.last()
        val updatedMsg = lastMsg.copy(content = lastMsg.content + token)
        val updatedMessages = conv.messages.dropLast(1) + updatedMsg
        val updatedConv = conv.copy(messages = updatedMessages)
        _conversations.value = convs.toMutableList().apply { set(index, updatedConv) }
    }

    private fun finishStreamingMessage(id: String) {
        val convs = _conversations.value
        val index = convs.indexOfFirst { it.id == id }
        if (index == -1) return
        val conv = convs[index]
        if (conv.messages.isEmpty()) return
        
        val lastMsg = conv.messages.last()
        val updatedMsg = lastMsg.copy(isStreaming = false)
        val updatedMessages = conv.messages.dropLast(1) + updatedMsg
        val updatedConv = conv.copy(messages = updatedMessages)
        _conversations.value = convs.toMutableList().apply { set(index, updatedConv) }
    }

    private suspend fun generateTitle(conversationId: String) {
        val conv = _conversations.value.firstOrNull { it.id == conversationId } ?: return
        
        val promptMessage = Message(
            role = MessageRole.user, 
            content = "Summarize our conversation above into a very concise title (maximum 5 words). Reply ONLY with the title text itself, without quotes, prefixes, or punctuation."
        )
        val messagesForTitle = conv.messages + promptMessage

        try {
            val generatedTitle = OllamaService.shared.generateChat(messagesForTitle, _settings.value)
            if (generatedTitle.isNotEmpty()) {
                val cleanTitle = generatedTitle.replace("\"", "")
                renameConversation(conversationId, cleanTitle)
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    fun stopGeneration() {
        val id = _activeConversationId.value ?: return
        streamingJobs[id]?.cancel()
        streamingJobs.remove(id)
        _generatingConversations.value = _generatingConversations.value - id
        finishStreamingMessage(id)
        save()
    }

    private fun save() {
        val conversationsSnapshot = _conversations.value.toList()
        viewModelScope.launch {
            storageService.saveConversations(conversationsSnapshot)
        }
    }
}
