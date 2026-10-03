package com.localllm.chat.ui

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.localllm.chat.models.Message
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel, onMenu: () -> Unit) {
    val conversation = viewModel.activeConversation
    val messages = conversation?.messages.orEmpty()
    // Reversed so the newest message stays anchored to the bottom while it streams in.
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(conversation?.id, messages.size) { listState.animateScrollToItem(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(conversation?.title ?: "Local LLM Chat", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onMenu) { Icon(Icons.Default.Menu, "Chats") } },
            )
        },
        bottomBar = { ChatInput(viewModel, Modifier.navigationBarsPadding().imePadding()) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (messages.isEmpty()) {
                Text(
                    "Start a conversation",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            LazyColumn(
                state = listState,
                reverseLayout = true,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                messages.groupBy { it.day }.toList().asReversed().forEach { (day, group) ->
                    items(group.asReversed(), key = { it.id }) { MessageBubble(it, viewModel.attachments) }
                    item(key = day.toEpochDay()) { DayHeader(day) }
                }
            }
            AnimatedVisibility(
                visible = listState.canScrollBackward,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
            ) {
                SmallFloatingActionButton(onClick = { scope.launch { listState.animateScrollToItem(0) } }) {
                    Icon(Icons.Default.ArrowDownward, "Scroll to latest message")
                }
            }
        }
    }
}

@Composable
private fun DayHeader(day: LocalDate) {
    val millis = day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    Text(
        DateUtils.getRelativeTimeSpanString(millis, System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS).toString(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    )
}

private val Message.day: LocalDate
    get() = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
