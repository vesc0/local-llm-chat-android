package com.localllm.chat

import com.localllm.chat.models.AppSettings
import com.localllm.chat.models.Conversation
import com.localllm.chat.models.Role
import com.localllm.chat.services.AttachmentStore
import com.localllm.chat.services.ChatService
import com.localllm.chat.services.ConversationStore
import com.localllm.chat.services.ModelManager
import com.localllm.chat.ui.ChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(service: ChatService, store: MemoryStore = MemoryStore()) =
        ChatViewModel(store, AttachmentStore(folder.root), ModelManager(folder.root)) { service }

    private fun tokens(vararg tokens: String) = ChatService { flowOf(*tokens) }

    @Test
    fun streamedTokensLandInTheAssistantMessage() {
        val viewModel = viewModel(tokens("Hel", "lo"))
        viewModel.send("Hi")

        val messages = viewModel.activeConversation!!.messages
        assertEquals(2, messages.size)
        assertEquals(Role.User, messages.first().role)
        assertEquals("Hello", messages.last().content)
        assertFalse(messages.last().isStreaming)
    }

    @Test
    fun firstExchangeIsRetitled() {
        val viewModel = viewModel(tokens("Greeting"))
        viewModel.send("Hi")
        assertEquals("Greeting", viewModel.activeConversation?.title)
    }

    @Test
    fun failureIsReportedOnTheMessage() {
        val viewModel = viewModel(ChatService { flow { throw IllegalStateException("boom") } })
        viewModel.send("Hi")

        val last = viewModel.activeConversation?.messages?.last()
        assertEquals("boom", last?.errorMessage)
        assertEquals(false, last?.isStreaming)
    }

    @Test
    fun stoppingCancelsTheReply() {
        val viewModel = viewModel(ChatService { flow { awaitCancellation() } })
        viewModel.send("Hi")
        assertTrue(viewModel.isGenerating)

        viewModel.stop()

        val last = viewModel.activeConversation?.messages?.last()
        assertEquals(true, last?.isCancelled)
        assertEquals(false, last?.isStreaming)
        assertFalse(viewModel.isGenerating)
    }

    @Test
    fun emptyInputIsIgnored() {
        val viewModel = viewModel(tokens())
        viewModel.send("   ")
        assertTrue(viewModel.conversations.isEmpty())
    }

    @Test
    fun deletingAConversationClearsTheSelection() {
        val viewModel = viewModel(tokens("ok"))
        viewModel.send("Hi")

        viewModel.deleteConversation(viewModel.activeId!!)

        assertTrue(viewModel.conversations.isEmpty())
        assertNull(viewModel.activeId)
    }

    @Test
    fun conversationsPersistAfterAnExchange() {
        val store = MemoryStore()
        viewModel(tokens("ok"), store).send("Hi")

        assertEquals(1, store.conversations.size)
        assertEquals(2, store.conversations.single().messages.size)
    }

    @Test
    fun renameIgnoresBlankTitles() {
        val viewModel = viewModel(tokens())
        viewModel.createConversation()
        val id = viewModel.activeId!!

        viewModel.renameConversation(id, "  ")
        assertEquals("New Chat", viewModel.activeConversation?.title)

        viewModel.renameConversation(id, " Renamed ")
        assertEquals("Renamed", viewModel.activeConversation?.title)
    }
}

class MemoryStore : ConversationStore {
    var conversations = emptyList<Conversation>()
    var settings = AppSettings()

    override fun loadConversations() = conversations
    override fun loadSettings() = settings
    override suspend fun save(conversations: List<Conversation>) {
        this.conversations = conversations
    }

    override suspend fun save(settings: AppSettings) {
        this.settings = settings
    }
}
