package com.localllm.chat.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.localllm.chat.models.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(viewModel: ChatViewModel, onDismiss: () -> Unit) {
    val settings by viewModel.settings.collectAsState()
    
    var host by remember { mutableStateOf(settings.ollamaHost) }
    var model by remember { mutableStateOf(settings.selectedModel) }
    var themeMode by remember { mutableStateOf(settings.themeMode) }

    var expandedTheme by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        title = { Text("Settings", style = androidx.compose.material3.MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("Ollama Host URL") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("Model Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                ExposedDropdownMenuBox(
                    expanded = expandedTheme,
                    onExpandedChange = { expandedTheme = !expandedTheme }
                ) {
                    OutlinedTextField(
                        readOnly = true,
                        value = themeMode.name,
                        onValueChange = { },
                        label = { Text("Theme") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedTheme) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedTheme,
                        onDismissRequest = { expandedTheme = false }
                    ) {
                        ThemeMode.values().forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(mode.name) },
                                onClick = {
                                    themeMode = mode
                                    expandedTheme = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    viewModel.updateSettings(
                        settings.copy(
                            ollamaHost = host.trim(),
                            selectedModel = model.trim(),
                            themeMode = themeMode
                        )
                    )
                    onDismiss()
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
            }
        }
    )
}
