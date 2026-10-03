package com.localllm.chat.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch

@Composable
fun App(viewModel: ChatViewModel) {
    val navController = rememberNavController()
    val back: () -> Unit = { navController.navigateUp() }
    NavHost(navController, startDestination = "chat") {
        composable("chat") { MainScreen(viewModel) { navController.navigate("settings") } }
        composable("settings") { SettingsScreen(viewModel, { navController.navigate(it) }, back) }
        composable("ollama") { OllamaModelsScreen(viewModel, back) }
        composable("models") { LocalModelsScreen(viewModel, back) }
        composable("images") { ImagesScreen(viewModel, back) }
    }
}

@Composable
private fun MainScreen(viewModel: ChatViewModel, onSettings: () -> Unit) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = { Sidebar(viewModel, onSettings) { scope.launch { drawerState.close() } } },
    ) {
        ChatScreen(viewModel) { scope.launch { drawerState.open() } }
    }
}

/** A screen of list rows with a back button, shared by the settings screens. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScaffold(title: String, onBack: () -> Unit, content: LazyListScope.() -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
    ) { padding ->
        LazyColumn(contentPadding = padding, content = content)
    }
}

fun LazyListScope.section(title: String) = item {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
fun NavigationRow(title: String, onClick: () -> Unit) = ListItem(
    headlineContent = { Text(title) },
    trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
    modifier = Modifier.clickable(onClick = onClick),
)

@Composable
fun ConfirmDialog(title: String, onConfirm: () -> Unit, onDismiss: () -> Unit) = AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    confirmButton = {
        TextButton(onClick = { onConfirm(); onDismiss() }) {
            Text("Delete", color = MaterialTheme.colorScheme.error)
        }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
)
