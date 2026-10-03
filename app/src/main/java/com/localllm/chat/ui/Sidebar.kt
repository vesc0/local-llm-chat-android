package com.localllm.chat.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.localllm.chat.models.Conversation

@Composable
fun Sidebar(viewModel: ChatViewModel, onSettings: () -> Unit, onClose: () -> Unit) {
    var renaming by remember { mutableStateOf<Conversation?>(null) }

    ModalDrawerSheet {
        LazyColumn(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            item {
                NavigationDrawerItem(
                    label = { Text("New Chat", style = MaterialTheme.typography.titleMedium) },
                    icon = { Icon(Icons.Default.Add, null) },
                    selected = false,
                    onClick = {
                        viewModel.createConversation()
                        onClose()
                    },
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            item {
                Text(
                    "Conversations",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            items(viewModel.conversations, key = { it.id }) { conversation ->
                var showMenu by remember { mutableStateOf(false) }
                NavigationDrawerItem(
                    label = { Text(conversation.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    icon = { Icon(Icons.Default.ChatBubbleOutline, null) },
                    badge = {
                        if (conversation.id in viewModel.generatingIds) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        }
                        Box {
                            IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, "More options") }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Rename") },
                                    leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                                    onClick = {
                                        showMenu = false
                                        renaming = conversation
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        showMenu = false
                                        viewModel.deleteConversation(conversation.id)
                                    },
                                )
                            }
                        }
                    },
                    selected = conversation.id == viewModel.activeId,
                    onClick = {
                        viewModel.activeId = conversation.id
                        onClose()
                    },
                )
            }
        }
        HorizontalDivider()
        NavigationDrawerItem(
            label = { Text("Settings") },
            icon = { Icon(Icons.Outlined.Settings, null) },
            selected = false,
            onClick = {
                onClose()
                onSettings()
            },
            modifier = Modifier.padding(12.dp),
        )
    }

    renaming?.let { conversation ->
        var title by remember(conversation.id) { mutableStateOf(conversation.title) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename Conversation") },
            text = { OutlinedTextField(title, { title = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.renameConversation(conversation.id, title)
                    renaming = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
        )
    }
}
