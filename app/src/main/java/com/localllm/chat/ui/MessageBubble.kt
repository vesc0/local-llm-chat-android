package com.localllm.chat.ui

import android.content.ClipData
import android.text.format.DateFormat
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.localllm.chat.models.Attachment
import com.localllm.chat.models.AttachmentType
import com.localllm.chat.models.Message
import com.localllm.chat.models.Role
import com.localllm.chat.models.TeX
import com.localllm.chat.models.ThoughtParser
import com.localllm.chat.services.AttachmentStore
import com.mikepenz.markdown.compose.LocalMarkdownColors
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownCodeBackground
import com.mikepenz.markdown.compose.elements.MarkdownCodeFence
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.elements.MarkdownCheckBox
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.model.markdownAnnotator
import com.mikepenz.markdown.model.markdownAnnotatorConfig
import com.mikepenz.markdown.model.rememberMarkdownState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date

@Composable
fun MessageBubble(message: Message, attachments: AttachmentStore) {
    val isUser = message.role == Role.User
    val parsed = remember(message.content) { ThoughtParser.parse(message.content) }
    val colors = MaterialTheme.colorScheme

    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp, if (isUser) Alignment.End else Alignment.Start),
        modifier = Modifier.fillMaxWidth().padding(start = if (isUser) 40.dp else 0.dp),
    ) {
        if (!isUser) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(28.dp).background(colors.onSurface.copy(alpha = 0.1f), CircleShape).padding(6.dp),
            )
        }
        Column(horizontalAlignment = if (isUser) Alignment.End else Alignment.Start, modifier = Modifier.weight(1f, fill = !isUser)) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = if (isUser) Modifier.background(colors.primary, RoundedCornerShape(18.dp)).padding(14.dp) else Modifier,
            ) {
                if (message.isStreaming && message.content.isEmpty()) TypingIndicator()
                if (message.attachments.isNotEmpty()) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        message.attachments.forEach {
                            AttachmentThumbnail(it, attachments, if (it.type == AttachmentType.Image) 150.dp else 80.dp)
                        }
                    }
                }
                parsed.thought?.let { ThoughtSection(it, message, hasAnswer = parsed.answer.isNotEmpty()) }
                if (parsed.answer.isNotEmpty()) {
                    SelectionContainer { MarkdownText(parsed.answer, if (isUser) colors.onPrimary else colors.onSurface) }
                }
                if (message.isCancelled) Text("(canceled)", style = MaterialTheme.typography.bodySmall, color = colors.error)
                message.errorMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.error) }
            }
            if (!message.isStreaming || message.content.isNotEmpty()) Footer(message, parsed.answer, isUser)
        }
    }
}

private val annotator = markdownAnnotator(markdownAnnotatorConfig(eolAsNewLine = true))

private val components = markdownComponents(
    codeFence = { model ->
        MarkdownCodeFence(model.content, model.node, model.typography.code) { code, language, style ->
            MarkdownCodeBackground(
                LocalMarkdownColors.current.codeBackground,
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                RoundedCornerShape(8.dp),
                showHeader = true,
                language = language,
                code = code,
            ) {
                Text(code, Modifier.horizontalScroll(rememberScrollState()).padding(8.dp), LocalMarkdownColors.current.text, style = style)
            }
        }
    },
    checkbox = { MarkdownCheckBox(it.content, it.node, it.typography.text) },
)

@Composable
private fun MarkdownText(text: String, color: Color) = Markdown(
    // Retained so a streaming reply doesn't flash empty while it is re-parsed.
    rememberMarkdownState(remember(text) { TeX.substitute(text) }, retainState = true),
    colors = markdownColor(text = color),
    annotator = annotator,
    components = components,
    modifier = Modifier,
)

@Composable
private fun ThoughtSection(thought: String, message: Message, hasAnswer: Boolean) {
    var isExpanded by rememberSaveable(message.id) { mutableStateOf(false) }
    val isThinking = message.isStreaming && !hasAnswer
    Column(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .clickable { isExpanded = !isExpanded }
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .animateContentSize(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Outlined.Psychology, null, Modifier.size(18.dp))
            Text(
                when {
                    isThinking -> "Thinking…"
                    message.thoughtMillis != null -> "Thought for %.1fs".format(message.thoughtMillis / 1000.0)
                    else -> "Thought Process"
                },
                style = MaterialTheme.typography.labelLarge,
                modifier = if (isThinking) pulse().let { alpha -> Modifier.graphicsLayer { this.alpha = alpha.value } } else Modifier,
            )
            Icon(if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, Modifier.size(18.dp))
        }
        if (isExpanded) MarkdownText(thought, MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Footer(message: Message, answer: String, isUser: Boolean) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var isCopied by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            DateFormat.getTimeFormat(context).format(Date(message.timestamp)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!isUser) {
            IconButton(
                onClick = {
                    scope.launch {
                        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Message", answer)))
                        isCopied = true
                        delay(2000)
                        isCopied = false
                    }
                },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy, "Copy message", Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun AttachmentThumbnail(attachment: Attachment, store: AttachmentStore, size: Dp) {
    val modifier = Modifier.size(size).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
    when (attachment.type) {
        AttachmentType.Image -> AsyncImage(
            model = attachment.filename?.let(store::file),
            contentDescription = "Attached image",
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
        AttachmentType.Pdf, AttachmentType.Text -> Box(modifier, contentAlignment = Alignment.Center) {
            Icon(
                if (attachment.type == AttachmentType.Pdf) Icons.Outlined.PictureAsPdf else Icons.Outlined.Description,
                contentDescription = "Attached document",
                tint = if (attachment.type == AttachmentType.Pdf) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TypingIndicator() {
    Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) {
            val alpha = pulse(it * 200)
            Box(Modifier.size(6.dp).graphicsLayer { this.alpha = alpha.value }.background(MaterialTheme.colorScheme.onSurface, CircleShape))
        }
    }
}

@Composable
private fun pulse(delayMillis: Int = 0) = rememberInfiniteTransition(label = "pulse").animateFloat(
    initialValue = 0.3f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse, StartOffset(delayMillis)),
    label = "alpha",
)
