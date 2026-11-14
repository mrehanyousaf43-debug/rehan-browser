package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ai.AiConfig
import com.example.ui.localization.LocalBrowserStrings
import com.example.ui.viewmodel.AiChatMessage
import com.example.ui.viewmodel.AiPanelUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiSidePanelSheet(
    pageTitle: String,
    pageUrl: String,
    uiState: AiPanelUiState,
    chatHistory: List<AiChatMessage>,
    onQuickAction: (actionType: String, targetLang: String) -> Unit,
    onAskQuestion: (String) -> Unit,
    onClearChat: () -> Unit,
    onClose: () -> Unit
) {
    BackHandler(onBack = onClose)
    val strings = LocalBrowserStrings.current
    var question by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .fillMaxWidth(0.92f)
                .widthIn(max = 460.dp)
                .systemBarsPadding()
                .imePadding(),
            shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(strings.aiAssistant, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = AiConfig.DEFAULT_MODEL,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Row {
                        IconButton(onClick = onClearChat, modifier = Modifier.testTag("ai_clear_chat")) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear AI chat")
                        }
                        IconButton(onClick = onClose, modifier = Modifier.testTag("ai_panel_close")) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                }

                Text(
                    text = if (pageUrl == "about:newtab") "New Tab" else "$pageTitle ($pageUrl)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                // Quick Action Chips
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    AssistChip(
                        onClick = { onQuickAction("summarize", "") },
                        label = { Text(strings.summarize) },
                        leadingIcon = { Icon(Icons.Default.Summarize, null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("ai_summarize_chip")
                    )
                    AssistChip(
                        onClick = { onQuickAction("explain", "") },
                        label = { Text(strings.explainSimply) },
                        leadingIcon = { Icon(Icons.Default.Lightbulb, null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("ai_explain_chip")
                    )
                    AssistChip(
                        onClick = { onQuickAction("translate", "Urdu") },
                        label = { Text("Urdu (اردو)") },
                        leadingIcon = { Icon(Icons.Default.Translate, null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("ai_translate_urdu_chip")
                    )
                    AssistChip(
                        onClick = { onQuickAction("translate", "Hindi") },
                        label = { Text("Hindi (हिन्दी)") },
                        leadingIcon = { Icon(Icons.Default.Translate, null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("ai_translate_hindi_chip")
                    )
                    AssistChip(
                        onClick = { onQuickAction("translate", "English") },
                        label = { Text("English") },
                        leadingIcon = { Icon(Icons.Default.Translate, null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("ai_translate_english_chip")
                    )
                }

                // Conversation & Streaming Output
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (chatHistory.isEmpty() && uiState is AiPanelUiState.Idle) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Tap Summarize, Explain Simply, Translate to Urdu/Hindi/English, or ask any question about this page below.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    }

                    items(chatHistory) { msg ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (msg.isUser) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = msg.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (msg.isUser) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    when (uiState) {
                        is AiPanelUiState.Loading -> {
                            item {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.padding(8.dp)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Text("Gemini is reading the page...", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        is AiPanelUiState.Streaming -> {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = uiState.text,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }
                        }
                        is AiPanelUiState.Error -> {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.16f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = uiState.message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }
                        }
                        else -> {}
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Ask Question Input
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = question,
                        onValueChange = { question = it },
                        placeholder = { Text(strings.askAboutPage, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ai_question_input")
                    )
                    IconButton(
                        onClick = {
                            if (question.isNotBlank()) {
                                val q = question.trim()
                                question = ""
                                onAskQuestion(q)
                            }
                        },
                        modifier = Modifier.testTag("ai_send_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Ask Gemini",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
