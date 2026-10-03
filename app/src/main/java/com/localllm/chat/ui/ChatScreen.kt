package com.localllm.chat.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel, onOpenDrawer: () -> Unit) {
    val activeConversation by viewModel.activeConversationId.collectAsState()
    val conversations by viewModel.conversations.collectAsState()
    
    val conversation = conversations.firstOrNull { it.id == activeConversation }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(conversation?.title ?: "Select a Conversation") },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                reverseLayout = false
            ) {
                if (conversation != null) {
                    items(
                        items = conversation.messages,
                        key = { it.id }
                    ) { message ->
                        MessageBubble(message = message)
                    }
                }
            }

            ChatInput(viewModel = viewModel)
        }
    }
}
