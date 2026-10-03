package com.localllm.chat.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch

@Composable
fun MainScreen(viewModel: ChatViewModel) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    Surface(modifier = Modifier.fillMaxSize()) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                Sidebar(
                    viewModel = viewModel,
                    onCloseDrawer = {
                        scope.launch { drawerState.close() }
                    }
                )
            }
        ) {
            ChatScreen(
                viewModel = viewModel,
                onOpenDrawer = {
                    scope.launch { drawerState.open() }
                }
            )
        }
    }
}
