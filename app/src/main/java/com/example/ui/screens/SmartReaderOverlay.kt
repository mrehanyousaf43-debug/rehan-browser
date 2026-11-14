package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.TtsUiState
import com.example.ui.viewmodel.SmartReaderUiState

@Composable
fun SmartReaderOverlay(
    readerState: SmartReaderUiState,
    ttsState: TtsUiState,
    onToggleTts: () -> Unit,
    onSetRate: (Float) -> Unit,
    onSetFontSize: (Int) -> Unit,
    onSetTheme: (Int) -> Unit,
    onClose: () -> Unit
) {
    BackHandler(onBack = onClose)

    val bgColor = when (readerState.readerThemeIndex) {
        1 -> Color(0xFFF6EED9) // Sepia
        2 -> Color(0xFFFAFAFA) // Light
        else -> Color(0xFF0B111E) // Dark
    }
    val fgColor = when (readerState.readerThemeIndex) {
        1 -> Color(0xFF3E3221)
        2 -> Color(0xFF111827)
        else -> Color(0xFFE2E8F0)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = bgColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
        ) {
            // Top Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose, modifier = Modifier.testTag("reader_close_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Exit Reader", tint = fgColor)
                    }
                    Text(
                        text = "Smart Reader",
                        style = MaterialTheme.typography.titleMedium,
                        color = fgColor
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onSetFontSize(readerState.fontSizeSp - 2) }) {
                        Icon(Icons.Default.Remove, contentDescription = "Decrease font", tint = fgColor)
                    }
                    Text("${readerState.fontSizeSp}sp", color = fgColor, style = MaterialTheme.typography.labelSmall)
                    IconButton(onClick = { onSetFontSize(readerState.fontSizeSp + 2) }) {
                        Icon(Icons.Default.Add, contentDescription = "Increase font", tint = fgColor)
                    }
                }
            }

            // Theme + TTS Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Dark", "Sepia", "Light").forEachIndexed { idx, label ->
                        FilterChip(
                            selected = readerState.readerThemeIndex == idx,
                            onClick = { onSetTheme(idx) },
                            label = { Text(label) }
                        )
                    }
                }

                FilledTonalButton(
                    onClick = onToggleTts,
                    enabled = readerState.content.isNotBlank(),
                    modifier = Modifier.testTag("reader_tts_button")
                ) {
                    Icon(
                        imageVector = if (ttsState.isSpeaking) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = if (ttsState.isSpeaking) "Stop reading" else "Read aloud"
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (ttsState.isSpeaking) "Stop" else "Listen")
                }
            }

            // Speech Rate Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Speed:", color = fgColor.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                listOf(0.8f, 1.0f, 1.25f, 1.5f).forEach { rate ->
                    FilterChip(
                        selected = ttsState.speechRate == rate,
                        onClick = { onSetRate(rate) },
                        label = { Text("${rate}x") }
                    )
                }
            }

            // Content Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when {
                    readerState.isLoading -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator()
                            Text("Formatting article with Gemini AI...", color = fgColor)
                        }
                    }
                    readerState.error != null -> {
                        Text(
                            text = readerState.error,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp)
                        )
                    }
                    else -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .widthIn(max = 680.dp)
                                .align(Alignment.TopCenter)
                                .padding(horizontal = 24.dp, vertical = 16.dp)
                        ) {
                            Text(
                                text = readerState.title,
                                style = MaterialTheme.typography.headlineMedium,
                                color = fgColor,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = readerState.content,
                                color = fgColor.copy(alpha = 0.92f),
                                fontSize = readerState.fontSizeSp.sp,
                                lineHeight = (readerState.fontSizeSp * 1.55f).sp
                            )
                            Spacer(modifier = Modifier.height(32.dp))
                        }
                    }
                }
            }
        }
    }
}
