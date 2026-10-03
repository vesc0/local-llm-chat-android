package com.localllm.chat.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ChatInput(viewModel: ChatViewModel, modifier: Modifier = Modifier) {
    var text by rememberSaveable { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    val canSend = text.isNotBlank() || viewModel.pendingAttachments.isNotEmpty()

    val resolver = LocalContext.current.contentResolver
    val scope = rememberCoroutineScope()
    fun read(uri: Uri?, then: (ByteArray, String?) -> Unit) {
        uri ?: return
        scope.launch {
            val bytes = withContext(Dispatchers.IO) { runCatching { resolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull() }
            bytes?.let { then(it, resolver.getType(uri)) }
        }
    }
    val pickImage = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        read(uri) { bytes, _ -> viewModel.attachImage(bytes) }
    }
    val pickDocument = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        read(uri) { bytes, type -> viewModel.attachDocument(bytes, isPdf = type == "application/pdf") }
    }

    Column(modifier) {
        if (viewModel.pendingAttachments.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(viewModel.pendingAttachments, key = { it.id }) { attachment ->
                    Box {
                        AttachmentThumbnail(attachment, viewModel.attachments, 60.dp)
                        IconButton(
                            onClick = { viewModel.removePendingAttachment(attachment) },
                            modifier = Modifier.align(Alignment.TopEnd).offset(x = 12.dp, y = (-12).dp),
                        ) {
                            Icon(Icons.Default.Cancel, "Remove attachment")
                        }
                    }
                }
            }
        }
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.Bottom) {
            Box {
                IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.Add, "Add attachment") }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Photo") },
                        leadingIcon = { Icon(Icons.Outlined.Image, null) },
                        onClick = {
                            showMenu = false
                            pickImage.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("File") },
                        leadingIcon = { Icon(Icons.Outlined.Description, null) },
                        onClick = {
                            showMenu = false
                            pickDocument.launch(arrayOf("application/pdf", "text/*"))
                        },
                    )
                }
            }
            TextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Message") },
                maxLines = 5,
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
            )
            if (viewModel.isGenerating) {
                FilledIconButton(onClick = viewModel::stop, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Stop, "Stop generating")
                }
            } else {
                FilledIconButton(
                    onClick = {
                        viewModel.send(text)
                        text = ""
                    },
                    enabled = canSend,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(Icons.Default.ArrowUpward, "Send message")
                }
            }
        }
    }
}
