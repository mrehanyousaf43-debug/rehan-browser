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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.TabEntity
import com.example.data.local.WorkspaceType
import com.example.ui.localization.LocalBrowserStrings
import com.example.ui.theme.IncognitoAccent
import com.example.ui.theme.TabGroupColors

@Composable
fun TabSwitcherSheet(
    workspace: WorkspaceType,
    tabs: List<TabEntity>,
    activeTabId: String,
    isOrganizing: Boolean,
    onSelectWorkspace: (WorkspaceType) -> Unit,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onAddTab: (incognito: Boolean) -> Unit,
    onTogglePin: (TabEntity) -> Unit,
    onMoveTab: (String, Int) -> Unit,
    onAssignGroup: (TabEntity, String?, Long?) -> Unit,
    onAiOrganizeTabs: () -> Unit,
    onClose: () -> Unit
) {
    androidx.activity.compose.BackHandler(onBack = onClose)
    val strings = LocalBrowserStrings.current
    var groupingTab by remember { mutableStateOf<TabEntity?>(null) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose, modifier = Modifier.testTag("tab_switcher_close")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Text(
                        text = "${workspace.label} • ${tabs.size} ${strings.tabs}",
                        style = MaterialTheme.typography.titleLarge
                    )
                }

                FilledTonalButton(
                    onClick = onAiOrganizeTabs,
                    enabled = !isOrganizing && tabs.size >= 2,
                    modifier = Modifier.testTag("ai_organize_tabs_button")
                ) {
                    if (isOrganizing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(strings.aiOrganizeTabs)
                }
            }

            // Workspace Tabs Switcher
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WorkspaceType.entries.forEach { ws ->
                    val selected = ws == workspace
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSelectWorkspace(ws) }
                    ) {
                        Text(
                            text = ws.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Grid of Tab Cards
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                itemsIndexed(tabs, key = { _, t -> t.id }) { index, tab ->
                    val isSelected = tab.id == activeTabId
                    val groupColor = tab.groupColorHex?.let { Color(it) }
                    val borderColor = when {
                        isSelected -> MaterialTheme.colorScheme.primary
                        groupColor != null -> groupColor
                        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    }
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = if (tab.isIncognito) Color(0xFF1E1333) else MaterialTheme.colorScheme.surface,
                        tonalElevation = if (isSelected) 6.dp else 2.dp,
                        modifier = Modifier
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = borderColor,
                                shape = RoundedCornerShape(18.dp)
                            )
                            .clickable { onSelectTab(tab.id) }
                            .testTag("grid_tab_${tab.id}")
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            // Top row: Group badge / Incognito + Close
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    if (tab.isIncognito) {
                                        Icon(
                                            Icons.Default.VisibilityOff,
                                            contentDescription = "Incognito",
                                            tint = IncognitoAccent,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    if (!tab.groupName.isNullOrBlank()) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = (groupColor ?: MaterialTheme.colorScheme.primary).copy(alpha = 0.2f)
                                        ) {
                                            Text(
                                                text = tab.groupName,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = groupColor ?: MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                                IconButton(
                                    onClick = { onCloseTab(tab.id) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Close tab",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = tab.title.ifBlank { "New Tab" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (tab.url == "about:newtab") "New Tab Page" else tab.url,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Action controls: Pin, Group, Reorder Left/Right
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { onTogglePin(tab) },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        Icons.Default.PushPin,
                                        contentDescription = if (tab.isPinned) "Unpin" else "Pin",
                                        tint = if (tab.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { groupingTab = tab },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Folder,
                                        contentDescription = "Group tab",
                                        tint = groupColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { onMoveTab(tab.id, -1) },
                                    enabled = index > 0,
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Move tab earlier",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { onMoveTab(tab.id, 1) },
                                    enabled = index < tabs.lastIndex,
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = "Move tab later",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Action Buttons: New Tab + Incognito Tab
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { onAddTab(false) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("grid_new_tab_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(strings.newTab)
                }
                OutlinedButton(
                    onClick = { onAddTab(true) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("grid_incognito_tab_button")
                ) {
                    Icon(Icons.Default.VisibilityOff, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(strings.incognitoTab)
                }
            }
        }
    }

    groupingTab?.let { targetTab ->
        var groupName by remember(targetTab) { mutableStateOf(targetTab.groupName.orEmpty()) }
        var selectedColor by remember(targetTab) { mutableLongStateOf(targetTab.groupColorHex ?: TabGroupColors.first()) }
        AlertDialog(
            onDismissRequest = { groupingTab = null },
            title = { Text("Assign Tab Group") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = groupName,
                        onValueChange = { groupName = it },
                        label = { Text("Group name (leave empty to ungroup)") },
                        singleLine = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TabGroupColors.forEach { hex ->
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(Color(hex))
                                    .then(
                                        if (selectedColor == hex) {
                                            Modifier.border(2.5.dp, Color.White, CircleShape)
                                        } else Modifier
                                    )
                                    .clickable { selectedColor = hex }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onAssignGroup(targetTab, groupName.trim(), selectedColor)
                        groupingTab = null
                    }
                ) { Text("Apply") }
            },
            dismissButton = {
                TextButton(onClick = { groupingTab = null }) { Text("Cancel") }
            }
        )
    }
}
