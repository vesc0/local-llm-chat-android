package com.localllm.chat.ui

import android.text.format.DateFormat
import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import java.io.File
import java.util.Date

@Composable
fun ImagesScreen(viewModel: ChatViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    var images by remember { mutableStateOf(emptyList<File>()) }
    var preview by remember { mutableStateOf<File?>(null) }
    LaunchedEffect(Unit) { images = viewModel.attachments.listImages() }

    fun delete(file: File) {
        images -= file
        viewModel.deleteAttachments(listOf(file.name))
    }

    DetailScaffold("Uploaded Images", onBack) {
        if (images.isEmpty()) item { Text("No images.", Modifier.padding(16.dp)) }
        items(images, key = { it.name }) { file ->
            ListItem(
                leadingContent = {
                    AsyncImage(file, null, Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                },
                headlineContent = { Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = {
                    Text("${Formatter.formatShortFileSize(context, file.length())} • ${DateFormat.getDateFormat(context).format(Date(file.lastModified()))}")
                },
                trailingContent = { IconButton(onClick = { delete(file) }) { Icon(Icons.Outlined.Delete, "Delete") } },
                modifier = Modifier.clickable { preview = file },
            )
        }
    }

    preview?.let { file ->
        Dialog(onDismissRequest = { preview = null }) {
            Column {
                AsyncImage(file, "Image preview", Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Fit)
                Row {
                    TextButton(onClick = {
                        delete(file)
                        preview = null
                    }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { preview = null }) { Text("Done") }
                }
            }
        }
    }
}
