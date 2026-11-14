package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.BookmarkEntity
import com.example.data.local.DownloadEntity
import com.example.data.local.HistoryEntity
import com.example.data.local.WorkspaceType
import com.example.ui.localization.LocalBrowserStrings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BookmarksScreen(
    bookmarks: List<BookmarkEntity>,
    onOpenUrl: (String) -> Unit,
    onDeleteBookmark: (Long) -> Unit,
    onClose: () -> Unit
) {
    BackHandler(onBack = onClose)
    val strings = LocalBrowserStrings.current
    LibraryScaffold(
        title = strings.bookmarks,
        onClose = onClose,
        emptyIcon = { Icon(Icons.Default.Bookmark, null, tint = MaterialTheme.colorScheme.primary) },
        emptyText = "No bookmarks saved yet.",
        isEmpty = bookmarks.isEmpty()
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(bookmarks, key = { it.id }) { bm ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onOpenUrl(bm.url)
                            onClose()
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Bookmark, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(bm.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(bm.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { onDeleteBookmark(bm.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete bookmark")
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            }
        }
    }
}

@Composable
fun HistoryScreen(
    workspace: WorkspaceType,
    history: List<HistoryEntity>,
    onOpenUrl: (String) -> Unit,
    onDeleteItem: (Long) -> Unit,
    onClearWorkspaceHistory: () -> Unit,
    onClose: () -> Unit
) {
    BackHandler(onBack = onClose)
    val strings = LocalBrowserStrings.current
    LibraryScaffold(
        title = "${strings.history} (${workspace.label})",
        onClose = onClose,
        trailingAction = {
            if (history.isNotEmpty()) {
                TextButton(onClick = onClearWorkspaceHistory) { Text("Clear") }
            }
        },
        emptyIcon = { Icon(Icons.Default.History, null, tint = MaterialTheme.colorScheme.primary) },
        emptyText = "No browsing history in ${workspace.label} workspace.",
        isEmpty = history.isEmpty()
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(history, key = { it.id }) { item ->
                val time = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(item.visitedAt))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onOpenUrl(item.url)
                            onClose()
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.History, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("$time • ${item.url}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { onDeleteItem(item.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete history entry")
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            }
        }
    }
}

@Composable
fun DownloadsScreen(
    downloads: List<DownloadEntity>,
    onDeleteDownload: (Long) -> Unit,
    onClose: () -> Unit
) {
    BackHandler(onBack = onClose)
    val strings = LocalBrowserStrings.current
    LibraryScaffold(
        title = strings.downloads,
        onClose = onClose,
        emptyIcon = { Icon(Icons.Default.Download, null, tint = MaterialTheme.colorScheme.primary) },
        emptyText = "No downloads recorded yet.",
        isEmpty = downloads.isEmpty()
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(downloads, key = { it.id }) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Download, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.fileName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${item.status} • ${item.url}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { onDeleteDownload(item.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove download record")
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            }
        }
    }
}

@Composable
private fun LibraryScaffold(
    title: String,
    onClose: () -> Unit,
    trailingAction: @Composable (() -> Unit)? = null,
    emptyIcon: @Composable () -> Unit,
    emptyText: String,
    isEmpty: Boolean,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose, modifier = Modifier.testTag("library_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Text(title, style = MaterialTheme.typography.titleLarge)
                }
                trailingAction?.invoke()
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
            if (isEmpty) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        emptyIcon()
                        Text(emptyText, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                content()
            }
        }
    }
}
