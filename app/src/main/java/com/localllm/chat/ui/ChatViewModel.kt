package com.localllm.chat.ui

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.localllm.chat.models.AppSettings
import com.localllm.chat.models.Attachment
import com.localllm.chat.models.AttachmentType
import com.localllm.chat.models.Conversation
import com.localllm.chat.models.Message
import com.localllm.chat.models.Role
import com.localllm.chat.models.ThoughtParser
import com.localllm.chat.services.AttachmentStore
import com.localllm.chat.services.ChatService
import com.localllm.chat.services.ConversationStore
import com.localllm.chat.services.FileStore
import com.localllm.chat.services.LocalModel
import com.localllm.chat.services.ModelManager
import com.localllm.chat.services.TAG
import com.localllm.chat.services.generate
import com.localllm.chat.services.makeChatService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

class ChatViewModel(
    private val store: ConversationStore,
    val attachments: AttachmentStore,
    val models: ModelManager,
    private val makeService: (AppSettings) -> ChatService,
) : ViewModel() {
    var conversations by mutableStateOf(store.loadConversations().sortedByDescending { it.updatedAt })
        private set
    var generatingIds by mutableStateOf(emptySet<String>())
        private set
    var pendingAttachments by mutableStateOf(emptyList<Attachment>())
        private set
    var activeId by mutableStateOf<String?>(null)
    var settings by mutableStateOf(store.loadSettings())
        private set

    private val tasks = mutableMapOf<String, Job>()
    private var settingsSave: Job? = null

    val activeConversation get() = conversations.firstOrNull { it.id == activeId }
    val isGenerating get() = activeId in generatingIds

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        settings = transform(settings)
        settingsSave?.cancel()
        settingsSave = viewModelScope.launch {
            delay(400)
            store.save(settings)
        }
    }

    fun createConversation() {
        val conversation = Conversation(title = "New Chat")
        conversations = listOf(conversation) + conversations
        activeId = conversation.id
        persist()
    }

    fun deleteConversation(id: String) {
        val conversation = conversations.firstOrNull { it.id == id } ?: return
        tasks.remove(id)?.cancel()
        generatingIds -= id
        conversations -= conversation
        deleteAttachments(conversation.attachmentFilenames)
        if (activeId == id) activeId = conversations.firstOrNull()?.id
        persist()
    }

    fun renameConversation(id: String, title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        updateConversation(id) { it.copy(title = trimmed) }
        persist()
    }

    fun attachImage(bytes: ByteArray) {
        viewModelScope.launch {
            val filename = attachments.saveImage(bytes) ?: return@launch
            pendingAttachments += Attachment(type = AttachmentType.Image, filename = filename)
        }
    }

    fun attachDocument(bytes: ByteArray, isPdf: Boolean) {
        viewModelScope.launch {
            val text = attachments.extractText(bytes, isPdf) ?: return@launch
            pendingAttachments += Attachment(type = if (isPdf) AttachmentType.Pdf else AttachmentType.Text, extractedText = text)
        }
    }

    fun removePendingAttachment(attachment: Attachment) {
        pendingAttachments -= attachment
        deleteAttachments(listOfNotNull(attachment.filename))
    }

    fun deleteAttachments(filenames: List<String>) {
        if (filenames.isNotEmpty()) viewModelScope.launch(Dispatchers.IO) { attachments.delete(filenames) }
    }

    fun downloadModel(repoId: String) = models.download(viewModelScope, repoId)

    fun deleteModel(model: LocalModel) {
        viewModelScope.launch {
            models.delete(model)
            if (settings.localModel == model.repoId) updateSettings { it.copy(localModel = "") }
        }
    }

    fun deleteAllModels() {
        viewModelScope.launch {
            models.deleteAll()
            updateSettings { it.copy(localModel = "") }
        }
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() && pendingAttachments.isEmpty()) return
        if (activeConversation == null) createConversation()
        val conversation = activeConversation ?: return

        val isFirstExchange = conversation.messages.isEmpty()
        val history = conversation.messages + Message(role = Role.User, content = trimmed, attachments = pendingAttachments)
        val reply = Message(role = Role.Assistant, content = "", isStreaming = true)
        pendingAttachments = emptyList()

        updateConversation(conversation.id) {
            it.copy(
                messages = history + reply,
                updatedAt = System.currentTimeMillis(),
                title = if (isFirstExchange) provisionalTitle(trimmed) else it.title,
            )
        }
        generatingIds += conversation.id
        persist()

        val settings = settings
        tasks[conversation.id] = viewModelScope.launch {
            respond(history, conversation.id, reply.id, settings, isFirstExchange)
        }
    }

    fun stop() {
        activeId?.let { tasks[it]?.cancel() }
    }

    private suspend fun respond(
        history: List<Message>,
        conversationId: String,
        messageId: String,
        settings: AppSettings,
        retitleAfterwards: Boolean,
    ) {
        try {
            val service = makeService(settings)
            service.stream(history).collect { append(it, messageId, conversationId) }
            updateMessage(conversationId, messageId) { it.copy(isStreaming = false) }
            if (retitleAfterwards) retitle(conversationId, service)
        } catch (e: CancellationException) {
            updateMessage(conversationId, messageId) { if (it.isStreaming) it.copy(isStreaming = false, isCancelled = true) else it }
            throw e
        } catch (e: Exception) {
            updateMessage(conversationId, messageId) { it.copy(isStreaming = false, errorMessage = describe(e)) }
        } finally {
            generatingIds -= conversationId
            tasks.remove(conversationId)
            persist()
        }
    }

    private suspend fun retitle(conversationId: String, service: ChatService) {
        val conversation = conversations.firstOrNull { it.id == conversationId } ?: return
        val request = conversation.messages + Message(
            role = Role.User,
            content = "Summarize our conversation above into a title of at most 5 words. " +
                "Reply with the title only — no quotes, prefixes or punctuation.",
        )
        try {
            val title = ThoughtParser.parse(service.generate(request)).answer.replace("\"", "").trim()
            if (title.isNotEmpty()) updateConversation(conversationId) { it.copy(title = title.take(60)) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Title generation failed", e)
        }
    }

    private fun updateConversation(id: String, transform: (Conversation) -> Conversation) {
        conversations = conversations.map { if (it.id == id) transform(it) else it }
    }

    private fun updateMessage(conversationId: String, messageId: String, transform: (Message) -> Message) =
        updateConversation(conversationId) { conversation ->
            conversation.copy(messages = conversation.messages.map { if (it.id == messageId) transform(it) else it })
        }

    private fun append(token: String, messageId: String, conversationId: String) =
        updateMessage(conversationId, messageId) { message ->
            val content = message.content + token
            // Scanning is bounded to the thinking phase of a reasoning model's reply.
            val thoughtMillis = message.thoughtMillis ?: (System.currentTimeMillis() - message.timestamp).takeIf {
                ThoughtParser.openers.any { content.startsWith(it) } && ThoughtParser.closers.any { content.contains(it) }
            }
            message.copy(content = content, thoughtMillis = thoughtMillis)
        }

    private fun persist() {
        conversations = conversations.sortedByDescending { it.updatedAt }
        val snapshot = conversations
        viewModelScope.launch { store.save(snapshot) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val attachments = AttachmentStore(File(app.filesDir, "attachments"))
                val models = ModelManager(File(app.noBackupFilesDir, "models"))
                ChatViewModel(FileStore(app.filesDir), attachments, models) {
                    makeChatService(it, attachments, models, app.cacheDir)
                }
            }
        }

        private fun provisionalTitle(text: String) = when {
            text.isEmpty() -> "New Chat"
            text.length > 30 -> text.take(30) + "…"
            else -> text
        }

        private fun describe(error: Exception) = when (error) {
            is IOException -> "${error.message ?: "Network error"}\n\nCheck the Ollama host in Settings and that the server is reachable."
            else -> error.message ?: error.toString()
        }
    }
}
