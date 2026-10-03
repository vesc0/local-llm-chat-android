package com.localllm.chat.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Sidebar(viewModel: ChatViewModel, onCloseDrawer: () -> Unit) {
    val conversations by viewModel.conversations.collectAsState()
    val activeId by viewModel.activeConversationId.collectAsState()
    val generatingConversations by viewModel.generatingConversations.collectAsState()
    
    var showSettings by remember { mutableStateOf(false) }
    
    var showRenameDialog by remember { mutableStateOf(false) }
    var conversationToRename by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }

    ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(16.dp)
        ) {
            Button(
                onClick = {
                    viewModel.createConversation()
                    onCloseDrawer()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Chat")
                Spacer(modifier = Modifier.width(8.dp))
                Text("New Chat", style = MaterialTheme.typography.titleLarge)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Divider()
            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(
                    items = conversations,
                    key = { it.id }
                ) { conv ->
                    val isActive = conv.id == activeId
                    val isGenerating = generatingConversations.contains(conv.id)
                    var menuExpanded by remember { mutableStateOf(false) }
                    
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        viewModel.selectConversation(conv.id)
                                        onCloseDrawer()
                                    },
                                    onLongClick = {
                                        menuExpanded = true
                                    }
                                )
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isGenerating) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .padding(2.dp),
                                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    Icons.Default.ChatBubbleOutline,
                                    contentDescription = null,
                                    tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = conv.title.ifEmpty { "New Chat" },
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }

                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Rename") },
                                onClick = {
                                    menuExpanded = false
                                    conversationToRename = conv.id
                                    renameText = conv.title.ifEmpty { "New Chat" }
                                    showRenameDialog = true
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Rename",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.deleteConversation(conv.id)
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            )
                        }
                    }
                }
            }

            Divider()
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showSettings = true }
                    .padding(vertical = 16.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Settings", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }

    if (showSettings) {
        SettingsDialog(
            viewModel = viewModel,
            onDismiss = { showSettings = false }
        )
    }

    if (showRenameDialog && conversationToRename != null) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Chat") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.renameConversation(conversationToRename!!, renameText.trim())
                        showRenameDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showRenameDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun Spacer(modifier: Modifier) {
    androidx.compose.foundation.layout.Spacer(modifier = modifier)
}
