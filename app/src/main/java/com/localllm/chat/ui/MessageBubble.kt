package com.localllm.chat.ui

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.localllm.chat.models.Message
import com.localllm.chat.models.MessageRole

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(message: Message) {
    val isUser = message.role == MessageRole.user
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    
    val bubbleShape = if (isUser) {
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 4.dp, bottomEnd = 20.dp)
    }
    
    val onLongClickCopy = {
        if (message.content.isNotEmpty()) {
            clipboardManager.setText(AnnotatedString(message.content))
            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 12.dp),
        horizontalArrangement = if (isUser) androidx.compose.foundation.layout.Arrangement.End else androidx.compose.foundation.layout.Arrangement.Start
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .clip(bubbleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = onLongClickCopy
                    )
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Column {
                    if (message.content.isEmpty() && message.isStreaming) {
                        Text(
                            text = "…",
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(4.dp)
                        )
                    } else {
                        SelectionContainer {
                            Text(
                                text = message.content,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                        if (message.isStreaming) {
                            Text(
                                text = "●",
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(top = 4.dp),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .clip(bubbleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = onLongClickCopy
                    )
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                SelectionContainer {
                    Text(
                        text = message.content,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}
