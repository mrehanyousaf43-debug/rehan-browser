package com.example.data.repository

import com.example.data.local.BookmarkEntity
import com.example.data.local.BrowserDatabase
import com.example.data.local.BrowserPreferencesState
import com.example.data.local.DownloadEntity
import com.example.data.local.HistoryEntity
import com.example.data.local.PrivacyPreferences
import com.example.data.local.SiteSettingsEntity
import com.example.data.local.SpeedDialEntity
import com.example.data.local.TabEntity
import com.example.data.local.WorkspaceType
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class BrowserRepository(
    private val db: BrowserDatabase,
    val preferences: PrivacyPreferences
) {
    val prefsFlow: Flow<BrowserPreferencesState> = preferences.preferencesFlow
    val allTabsFlow: Flow<List<TabEntity>> = db.tabDao().observeAllTabs()
    val bookmarksFlow: Flow<List<BookmarkEntity>> = db.bookmarkDao().observeBookmarks()
    val allHistoryFlow: Flow<List<HistoryEntity>> = db.historyDao().observeAllHistory()
    val downloadsFlow: Flow<List<DownloadEntity>> = db.downloadDao().observeDownloads()
    val speedDialsFlow: Flow<List<SpeedDialEntity>> = db.speedDialDao().observeSpeedDials()
    val siteSettingsFlow: Flow<List<SiteSettingsEntity>> = db.siteSettingsDao().observeAllSiteSettings()

    fun observeTabsForWorkspace(workspace: WorkspaceType): Flow<List<TabEntity>> =
        db.tabDao().observeTabsForWorkspace(workspace.name)

    fun observeHistoryForWorkspace(workspace: WorkspaceType): Flow<List<HistoryEntity>> =
        db.historyDao().observeHistoryForWorkspace(workspace.name)

    suspend fun ensureDefaultsSeeded() {
        if (db.speedDialDao().count() == 0) {
            db.speedDialDao().insertAll(
                listOf(
                    SpeedDialEntity(title = "DuckDuckGo", url = "https://duckduckgo.com", accentHex = 0xFFDE5833, position = 0),
                    SpeedDialEntity(title = "Wikipedia", url = "https://en.wikipedia.org", accentHex = 0xFF00E5FF, position = 1),
                    SpeedDialEntity(title = "GitHub", url = "https://github.com", accentHex = 0xFF818CF8, position = 2),
                    SpeedDialEntity(title = "YouTube", url = "https://m.youtube.com", accentHex = 0xFFF43F5E, position = 3),
                    SpeedDialEntity(title = "BBC Urdu", url = "https://www.bbc.com/urdu", accentHex = 0xFF10B981, position = 4),
                    SpeedDialEntity(title = "Reuters", url = "https://www.reuters.com", accentHex = 0xFFF59E0B, position = 5),
                    SpeedDialEntity(title = "MDN Docs", url = "https://developer.mozilla.org", accentHex = 0xFF38BDF8, position = 6),
                    SpeedDialEntity(title = "Internet Archive", url = "https://archive.org", accentHex = 0xFFA78BFA, position = 7)
                )
            )
        }
        // Ensure each workspace has at least one initial tab
        for (workspace in WorkspaceType.entries) {
            val existing = db.tabDao().getTabsForWorkspace(workspace.name)
            if (existing.isEmpty()) {
                val tab = TabEntity(
                    id = UUID.randomUUID().toString(),
                    workspace = workspace.name,
                    url = "about:newtab",
                    title = "New Tab",
                    position = 0
                )
                db.tabDao().upsertTab(tab)
            }
        }
    }

    suspend fun upsertTab(tab: TabEntity) = db.tabDao().upsertTab(tab)

    suspend fun upsertTabs(tabs: List<TabEntity>) = db.tabDao().upsertTabs(tabs)

    suspend fun deleteTab(tabId: String) = db.tabDao().deleteTab(tabId)

    suspend fun deleteIncognitoTabs() = db.tabDao().deleteAllIncognitoTabs()

    suspend fun toggleBookmark(title: String, url: String, workspace: WorkspaceType): Boolean {
        if (url.isBlank() || url == "about:newtab") return false
        return if (db.bookmarkDao().isBookmarked(url)) {
            db.bookmarkDao().deleteByUrl(url)
            false
        } else {
            db.bookmarkDao().insert(
                BookmarkEntity(title = title.ifBlank { url }, url = url, workspace = workspace.name)
            )
            true
        }
    }

    suspend fun addBookmark(title: String, url: String, workspace: WorkspaceType) {
        db.bookmarkDao().insert(BookmarkEntity(title = title.ifBlank { url }, url = url, workspace = workspace.name))
    }

    suspend fun deleteBookmark(id: Long) = db.bookmarkDao().deleteById(id)

    suspend fun recordHistory(title: String, url: String, workspace: WorkspaceType) {
        if (url.isBlank() || url == "about:newtab" || url.startsWith("about:")) return
        db.historyDao().insert(
            HistoryEntity(
                title = title.ifBlank { url },
                url = url,
                workspace = workspace.name,
                visitedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun searchHistory(workspace: WorkspaceType, query: String): List<HistoryEntity> =
        db.historyDao().searchHistory(workspace.name, query)

    suspend fun deleteHistoryItem(id: Long) = db.historyDao().deleteById(id)

    suspend fun clearHistory(workspace: WorkspaceType?) {
        if (workspace == null) db.historyDao().clearAllHistory()
        else db.historyDao().clearWorkspaceHistory(workspace.name)
    }

    suspend fun recordDownload(download: DownloadEntity): Long = db.downloadDao().insert(download)

    suspend fun updateDownloadStatus(id: Long, status: String) = db.downloadDao().updateStatus(id, status)

    suspend fun deleteDownload(id: Long) = db.downloadDao().deleteById(id)

    suspend fun clearDownloads() = db.downloadDao().clearAll()

    suspend fun addSpeedDial(title: String, url: String, accentHex: Long = 0xFF00E5FF) {
        val count = db.speedDialDao().count()
        db.speedDialDao().insert(SpeedDialEntity(title = title, url = url, accentHex = accentHex, position = count))
    }

    suspend fun deleteSpeedDial(id: Long) = db.speedDialDao().deleteById(id)

    suspend fun setSiteAdBlockEnabled(host: String, enabled: Boolean) {
        if (host.isBlank()) return
        db.siteSettingsDao().upsert(SiteSettingsEntity(host = host.lowercase(), adBlockEnabled = enabled))
    }

    suspend fun isSiteAdBlockEnabled(host: String): Boolean {
        if (host.isBlank()) return true
        return db.siteSettingsDao().getForHost(host.lowercase())?.adBlockEnabled ?: true
    }
}
