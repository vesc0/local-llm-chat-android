package com.localllm.chat.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.localllm.chat.models.InferenceEngine
import com.localllm.chat.services.OllamaService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun OllamaModelsScreen(viewModel: ChatViewModel, onBack: () -> Unit) {
    val settings = viewModel.settings
    var models by remember { mutableStateOf<List<String>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var isConnecting by remember { mutableStateOf(false) }
    val canConnect = settings.ollamaHost.isNotBlank() && !isConnecting

    val scope = rememberCoroutineScope()
    fun connect() = scope.launch {
        isConnecting = true
        error = null
        try {
            models = OllamaService.fetchModels(viewModel.settings.ollamaHost)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The saved selection is left alone: a transient failure must not discard it.
            models = null
            error = e.message ?: e.toString()
        }
        isConnecting = false
    }
    LaunchedEffect(Unit) { if (canConnect) connect() }

    DetailScaffold("Ollama", onBack) {
        section("Server")
        item {
            OutlinedTextField(
                value = settings.ollamaHost,
                onValueChange = { host -> viewModel.updateSettings { it.copy(ollamaHost = host) } },
                label = { Text("Server address") },
                placeholder = { Text("http://192.168.1.10:11434") },
                supportingText = { Text("Start Ollama with OLLAMA_HOST=0.0.0.0 so it accepts connections from your network.") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { if (canConnect) connect() }),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
        }
        item {
            ListItem(
                headlineContent = { Text("Connect", color = if (canConnect) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) },
                leadingContent = { Icon(Icons.Default.Wifi, null) },
                trailingContent = { if (isConnecting) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) },
                modifier = Modifier.clickable(enabled = canConnect) { connect() },
            )
        }
        error?.let {
            item {
                Text(
                    "Could not reach Ollama. Check that it's running and that the address above is correct.\n\n$it",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        models?.let { models ->
            section("Models")
            if (models.isEmpty()) item { Text("No models found on the server.", Modifier.padding(16.dp)) }
            items(models) { model ->
                val isSelected = model == settings.selectedModel && settings.engine == InferenceEngine.Ollama
                ListItem(
                    headlineContent = { Text(model, color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) },
                    trailingContent = { if (isSelected) Icon(Icons.Default.CheckCircle, "Selected", tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable {
                        viewModel.updateSettings { it.copy(selectedModel = model, engine = InferenceEngine.Ollama) }
                        onBack()
                    },
                )
            }
        }
    }
}
