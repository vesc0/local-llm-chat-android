package com.localllm.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun ChatInput(viewModel: ChatViewModel) {
    var text by remember { mutableStateOf("") }
    val generatingConversations by viewModel.generatingConversations.collectAsState()
    val activeId by viewModel.activeConversationId.collectAsState()
    val isGenerating = activeId != null && generatingConversations.contains(activeId)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text("Message") },
            modifier = Modifier.weight(1f),
            maxLines = 5,
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                unfocusedContainerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
            ),
            textStyle = MaterialTheme.typography.bodyLarge
        )

        Spacer(modifier = Modifier.width(8.dp))

        if (isGenerating) {
            IconButton(
                onClick = { viewModel.stopGeneration() },
                modifier = Modifier.background(MaterialTheme.colorScheme.error, CircleShape)
            ) {
                Icon(
                    Icons.Default.Stop,
                    contentDescription = "Stop",
                    tint = MaterialTheme.colorScheme.onError
                )
            }
        } else {
            IconButton(
                onClick = {
                    if (text.isNotBlank()) {
                        viewModel.sendMessage(text)
                        text = ""
                    }
                },
                enabled = text.isNotBlank(),
                modifier = Modifier.background(
                    if (text.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                    CircleShape
                )
            ) {
                Icon(
                    Icons.Default.ArrowUpward,
                    contentDescription = "Send",
                    tint = if (text.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

