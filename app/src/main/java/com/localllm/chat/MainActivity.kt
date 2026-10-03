package com.localllm.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.localllm.chat.models.ThemeMode
import com.localllm.chat.ui.ChatViewModel
import com.localllm.chat.ui.MainScreen
import com.localllm.chat.ui.theme.LocalLLMChatTheme

class MainActivity : ComponentActivity() {
    private val viewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val settings by viewModel.settings.collectAsState()
            
            val isDarkTheme = when (settings.themeMode) {
                ThemeMode.Dark -> true
                ThemeMode.Light -> false
                ThemeMode.Auto -> isSystemInDarkTheme()
            }

            LocalLLMChatTheme(darkTheme = isDarkTheme) {
                MainScreen(viewModel = viewModel)
            }
        }
    }
}
