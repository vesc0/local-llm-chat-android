package com.localllm.chat.ui

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.localllm.chat.models.InferenceEngine
import com.localllm.chat.services.LocalModel

@Composable
fun LocalModelsScreen(viewModel: ChatViewModel, onBack: () -> Unit) {
    val manager = viewModel.models
    val settings = viewModel.settings
    val context = LocalContext.current
    fun size(bytes: Long) = Formatter.formatShortFileSize(context, bytes)
    var repoId by rememberSaveable { mutableStateOf("") }
    var deleting by remember { mutableStateOf<LocalModel?>(null) }

    LaunchedEffect(Unit) {
        manager.clearStatus()
        manager.refresh()
    }

    DetailScaffold("Models", onBack) {
        section("Download")
        item {
            OutlinedTextField(
                value = repoId,
                onValueChange = { repoId = it },
                label = { Text("Hugging Face repo id") },
                placeholder = { Text("litert-community/Qwen2.5-1.5B-Instruct") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
        }
        item {
            if (manager.downloadJob != null) Column {
                val total = manager.totalBytes
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(
                        progress = { if (total > 0) manager.downloadedBytes.toFloat() / total else 0f },
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = manager::cancelDownload) {
                        Icon(Icons.Default.Cancel, "Cancel download", tint = MaterialTheme.colorScheme.error)
                    }
                }
                val progress = if (total > 0) " ${manager.downloadedBytes * 100 / total}% (${size(manager.downloadedBytes)} / ${size(total)})" else ""
                Text("Downloading…$progress", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
            } else {
                ListItem(
                    headlineContent = { Text("Download", color = if (repoId.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary) },
                    leadingContent = { Icon(Icons.Default.Download, null) },
                    supportingContent = if (manager.status.isEmpty()) null else { { Text(manager.status) } },
                    modifier = Modifier.clickable(enabled = repoId.isNotBlank()) {
                        viewModel.downloadModel(repoId)
                        repoId = ""
                    },
                )
            }
        }

        section("Downloaded")
        if (manager.models.isEmpty()) item { Text("No models downloaded yet.", Modifier.padding(16.dp)) }
        items(manager.models, key = { it.repoId }) { model ->
            val isSelected = model.repoId == settings.localModel && settings.engine == InferenceEngine.LiteRt
            ListItem(
                headlineContent = { Text(model.name, color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) },
                supportingContent = { Text("${model.namespace} · ${size(model.sizeBytes)}") },
                leadingContent = { if (isSelected) Icon(Icons.Default.CheckCircle, "Selected", tint = MaterialTheme.colorScheme.primary) },
                trailingContent = { IconButton(onClick = { deleting = model }) { Icon(Icons.Outlined.Delete, "Delete") } },
                modifier = Modifier.clickable {
                    viewModel.updateSettings { it.copy(localModel = model.repoId, engine = InferenceEngine.LiteRt) }
                    onBack()
                },
            )
        }
    }

    deleting?.let { model ->
        ConfirmDialog("Delete ${model.name}?", { viewModel.deleteModel(model) }) { deleting = null }
    }
}
