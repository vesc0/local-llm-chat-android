package com.localllm.chat.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.localllm.chat.models.InferenceEngine
import com.localllm.chat.models.ThemeMode
import com.localllm.chat.services.GeminiNanoService
import com.localllm.chat.services.OllamaService
import kotlinx.coroutines.CancellationException

@Composable
fun SettingsScreen(viewModel: ChatViewModel, onNavigate: (String) -> Unit, onBack: () -> Unit) {
    val settings = viewModel.settings
    var confirmingClear by remember { mutableStateOf(false) }
    var nanoReason by remember { mutableStateOf<String?>(null) }

    // A model counts as active only while it can be used: downloaded for LiteRT,
    // served by a reachable host for Ollama. The stored selection is never discarded.
    val isAvailable by produceState<Boolean?>(null, settings.engine, settings.ollamaHost, settings.selectedModel, settings.localModel) {
        value = null
        value = when (settings.engine) {
            InferenceEngine.Ollama -> runCatching { settings.selectedModel in OllamaService.fetchModels(settings.ollamaHost) }
                .getOrElse { if (it is CancellationException) throw it else false }
            InferenceEngine.LiteRt -> viewModel.models.file(settings.localModel) != null
            InferenceEngine.GeminiNano -> GeminiNanoService.status().also { nanoReason = it.second }.first
        }
    }

    DetailScaffold("Settings", onBack) {
        section("Model")
        item {
            Choices(InferenceEngine.entries, settings.engine, { it.label }) { engine -> viewModel.updateSettings { it.copy(engine = engine) } }
        }
        item {
            ListItem(
                headlineContent = { Text("Active Model") },
                trailingContent = {
                    when (isAvailable) {
                        null -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        true -> Text(settings.activeModelName.orEmpty(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        false -> Text("None", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    }
                },
            )
        }
        item {
            when (settings.engine) {
                InferenceEngine.Ollama -> NavigationRow("Select Ollama Model") { onNavigate("ollama") }
                InferenceEngine.LiteRt -> NavigationRow("Manage Models") { onNavigate("models") }
                InferenceEngine.GeminiNano -> nanoReason?.let { ListItem(headlineContent = { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }) }
            }
        }

        section("Storage")
        item { NavigationRow("Manage Uploaded Images") { onNavigate("images") } }
        item {
            ListItem(
                headlineContent = { Text("Delete All Downloaded Models", color = MaterialTheme.colorScheme.error) },
                modifier = Modifier.clickable { confirmingClear = true },
            )
        }

        section("Appearance")
        item {
            Choices(ThemeMode.entries, settings.themeMode, { it.name }) { mode -> viewModel.updateSettings { it.copy(themeMode = mode) } }
        }
    }

    if (confirmingClear) {
        ConfirmDialog("Delete all downloaded models?", viewModel::deleteAllModels) { confirmingClear = false }
    }
}

@Composable
private fun <T> Choices(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) { Text(label(option)) }
        }
    }
}
