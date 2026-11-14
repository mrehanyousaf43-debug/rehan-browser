package com.example.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Patterns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.adblock.AdBlockEngine
import com.example.adblock.FilterListStatus
import com.example.adblock.PageBlockStats
import com.example.ai.GeminiBrowserService
import com.example.ai.SmartReaderTtsManager
import com.example.ai.TtsUiState
import com.example.ai.VoiceCommandManager
import com.example.ai.VoiceRecognitionState
import com.example.browser.BrowserDownloadHelper
import com.example.browser.CapturedScreenshot
import com.example.browser.WebViewPool
import com.example.data.local.BookmarkEntity
import com.example.data.local.BrowserDatabase
import com.example.data.local.BrowserPreferencesState
import com.example.data.local.DownloadEntity
import com.example.data.local.HistoryEntity
import com.example.data.local.PrivacyPreferences
import com.example.data.local.SearchEngine
import com.example.data.local.SiteSettingsEntity
import com.example.data.local.SpeedDialEntity
import com.example.data.local.TabEntity
import com.example.data.local.WorkspaceType
import com.example.data.repository.BrowserRepository
import com.example.privacy.BrowsingDataCleaner
import com.example.privacy.ClearDataOptions
import com.example.privacy.DohLookupResult
import com.example.privacy.DohProvider
import com.example.privacy.DohResolver
import com.example.ui.theme.TabGroupColors
import com.example.vpn.VpnUiState
import com.example.vpn.WireGuardManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.util.UUID

sealed interface BrowserOverlay {
    data object None : BrowserOverlay
    data object TabSwitcher : BrowserOverlay
    data object Menu : BrowserOverlay
    data object AiSidePanel : BrowserOverlay
    data object SmartReader : BrowserOverlay
    data object AiWritingHelper : BrowserOverlay
    data object AdBlockDashboard : BrowserOverlay
    data object VpnPanel : BrowserOverlay
    data object QrScanner : BrowserOverlay
    data object Bookmarks : BrowserOverlay
    data object History : BrowserOverlay
    data object Downloads : BrowserOverlay
    data object PrivacySettings : BrowserOverlay
    data object About : BrowserOverlay
}

data class AddressSuggestion(
    val title: String,
    val subtitle: String,
    val urlOrQuery: String,
    val isHistory: Boolean = false,
    val isBookmark: Boolean = false
)

sealed interface AiPanelUiState {
    data object Idle : AiPanelUiState
    data object Loading : AiPanelUiState
    data class Streaming(val text: String) : AiPanelUiState
    data class Success(val text: String) : AiPanelUiState
    data class Error(val message: String) : AiPanelUiState
}

data class AiChatMessage(
    val isUser: Boolean,
    val text: String
)

data class SmartReaderUiState(
    val isLoading: Boolean = false,
    val title: String = "",
    val content: String = "",
    val fontSizeSp: Int = 18,
    val readerThemeIndex: Int = 0, // 0 = Dark, 1 = Sepia, 2 = Light
    val error: String? = null
)

data class AiWritingUiState(
    val isGenerating: Boolean = false,
    val existingFieldText: String = "",
    val outputText: String = "",
    val error: String? = null
)

data class FindInPageState(
    val isVisible: Boolean = false,
    val query: String = "",
    val activeMatchIndex: Int = 0,
    val totalMatches: Int = 0
)

class BrowserViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val db = BrowserDatabase.getInstance(appContext)
    private val preferences = PrivacyPreferences(appContext)
    val repository = BrowserRepository(db, preferences)

    val adBlockEngine = AdBlockEngine(appContext)
    val dohResolver = DohResolver()
    val webViewPool = WebViewPool(appContext, adBlockEngine, dohResolver)
    val wireGuardManager = WireGuardManager(appContext)
    private val geminiService = GeminiBrowserService()
    val ttsManager = SmartReaderTtsManager(appContext)
    val voiceCommandManager = VoiceCommandManager(appContext)

    val prefsState: StateFlow<BrowserPreferencesState> = repository.prefsFlow.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        BrowserPreferencesState()
    )

    val bookmarks: StateFlow<List<BookmarkEntity>> = repository.bookmarksFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val downloads: StateFlow<List<DownloadEntity>> = repository.downloadsFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val speedDials: StateFlow<List<SpeedDialEntity>> = repository.speedDialsFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val siteSettings: StateFlow<Map<String, Boolean>> = repository.siteSettingsFlow
        .map { list -> list.associate { it.host.lowercase() to it.adBlockEnabled } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val workspaceTabs: StateFlow<List<TabEntity>> = prefsState
        .map { it.activeWorkspace }
        .flatMapLatest { ws -> repository.observeTabsForWorkspace(ws) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val workspaceHistory: StateFlow<List<HistoryEntity>> = prefsState
        .map { it.activeWorkspace }
        .flatMapLatest { ws -> repository.observeHistoryForWorkspace(ws) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTabs: StateFlow<List<TabEntity>> = repository.allTabsFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val activeTab: StateFlow<TabEntity?> = combine(workspaceTabs, prefsState) { tabs, prefs ->
        tabs.firstOrNull { it.id == prefs.activeTabId } ?: tabs.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val filterListStatus: StateFlow<FilterListStatus> = adBlockEngine.filterStatus
    val tabBlockStats: StateFlow<Map<String, PageBlockStats>> = adBlockEngine.tabStats
    val vpnState: StateFlow<VpnUiState> = wireGuardManager.uiState
    val ttsState: StateFlow<TtsUiState> = ttsManager.state
    val voiceState: StateFlow<VoiceRecognitionState> = voiceCommandManager.state

    private val _isSplashVisible = MutableStateFlow(true)
    val isSplashVisible: StateFlow<Boolean> = _isSplashVisible.asStateFlow()

    private val _overlay = MutableStateFlow<BrowserOverlay>(BrowserOverlay.None)
    val overlay: StateFlow<BrowserOverlay> = _overlay.asStateFlow()

    private val _addressInput = MutableStateFlow("")
    val addressInput: StateFlow<String> = _addressInput.asStateFlow()

    private val _isAddressFocused = MutableStateFlow(false)
    val isAddressFocused: StateFlow<Boolean> = _isAddressFocused.asStateFlow()

    private val _suggestions = MutableStateFlow<List<AddressSuggestion>>(emptyList())
    val suggestions: StateFlow<List<AddressSuggestion>> = _suggestions.asStateFlow()

    private val _pageProgress = MutableStateFlow(0)
    val pageProgress: StateFlow<Int> = _pageProgress.asStateFlow()

    private val _isLoadingPage = MutableStateFlow(false)
    val isLoadingPage: StateFlow<Boolean> = _isLoadingPage.asStateFlow()

    private val _canGoBack = MutableStateFlow(false)
    val canGoBack: StateFlow<Boolean> = _canGoBack.asStateFlow()

    private val _canGoForward = MutableStateFlow(false)
    val canGoForward: StateFlow<Boolean> = _canGoForward.asStateFlow()

    private val _findState = MutableStateFlow(FindInPageState())
    val findState: StateFlow<FindInPageState> = _findState.asStateFlow()

    private val _aiPanelState = MutableStateFlow<AiPanelUiState>(AiPanelUiState.Idle)
    val aiPanelState: StateFlow<AiPanelUiState> = _aiPanelState.asStateFlow()

    private val _aiChatHistory = MutableStateFlow<List<AiChatMessage>>(emptyList())
    val aiChatHistory: StateFlow<List<AiChatMessage>> = _aiChatHistory.asStateFlow()

    private val _readerState = MutableStateFlow(SmartReaderUiState())
    val readerState: StateFlow<SmartReaderUiState> = _readerState.asStateFlow()

    private val _writingState = MutableStateFlow(AiWritingUiState())
    val writingState: StateFlow<AiWritingUiState> = _writingState.asStateFlow()

    private val _isOrganizingTabs = MutableStateFlow(false)
    val isOrganizingTabs: StateFlow<Boolean> = _isOrganizingTabs.asStateFlow()

    private val _dohTestResult = MutableStateFlow<DohLookupResult?>(null)
    val dohTestResult: StateFlow<DohLookupResult?> = _dohTestResult.asStateFlow()

    private val _isTestingDoh = MutableStateFlow(false)
    val isTestingDoh: StateFlow<Boolean> = _isTestingDoh.asStateFlow()

    private val _latestScreenshot = MutableStateFlow<CapturedScreenshot?>(null)
    val latestScreenshot: StateFlow<CapturedScreenshot?> = _latestScreenshot.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _voiceCommandResult = MutableStateFlow<String?>(null)
    val voiceCommandResult: StateFlow<String?> = _voiceCommandResult.asStateFlow()

    private var aiJob: Job? = null
    private var writingJob: Job? = null

    init {
        viewModelScope.launch {
            repository.ensureDefaultsSeeded()
            val initialPrefs = repository.prefsFlow.first()
            dohResolver.activeProvider = DohProvider.fromId(initialPrefs.dohProviderId)
            voiceCommandManager.setLanguage(initialPrefs.languageCode)
            ttsManager.setLanguage(initialPrefs.languageCode)
            adBlockEngine.initialize(
                lastUpdatedMs = initialPrefs.lastFilterUpdateMs,
                autoUpdate = initialPrefs.autoUpdateFilters
            ) { updatedMs ->
                viewModelScope.launch { preferences.setLastFilterUpdateMs(updatedMs) }
            }
            if (initialPrefs.wireGuardConfigText.isNotBlank()) {
                wireGuardManager.loadConfig(
                    initialPrefs.wireGuardConfigText,
                    initialPrefs.wireGuardTunnelName
                )
            }
            val tabs = repository.observeTabsForWorkspace(initialPrefs.activeWorkspace).first()
            if (initialPrefs.activeTabId.isBlank() || tabs.none { it.id == initialPrefs.activeTabId }) {
                tabs.firstOrNull()?.let { preferences.setActiveTabId(it.id) }
            }
            delay(1450L)
            _isSplashVisible.value = false
        }

        viewModelScope.launch {
            prefsState.collect { prefs ->
                dohResolver.activeProvider = DohProvider.fromId(prefs.dohProviderId)
                webViewPool.updateAllPrivacySettings(prefs)
            }
        }

        viewModelScope.launch {
            activeTab.collect { tab ->
                if (tab != null && !_isAddressFocused.value) {
                    _addressInput.value = if (tab.url == "about:newtab") "" else tab.url
                }
                val wv = tab?.let { webViewPool.getWebView(it.id) }
                _canGoBack.value = wv?.canGoBack() ?: false
                _canGoForward.value = wv?.canGoForward() ?: false
            }
        }
    }

    fun dismissSplash() {
        _isSplashVisible.value = false
    }

    fun openOverlay(overlay: BrowserOverlay) {
        _overlay.value = overlay
    }

    fun closeOverlay() {
        _overlay.value = BrowserOverlay.None
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun showStatus(message: String) {
        _statusMessage.value = message
    }

    fun onAddressFocusChanged(focused: Boolean) {
        _isAddressFocused.value = focused
        if (focused) {
            val currentUrl = activeTab.value?.url.orEmpty()
            _addressInput.value = if (currentUrl == "about:newtab") "" else currentUrl
            refreshSuggestions(_addressInput.value)
        } else {
            _suggestions.value = emptyList()
        }
    }

    fun onAddressInputChanged(input: String) {
        _addressInput.value = input
        refreshSuggestions(input)
    }

    private fun refreshSuggestions(query: String) {
        viewModelScope.launch {
            val trimmed = query.trim()
            val list = mutableListOf<AddressSuggestion>()
            if (trimmed.isNotEmpty()) {
                val engine = SearchEngine.fromId(prefsState.value.searchEngineId)
                if (looksLikeUrl(trimmed)) {
                    val normalized = normalizeUrl(trimmed)
                    list.add(AddressSuggestion("Go to $normalized", "Open website", normalized))
                }
                list.add(
                    AddressSuggestion(
                        "Search \"$trimmed\"",
                        "via ${engine.displayName}",
                        trimmed
                    )
                )
            }
            val matchingBookmarks = bookmarks.value.filter {
                trimmed.isEmpty() ||
                    it.title.contains(trimmed, ignoreCase = true) ||
                    it.url.contains(trimmed, ignoreCase = true)
            }.take(4)
            matchingBookmarks.forEach { bm ->
                list.add(AddressSuggestion(bm.title, bm.url, bm.url, isBookmark = true))
            }
            val histMatches = if (trimmed.isNotEmpty()) {
                repository.searchHistory(prefsState.value.activeWorkspace, trimmed)
            } else {
                workspaceHistory.value.take(4)
            }
            histMatches.forEach { h ->
                if (list.none { it.urlOrQuery == h.url }) {
                    list.add(AddressSuggestion(h.title, h.url, h.url, isHistory = true))
                }
            }
            _suggestions.value = list.take(8)
        }
    }

    fun submitAddressOrSearch(rawInput: String) {
        val target = resolveInputToUrl(rawInput)
        val tab = activeTab.value ?: return
        _isAddressFocused.value = false
        _suggestions.value = emptyList()
        _addressInput.value = if (target == "about:newtab") "" else target
        viewModelScope.launch {
            repository.upsertTab(tab.copy(url = target, lastAccessedAt = System.currentTimeMillis()))
        }
        if (target != "about:newtab") {
            webViewPool.getWebView(tab.id)?.loadUrl(target)
        }
    }

    fun resolveInputToUrl(rawInput: String): String {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty() || trimmed == "about:newtab") return "about:newtab"
        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true) ||
            trimmed.startsWith("about:", ignoreCase = true)
        ) {
            return if (prefsState.value.httpsOnlyMode && trimmed.startsWith("http://", ignoreCase = true)) {
                "https://" + trimmed.substring(7)
            } else {
                trimmed
            }
        }
        if (looksLikeUrl(trimmed)) {
            val scheme = "https://"
            return "$scheme$trimmed"
        }
        val engine = SearchEngine.fromId(prefsState.value.searchEngineId)
        val encoded = URLEncoder.encode(trimmed, "UTF-8")
        return engine.searchTemplate.replace("%s", encoded)
    }

    private fun looksLikeUrl(text: String): Boolean {
        if (text.contains(' ')) return false
        if (Patterns.WEB_URL.matcher(text).matches()) return true
        return text.contains('.') && !text.endsWith('.')
    }

    private fun normalizeUrl(text: String): String {
        return if (text.startsWith("http://") || text.startsWith("https://")) text
        else "https://$text"
    }

    fun loadUrlInActiveTab(url: String) {
        submitAddressOrSearch(url)
    }

    fun loadUrlInNewTab(url: String, incognito: Boolean = false) {
        viewModelScope.launch {
            val ws = prefsState.value.activeWorkspace
            val currentTabs = repository.observeTabsForWorkspace(ws).first()
            val resolved = resolveInputToUrl(url)
            val newTab = TabEntity(
                id = UUID.randomUUID().toString(),
                workspace = ws.name,
                url = resolved,
                title = if (resolved == "about:newtab") (if (incognito) "Incognito Tab" else "New Tab") else resolved,
                isIncognito = incognito,
                position = (currentTabs.maxOfOrNull { it.position } ?: -1) + 1
            )
            repository.upsertTab(newTab)
            preferences.setActiveTabId(newTab.id)
        }
    }

    fun addNewTab(incognito: Boolean = false) {
        loadUrlInNewTab("about:newtab", incognito)
        closeOverlay()
    }

    fun selectTab(tabId: String) {
        viewModelScope.launch {
            preferences.setActiveTabId(tabId)
        }
        closeOverlay()
    }

    fun closeTab(tabId: String) {
        viewModelScope.launch {
            val ws = prefsState.value.activeWorkspace
            val tabs = repository.observeTabsForWorkspace(ws).first()
            val closingIndex = tabs.indexOfFirst { it.id == tabId }
            webViewPool.destroyTab(tabId)
            adBlockEngine.removeTabStats(tabId)
            repository.deleteTab(tabId)

            val remaining = tabs.filterNot { it.id == tabId }
            if (remaining.isEmpty()) {
                val fresh = TabEntity(
                    id = UUID.randomUUID().toString(),
                    workspace = ws.name,
                    url = "about:newtab",
                    title = "New Tab",
                    position = 0
                )
                repository.upsertTab(fresh)
                preferences.setActiveTabId(fresh.id)
            } else if (prefsState.value.activeTabId == tabId) {
                val next = remaining.getOrElse((closingIndex - 1).coerceAtLeast(0)) { remaining.first() }
                preferences.setActiveTabId(next.id)
            }
        }
    }

    fun togglePinTab(tab: TabEntity) {
        viewModelScope.launch {
            repository.upsertTab(tab.copy(isPinned = !tab.isPinned))
        }
    }

    fun moveTab(tabId: String, delta: Int) {
        viewModelScope.launch {
            val list = workspaceTabs.value.toMutableList()
            val idx = list.indexOfFirst { it.id == tabId }
            val targetIdx = idx + delta
            if (idx >= 0 && targetIdx in list.indices) {
                val item = list.removeAt(idx)
                list.add(targetIdx, item)
                val reindexed = list.mapIndexed { index, tabEntity ->
                    tabEntity.copy(position = index)
                }
                repository.upsertTabs(reindexed)
            }
        }
    }

    fun assignTabGroup(tab: TabEntity, groupName: String?, colorHex: Long?) {
        viewModelScope.launch {
            repository.upsertTab(
                tab.copy(
                    groupName = groupName?.takeIf { it.isNotBlank() },
                    groupColorHex = if (groupName.isNullOrBlank()) null else colorHex
                )
            )
        }
    }

    fun organizeTabsWithAi() {
        val tabs = workspaceTabs.value
        if (tabs.size < 2) {
            showStatus("Open at least 2 tabs in this workspace to organize with AI.")
            return
        }
        viewModelScope.launch {
            _isOrganizingTabs.value = true
            try {
                val assignments = geminiService.organizeTabs(tabs)
                val tabMap = tabs.associateBy { it.id }.toMutableMap()
                val updated = mutableListOf<TabEntity>()
                assignments.forEach { group ->
                    val color = TabGroupColors[group.colorIndex.coerceIn(0, TabGroupColors.lastIndex)]
                    group.tabIds.forEach { id ->
                        tabMap[id]?.let { t ->
                            updated.add(t.copy(groupName = group.groupName, groupColorHex = color))
                            tabMap.remove(id)
                        }
                    }
                }
                if (updated.isNotEmpty()) {
                    repository.upsertTabs(updated)
                    showStatus("Organized ${updated.size} tabs into ${assignments.size} AI groups.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showStatus("AI Tab Organizer failed: ${e.localizedMessage ?: "error"}")
            } finally {
                _isOrganizingTabs.value = false
            }
        }
    }

    fun switchWorkspace(workspace: WorkspaceType) {
        viewModelScope.launch {
            preferences.setActiveWorkspace(workspace)
            val tabs = repository.observeTabsForWorkspace(workspace).first()
            if (tabs.isEmpty()) {
                val fresh = TabEntity(
                    id = UUID.randomUUID().toString(),
                    workspace = workspace.name,
                    url = "about:newtab",
                    title = "New Tab",
                    position = 0
                )
                repository.upsertTab(fresh)
                preferences.setActiveTabId(fresh.id)
            } else {
                preferences.setActiveTabId(tabs.first().id)
            }
        }
    }

    fun goBack() {
        val tab = activeTab.value ?: return
        val wv = webViewPool.getWebView(tab.id)
        if (wv != null && wv.canGoBack()) {
            wv.goBack()
            _canGoBack.value = wv.canGoBack()
            _canGoForward.value = wv.canGoForward()
        } else if (tab.url != "about:newtab") {
            goHome()
        }
    }

    fun goForward() {
        val tab = activeTab.value ?: return
        val wv = webViewPool.getWebView(tab.id)
        if (wv != null && wv.canGoForward()) {
            wv.goForward()
            _canGoBack.value = wv.canGoBack()
            _canGoForward.value = wv.canGoForward()
        }
    }

    fun refreshOrStop() {
        val tab = activeTab.value ?: return
        val wv = webViewPool.getWebView(tab.id) ?: return
        if (_isLoadingPage.value) {
            wv.stopLoading()
            _isLoadingPage.value = false
        } else {
            wv.reload()
        }
    }

    fun goHome() {
        val tab = activeTab.value ?: return
        viewModelScope.launch {
            repository.upsertTab(tab.copy(url = "about:newtab", title = if (tab.isIncognito) "Incognito Tab" else "New Tab"))
        }
    }

    fun toggleCurrentBookmark() {
        val tab = activeTab.value ?: return
        if (tab.url == "about:newtab") {
            showStatus("Open a webpage to bookmark it.")
            return
        }
        viewModelScope.launch {
            val added = repository.toggleBookmark(tab.title, tab.url, prefsState.value.activeWorkspace)
            showStatus(if (added) "Bookmarked ${tab.title}" else "Removed bookmark")
        }
    }

    fun deleteBookmark(id: Long) {
        viewModelScope.launch { repository.deleteBookmark(id) }
    }

    fun deleteHistoryItem(id: Long) {
        viewModelScope.launch { repository.deleteHistoryItem(id) }
    }

    fun clearWorkspaceHistory() {
        viewModelScope.launch {
            repository.clearHistory(prefsState.value.activeWorkspace)
            showStatus("Cleared ${prefsState.value.activeWorkspace.label} history")
        }
    }

    fun deleteDownload(id: Long) {
        viewModelScope.launch { repository.deleteDownload(id) }
    }

    fun addSpeedDial(title: String, url: String) {
        if (title.isBlank() || url.isBlank()) return
        viewModelScope.launch {
            repository.addSpeedDial(title.trim(), normalizeUrl(url.trim()))
        }
    }

    fun deleteSpeedDial(id: Long) {
        viewModelScope.launch { repository.deleteSpeedDial(id) }
    }

    // --- WebView callbacks ---
    fun onWebProgressChanged(tabId: String, progress: Int) {
        if (activeTab.value?.id == tabId) {
            _pageProgress.value = progress
            _isLoadingPage.value = progress in 1..99
        }
    }

    fun onWebTitleChanged(tabId: String, title: String) {
        viewModelScope.launch {
            val tab = allTabs.value.firstOrNull { it.id == tabId } ?: return@launch
            if (tab.url != "about:newtab") {
                repository.upsertTab(tab.copy(title = title))
            }
        }
    }

    fun onWebPageStarted(tabId: String, url: String) {
        if (activeTab.value?.id == tabId) {
            _isLoadingPage.value = true
            if (!_isAddressFocused.value) {
                _addressInput.value = url
            }
        }
        viewModelScope.launch {
            val tab = allTabs.value.firstOrNull { it.id == tabId } ?: return@launch
            if (url.isNotBlank()) {
                repository.upsertTab(tab.copy(url = url))
            }
        }
    }

    fun onWebPageFinished(
        tabId: String,
        url: String,
        title: String,
        canGoBack: Boolean,
        canGoForward: Boolean
    ) {
        if (activeTab.value?.id == tabId) {
            _isLoadingPage.value = false
            _pageProgress.value = 100
            _canGoBack.value = canGoBack
            _canGoForward.value = canGoForward
            if (!_isAddressFocused.value && url != "about:newtab") {
                _addressInput.value = url
            }
        }
        viewModelScope.launch {
            val tab = allTabs.value.firstOrNull { it.id == tabId } ?: return@launch
            if (url.isNotBlank() && url != "about:newtab") {
                repository.upsertTab(tab.copy(url = url, title = title))
            }
        }
    }

    fun onWebRecordHistory(tabId: String, title: String, url: String) {
        viewModelScope.launch {
            val tab = allTabs.value.firstOrNull { it.id == tabId }
            if (tab?.isIncognito == true) return@launch
            val ws = tab?.let {
                WorkspaceType.entries.firstOrNull { w -> w.name == it.workspace }
            } ?: prefsState.value.activeWorkspace
            repository.recordHistory(title, url, ws)
        }
    }

    fun onBlockedRequest(tabId: String) {
        viewModelScope.launch {
            preferences.incrementLifetimeBlocked(1L)
        }
    }

    fun onDownloadRequest(url: String, ua: String?, cd: String?, mime: String?) {
        val msg = BrowserDownloadHelper.enqueueDownload(
            appContext,
            repository,
            viewModelScope,
            url,
            ua,
            cd,
            mime
        )
        showStatus(msg)
    }

    // --- Find in page ---
    fun openFindInPage() {
        closeOverlay()
        _findState.value = FindInPageState(isVisible = true)
    }

    fun updateFindQuery(query: String) {
        _findState.update { it.copy(query = query) }
        val tab = activeTab.value ?: return
        val wv = webViewPool.getWebView(tab.id) ?: return
        if (query.isEmpty()) {
            wv.clearMatches()
            _findState.update { it.copy(activeMatchIndex = 0, totalMatches = 0) }
        } else {
            wv.findAllAsync(query)
        }
    }

    fun findNext(forward: Boolean) {
        val tab = activeTab.value ?: return
        webViewPool.getWebView(tab.id)?.findNext(forward)
    }

    fun onFindResultReceived(activeOrdinal: Int, total: Int) {
        _findState.update {
            it.copy(
                activeMatchIndex = if (total > 0) activeOrdinal + 1 else 0,
                totalMatches = total
            )
        }
    }

    fun closeFindInPage() {
        val tab = activeTab.value
        if (tab != null) {
            webViewPool.getWebView(tab.id)?.clearMatches()
        }
        _findState.value = FindInPageState(isVisible = false)
    }

    // --- Full-page screenshot ---
    fun captureFullPageScreenshot() {
        val tab = activeTab.value ?: return
        if (tab.url == "about:newtab") {
            showStatus("Open a webpage to capture a full-page screenshot.")
            return
        }
        closeOverlay()
        viewModelScope.launch {
            val captured = webViewPool.captureFullPageScreenshot(tab.id, tab.title)
            if (captured != null) {
                _latestScreenshot.value = captured
                showStatus("Full-page screenshot saved (${captured.width}x${captured.height}px)")
            } else {
                showStatus("Unable to capture screenshot on this page.")
            }
        }
    }

    fun dismissScreenshotPreview() {
        _latestScreenshot.value = null
    }

    fun shareCurrentPage(): Intent? {
        val tab = activeTab.value ?: return null
        if (tab.url == "about:newtab") return null
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, tab.title)
            putExtra(Intent.EXTRA_TEXT, "${tab.title}\n${tab.url}")
        }
    }

    // --- Ad Blocker ---
    fun isSiteAdBlockEnabled(host: String): Boolean {
        if (host.isBlank()) return true
        return siteSettings.value[host.lowercase()] ?: true
    }

    fun toggleGlobalAdBlock(enabled: Boolean) {
        viewModelScope.launch { preferences.setAdBlockEnabled(enabled) }
    }

    fun toggleSiteAdBlock(host: String, enabled: Boolean) {
        viewModelScope.launch {
            repository.setSiteAdBlockEnabled(host, enabled)
            activeTab.value?.let { webViewPool.getWebView(it.id)?.reload() }
        }
    }

    fun refreshFilterLists() {
        viewModelScope.launch {
            val ok = adBlockEngine.updateFilterLists { updatedMs ->
                viewModelScope.launch { preferences.setLastFilterUpdateMs(updatedMs) }
            }
            showStatus(if (ok) "EasyList & EasyPrivacy updated." else "Filter update failed; using cached lists.")
        }
    }

    // --- AI Side Panel ---
    fun runAiQuickAction(actionType: String, targetLang: String = "Urdu") {
        val prompt = when (actionType) {
            "summarize" -> "Provide a clear, structured bullet-point summary of this webpage."
            "translate" -> "Translate the main content and key takeaways of this webpage into $targetLang."
            "explain" -> "Explain the topic of this webpage simply so anyone can understand it, using short paragraphs."
            else -> actionType
        }
        askAiAboutPage(prompt, displayLabel = when (actionType) {
            "summarize" -> "Summarize page"
            "translate" -> "Translate to $targetLang"
            "explain" -> "Explain simply"
            else -> prompt
        })
    }

    fun askAiAboutPage(question: String, displayLabel: String = question) {
        val tab = activeTab.value ?: return
        aiJob?.cancel()
        aiJob = viewModelScope.launch {
            _aiChatHistory.update { it + AiChatMessage(isUser = true, text = displayLabel) }
            _aiPanelState.value = AiPanelUiState.Loading
            try {
                val pageText = if (tab.url == "about:newtab") "" else webViewPool.extractPageText(tab.id)
                var finalAnswer = ""
                geminiService.streamPageAction(
                    prompt = question,
                    pageTitle = tab.title,
                    pageUrl = tab.url,
                    pageText = pageText
                ).collect { partial ->
                    finalAnswer = partial
                    _aiPanelState.value = AiPanelUiState.Streaming(partial)
                }
                _aiPanelState.value = AiPanelUiState.Success(finalAnswer)
                _aiChatHistory.update { it + AiChatMessage(isUser = false, text = finalAnswer) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _aiPanelState.value = AiPanelUiState.Error(
                    e.localizedMessage ?: "Gemini AI request failed."
                )
            }
        }
    }

    fun clearAiChat() {
        aiJob?.cancel()
        _aiChatHistory.value = emptyList()
        _aiPanelState.value = AiPanelUiState.Idle
    }

    // --- Smart Reader + TTS ---
    fun openSmartReader() {
        val tab = activeTab.value ?: return
        if (tab.url == "about:newtab") {
            showStatus("Open an article or webpage to use Smart Reader.")
            return
        }
        openOverlay(BrowserOverlay.SmartReader)
        viewModelScope.launch {
            _readerState.update { it.copy(isLoading = true, title = tab.title, error = null) }
            val raw = webViewPool.extractPageText(tab.id)
            if (raw.isBlank()) {
                _readerState.update {
                    it.copy(isLoading = false, content = "", error = "No readable text found on this page.")
                }
                return@launch
            }
            val cleaned = geminiService.formatSmartReaderArticle(tab.title, raw)
            _readerState.update {
                it.copy(isLoading = false, title = tab.title, content = cleaned, error = null)
            }
        }
    }

    fun setReaderFontSize(sp: Int) {
        _readerState.update { it.copy(fontSizeSp = sp.coerceIn(14, 28)) }
    }

    fun setReaderTheme(index: Int) {
        _readerState.update { it.copy(readerThemeIndex = index) }
    }

    fun toggleReaderTts() {
        if (ttsState.value.isSpeaking) {
            ttsManager.stop()
        } else {
            val content = _readerState.value.content
            if (content.isNotBlank()) {
                ttsManager.speak(content, prefsState.value.languageCode)
            }
        }
    }

    // --- AI Writing Helper ---
    fun openWritingHelper() {
        val tab = activeTab.value
        openOverlay(BrowserOverlay.AiWritingHelper)
        viewModelScope.launch {
            val existing = if (tab != null && tab.url != "about:newtab") {
                webViewPool.extractFocusedFieldText(tab.id)
            } else ""
            _writingState.update { it.copy(existingFieldText = existing, error = null) }
        }
    }

    fun generateWritingHelper(instruction: String, draft: String, tone: String, language: String) {
        writingJob?.cancel()
        writingJob = viewModelScope.launch {
            _writingState.update { it.copy(isGenerating = true, outputText = "", error = null) }
            try {
                geminiService.streamWritingHelper(instruction, draft, tone, language).collect { partial ->
                    _writingState.update { it.copy(outputText = partial) }
                }
                _writingState.update { it.copy(isGenerating = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _writingState.update {
                    it.copy(isGenerating = false, error = e.localizedMessage ?: "Generation failed.")
                }
            }
        }
    }

    fun insertWritingOutputIntoPage() {
        val tab = activeTab.value ?: return
        val text = _writingState.value.outputText
        if (text.isNotBlank()) {
            webViewPool.insertTextIntoActiveField(tab.id, text)
            closeOverlay()
            showStatus("Inserted text into active field.")
        }
    }

    // --- Voice Commands (Urdu / Hindi / English) ---
    fun startVoiceCommand(languageCode: String, onNeedsVpnPermission: () -> Unit) {
        voiceCommandManager.setLanguage(languageCode)
        voiceCommandManager.startListening { transcript ->
            executeVoiceTranscript(transcript, onNeedsVpnPermission)
        }
    }

    private fun executeVoiceTranscript(transcript: String, onNeedsVpnPermission: () -> Unit) {
        viewModelScope.launch {
            _voiceCommandResult.value = "Processing \"$transcript\"..."
            try {
                val intent = geminiService.parseVoiceCommand(transcript)
                when (intent.action) {
                    "OPEN_URL", "SEARCH" -> {
                        if (intent.queryOrUrl.isNotBlank()) submitAddressOrSearch(intent.queryOrUrl)
                    }
                    "NEW_TAB" -> addNewTab(incognito = false)
                    "INCOGNITO_TAB" -> addNewTab(incognito = true)
                    "CLOSE_TAB" -> activeTab.value?.let { closeTab(it.id) }
                    "SUMMARIZE_PAGE" -> {
                        openOverlay(BrowserOverlay.AiSidePanel)
                        runAiQuickAction("summarize")
                    }
                    "TRANSLATE_URDU" -> {
                        openOverlay(BrowserOverlay.AiSidePanel)
                        runAiQuickAction("translate", "Urdu")
                    }
                    "TRANSLATE_HINDI" -> {
                        openOverlay(BrowserOverlay.AiSidePanel)
                        runAiQuickAction("translate", "Hindi")
                    }
                    "TRANSLATE_ENGLISH" -> {
                        openOverlay(BrowserOverlay.AiSidePanel)
                        runAiQuickAction("translate", "English")
                    }
                    "READER_MODE" -> openSmartReader()
                    "TOGGLE_VPN" -> toggleVpnConnection(onNeedsVpnPermission)
                    "TOGGLE_ADBLOCK" -> toggleGlobalAdBlock(!prefsState.value.adBlockEnabled)
                    "BOOKMARK_PAGE" -> toggleCurrentBookmark()
                    "DARK_THEME" -> setDarkTheme(true)
                    "LIGHT_THEME" -> setDarkTheme(false)
                    "WORKSPACE_RESEARCH" -> switchWorkspace(WorkspaceType.RESEARCH)
                    "WORKSPACE_EDITING" -> switchWorkspace(WorkspaceType.EDITING)
                    "WORKSPACE_ENTERTAINMENT" -> switchWorkspace(WorkspaceType.ENTERTAINMENT)
                    "GO_BACK" -> goBack()
                    "GO_FORWARD" -> goForward()
                    "REFRESH" -> refreshOrStop()
                    "HOME" -> goHome()
                }
                _voiceCommandResult.value = intent.languageResponse
                showStatus("${intent.languageResponse} ($transcript)")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Fallback direct search if Gemini fails
                submitAddressOrSearch(transcript)
                showStatus("Searched: $transcript")
            }
        }
    }

    // --- VPN ---
    fun importWireGuardConfig(configText: String, tunnelName: String = "RehanVPN") {
        val result = wireGuardManager.loadConfig(configText, tunnelName)
        if (result.isSuccess) {
            viewModelScope.launch {
                preferences.saveWireGuardConfig(configText.trim(), tunnelName)
                showStatus("WireGuard config imported ($tunnelName)")
            }
        } else {
            showStatus(result.exceptionOrNull()?.localizedMessage ?: "Invalid WireGuard config")
        }
    }

    fun toggleVpnConnection(onNeedsPermission: () -> Unit) {
        viewModelScope.launch {
            val current = vpnState.value.connectionState
            if (current == com.example.vpn.VpnConnectionState.CONNECTED ||
                current == com.example.vpn.VpnConnectionState.CONNECTING
            ) {
                wireGuardManager.disconnect()
            } else {
                val permIntent = wireGuardManager.prepareVpnPermission()
                if (permIntent != null) {
                    onNeedsPermission()
                } else {
                    wireGuardManager.connect()
                }
            }
        }
    }

    fun onVpnPermissionGranted() {
        viewModelScope.launch {
            wireGuardManager.connect()
        }
    }

    // --- Privacy & Preferences ---
    fun setDarkTheme(enabled: Boolean) {
        viewModelScope.launch { preferences.setDarkTheme(enabled) }
    }

    fun setLanguageCode(code: String) {
        viewModelScope.launch {
            preferences.setLanguageCode(code)
            voiceCommandManager.setLanguage(code)
            ttsManager.setLanguage(code)
        }
    }

    fun setBlockThirdPartyCookies(enabled: Boolean) {
        viewModelScope.launch { preferences.setBlockThirdPartyCookies(enabled) }
    }

    fun setBlockAllCookies(enabled: Boolean) {
        viewModelScope.launch { preferences.setBlockAllCookies(enabled) }
    }

    fun setSendDntHeader(enabled: Boolean) {
        viewModelScope.launch { preferences.setSendDntHeader(enabled) }
    }

    fun setHttpsOnlyMode(enabled: Boolean) {
        viewModelScope.launch { preferences.setHttpsOnlyMode(enabled) }
    }

    fun setJavaScriptEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setJavaScriptEnabled(enabled) }
    }

    fun setDohProvider(provider: DohProvider) {
        viewModelScope.launch {
            preferences.setDohProviderId(provider.id)
            dohResolver.activeProvider = provider
            _dohTestResult.value = null
        }
    }

    fun setSearchEngine(engine: SearchEngine) {
        viewModelScope.launch { preferences.setSearchEngineId(engine.id) }
    }

    fun testDohLookup(host: String = "example.com") {
        val provider = DohProvider.fromId(prefsState.value.dohProviderId)
        viewModelScope.launch {
            _isTestingDoh.value = true
            _dohTestResult.value = dohResolver.testLookup(host.ifBlank { "example.com" }, provider)
            _isTestingDoh.value = false
        }
    }

    fun clearBrowsingData(options: ClearDataOptions) {
        viewModelScope.launch {
            BrowsingDataCleaner.clear(appContext, repository, webViewPool.allWebViews(), options)
            showStatus("Selected browsing data cleared.")
        }
    }

    fun getCurrentHost(): String {
        val url = activeTab.value?.url.orEmpty()
        return try {
            Uri.parse(url).host?.lowercase().orEmpty()
        } catch (_: Exception) {
            ""
        }
    }

    override fun onCleared() {
        super.onCleared()
        ttsManager.shutdown()
        voiceCommandManager.stopListening()
        webViewPool.destroyAll()
    }
}
