package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.localization.LocalBrowserStrings
import com.example.ui.viewmodel.BrowserOverlay

private data class MenuAction(
    val title: String,
    val icon: ImageVector,
    val tag: String,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserMenuSheet(
    isDarkTheme: Boolean,
    onToggleTheme: (Boolean) -> Unit,
    onNewTab: () -> Unit,
    onIncognitoTab: () -> Unit,
    onBookmarkPage: () -> Unit,
    onFindInPage: () -> Unit,
    onShare: () -> Unit,
    onScreenshot: () -> Unit,
    onOpenReader: () -> Unit,
    onOpenWritingHelper: () -> Unit,
    onNavigateOverlay: (BrowserOverlay) -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalBrowserStrings.current
    val actions = listOf(
        MenuAction(strings.newTab, Icons.Default.Add, "menu_new_tab", onNewTab),
        MenuAction(strings.incognitoTab, Icons.Default.VisibilityOff, "menu_incognito", onIncognitoTab),
        MenuAction("Bookmark", Icons.Default.BookmarkAdd, "menu_bookmark_page", onBookmarkPage),
        MenuAction(strings.bookmarks, Icons.Default.Bookmarks, "menu_bookmarks") { onNavigateOverlay(BrowserOverlay.Bookmarks) },
        MenuAction(strings.history, Icons.Default.History, "menu_history") { onNavigateOverlay(BrowserOverlay.History) },
        MenuAction(strings.downloads, Icons.Default.Download, "menu_downloads") { onNavigateOverlay(BrowserOverlay.Downloads) },
        MenuAction(strings.findInPage, Icons.Default.FindInPage, "menu_find", onFindInPage),
        MenuAction(strings.share, Icons.Default.Share, "menu_share", onShare),
        MenuAction(strings.fullPageScreenshot, Icons.Default.Screenshot, "menu_screenshot", onScreenshot),
        MenuAction(strings.smartReader, Icons.AutoMirrored.Filled.MenuBook, "menu_reader", onOpenReader),
        MenuAction(strings.writingHelper, Icons.Default.EditNote, "menu_writing_helper", onOpenWritingHelper),
        MenuAction(strings.aiAssistant, Icons.Default.AutoAwesome, "menu_ai") { onNavigateOverlay(BrowserOverlay.AiSidePanel) },
        MenuAction(strings.adBlocker, Icons.Default.Shield, "menu_adblock") { onNavigateOverlay(BrowserOverlay.AdBlockDashboard) },
        MenuAction(strings.vpnTitle, Icons.Default.VpnKey, "menu_vpn") { onNavigateOverlay(BrowserOverlay.VpnPanel) },
        MenuAction(strings.privacySettings, Icons.Default.PrivacyTip, "menu_privacy") { onNavigateOverlay(BrowserOverlay.PrivacySettings) },
        MenuAction(strings.aboutApp, Icons.Default.Info, "menu_about") { onNavigateOverlay(BrowserOverlay.About) }
    )

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(strings.darkTheme, style = MaterialTheme.typography.titleMedium)
                }
                Switch(
                    checked = isDarkTheme,
                    onCheckedChange = onToggleTheme,
                    modifier = Modifier.testTag("menu_dark_theme_switch")
                )
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
            ) {
                items(actions) { action ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier
                            .clickable {
                                onDismiss()
                                action.onClick()
                            }
                            .testTag(action.tag)
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 12.dp, horizontal = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = action.icon,
                                contentDescription = action.title,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = action.title,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
