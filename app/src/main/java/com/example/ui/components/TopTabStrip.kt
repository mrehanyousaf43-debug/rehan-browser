package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.TabEntity
import com.example.data.local.WorkspaceType
import com.example.ui.localization.LocalBrowserStrings
import com.example.ui.theme.IncognitoAccent

@Composable
fun TopTabStrip(
    activeWorkspace: WorkspaceType,
    tabs: List<TabEntity>,
    activeTabId: String,
    onSelectWorkspace: (WorkspaceType) -> Unit,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onNewTab: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalBrowserStrings.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Workspace switcher strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WorkspaceType.entries.forEach { ws ->
                val label = when (ws) {
                    WorkspaceType.RESEARCH -> strings.research
                    WorkspaceType.EDITING -> strings.editing
                    WorkspaceType.ENTERTAINMENT -> strings.entertainment
                }
                val selected = ws == activeWorkspace
                FilterChip(
                    selected = selected,
                    onClick = { onSelectWorkspace(ws) },
                    label = {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier
                        .height(32.dp)
                        .testTag("workspace_${ws.name.lowercase()}")
                )
            }
        }

        // Open Tabs Horizontal Tab Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(tabs, key = { it.id }) { tab ->
                    val isSelected = tab.id == activeTabId
                    val groupColor = tab.groupColorHex?.let { Color(it) }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = when {
                            isSelected && tab.isIncognito -> IncognitoAccent.copy(alpha = 0.22f)
                            isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                        },
                        modifier = Modifier
                            .height(36.dp)
                            .widthIn(min = if (tab.isPinned) 56.dp else 110.dp, max = 168.dp)
                            .then(
                                if (groupColor != null) {
                                    Modifier.border(1.dp, groupColor, RoundedCornerShape(12.dp))
                                } else Modifier
                            )
                            .clickable { onSelectTab(tab.id) }
                            .testTag("tab_strip_${tab.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (groupColor != null) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(groupColor)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                            }
                            if (tab.isIncognito) {
                                Icon(
                                    imageVector = Icons.Default.VisibilityOff,
                                    contentDescription = "Incognito",
                                    tint = IncognitoAccent,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            if (tab.isPinned) {
                                Icon(
                                    imageVector = Icons.Default.PushPin,
                                    contentDescription = "Pinned",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                text = tab.title.ifBlank { "New Tab" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (!tab.isPinned) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close tab",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .size(15.dp)
                                        .clickable { onCloseTab(tab.id) }
                                )
                            }
                        }
                    }
                }
            }

            IconButton(
                onClick = onNewTab,
                modifier = Modifier
                    .size(40.dp)
                    .padding(end = 4.dp)
                    .testTag("strip_new_tab_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = strings.newTab,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
