package com.example

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.adblock.PageBlockStats
import com.example.ai.configureAppCheck
import com.example.browser.WebViewPool
import com.example.ui.components.BottomBrowserBar
import com.example.ui.components.BrowserMenuSheet
import com.example.ui.components.FindInPageBar
import com.example.ui.components.NewTabPage
import com.example.ui.components.SplashScreen
import com.example.ui.components.TabSwitcherSheet
import com.example.ui.components.TopTabStrip
import com.example.ui.localization.AppLanguage
import com.example.ui.localization.ProvideBrowserStrings
import com.example.ui.screens.AboutScreen
import com.example.ui.screens.AdBlockDashboardSheet
import com.example.ui.screens.AiSidePanelSheet
import com.example.ui.screens.AiWritingHelperDialog
import com.example.ui.screens.BookmarksScreen
import com.example.ui.screens.DownloadsScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.PrivacySettingsSheet
import com.example.ui.screens.SmartReaderOverlay
import com.example.ui.screens.VpnSheet
import com.example.ui.theme.RehanBrowserTheme
import com.example.ui.viewmodel.BrowserOverlay
import com.example.ui.viewmodel.BrowserViewModel
import com.example.vpn.QrScannerScreen

class MainActivity : ComponentActivity() {

    private val viewModel: BrowserViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Step 1: Configure Firebase App Check with launch intent before UI or ViewModel usage
        configureAppCheck(this, intent)

        WebViewPool.ensureSlowWholeDocumentDraw()
        enableEdgeToEdge()

        val initialUrl = intent?.dataString
        if (!initialUrl.isNullOrBlank() && initialUrl.startsWith("http")) {
            viewModel.loadUrlInNewTab(initialUrl)
        }

        setContent {
            RehanBrowserRoot(viewModel = viewModel)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val url = intent.dataString
        if (!url.isNullOrBlank() && url.startsWith("http")) {
            viewModel.loadUrlInNewTab(url)
        }
    }
}

@Composable
private fun RehanBrowserRoot(viewModel: BrowserViewModel) {
    val context = LocalContext.current
    val prefs by viewModel.prefsState.collectAsStateWithLifecycle()
    val tabs by viewModel.workspaceTabs.collectAsStateWithLifecycle()
    val activeTab by viewModel.activeTab.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val history by viewModel.workspaceHistory.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val speedDials by viewModel.speedDials.collectAsStateWithLifecycle()
    val siteSettings by viewModel.siteSettings.collectAsStateWithLifecycle()
    val tabStats by viewModel.tabBlockStats.collectAsStateWithLifecycle()
    val filterStatus by viewModel.filterListStatus.collectAsStateWithLifecycle()
    val vpnState by viewModel.vpnState.collectAsStateWithLifecycle()
    val ttsState by viewModel.ttsState.collectAsStateWithLifecycle()
    val voiceState by viewModel.voiceState.collectAsStateWithLifecycle()

    val isSplashVisible by viewModel.isSplashVisible.collectAsStateWithLifecycle()
    val overlay by viewModel.overlay.collectAsStateWithLifecycle()
    val addressInput by viewModel.addressInput.collectAsStateWithLifecycle()
    val isAddressFocused by viewModel.isAddressFocused.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val pageProgress by viewModel.pageProgress.collectAsStateWithLifecycle()
    val isLoadingPage by viewModel.isLoadingPage.collectAsStateWithLifecycle()
    val canGoBack by viewModel.canGoBack.collectAsStateWithLifecycle()
    val canGoForward by viewModel.canGoForward.collectAsStateWithLifecycle()
    val findState by viewModel.findState.collectAsStateWithLifecycle()
    val aiPanelState by viewModel.aiPanelState.collectAsStateWithLifecycle()
    val aiChatHistory by viewModel.aiChatHistory.collectAsStateWithLifecycle()
    val readerState by viewModel.readerState.collectAsStateWithLifecycle()
    val writingState by viewModel.writingState.collectAsStateWithLifecycle()
    val isOrganizingTabs by viewModel.isOrganizingTabs.collectAsStateWithLifecycle()
    val dohTestResult by viewModel.dohTestResult.collectAsStateWithLifecycle()
    val isTestingDoh by viewModel.isTestingDoh.collectAsStateWithLifecycle()
    val latestScreenshot by viewModel.latestScreenshot.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val addressFocusRequester = remember { FocusRequester() }
    val appLanguage = AppLanguage.fromCode(prefs.languageCode)

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onVpnPermissionGranted()
        } else {
            viewModel.showStatus("VPN permission denied.")
        }
    }

    val requestVpnFlow: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val permIntent = viewModel.wireGuardManager.prepareVpnPermission()
        if (permIntent != null) {
            vpnPermissionLauncher.launch(permIntent)
        } else {
            viewModel.onVpnPermissionGranted()
        }
    }

    val confPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (!text.isNullOrBlank()) {
                    val fileName = uri.lastPathSegment?.substringAfterLast('/')
                        ?.removeSuffix(".conf")
                        ?.ifBlank { "RehanVPN" } ?: "RehanVPN"
                    viewModel.importWireGuardConfig(text, fileName)
                } else {
                    viewModel.showStatus("Selected .conf file is empty.")
                }
            } catch (e: Exception) {
                viewModel.showStatus("Failed to read file: ${e.localizedMessage}")
            }
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startVoiceCommand(prefs.languageCode, requestVpnFlow)
        } else {
            viewModel.showStatus("Microphone permission is needed for voice commands.")
        }
    }

    val triggerVoiceCommand: () -> Unit = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.startVoiceCommand(prefs.languageCode, requestVpnFlow)
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(statusMessage) {
        statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    // Back press navigates WebView history when on a webpage and no overlay is open
    BackHandler(enabled = overlay == BrowserOverlay.None && canGoBack) {
        viewModel.goBack()
    }

    RehanBrowserTheme(
        darkTheme = prefs.isDarkTheme,
        isRtl = appLanguage.isRtl
    ) {
        ProvideBrowserStrings(language = appLanguage) {
            Box(modifier = Modifier.fillMaxSize()) {
                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    topBar = {
                        TopTabStrip(
                            activeWorkspace = prefs.activeWorkspace,
                            tabs = tabs,
                            activeTabId = activeTab?.id.orEmpty(),
                            onSelectWorkspace = viewModel::switchWorkspace,
                            onSelectTab = viewModel::selectTab,
                            onCloseTab = viewModel::closeTab,
                            onNewTab = { viewModel.addNewTab(false) },
                            modifier = Modifier.statusBarsPadding()
                        )
                    },
                    bottomBar = {
                        Column(modifier = Modifier.imePadding()) {
                            AnimatedVisibility(visible = findState.isVisible) {
                                FindInPageBar(
                                    state = findState,
                                    onQueryChange = viewModel::updateFindQuery,
                                    onPrevious = { viewModel.findNext(false) },
                                    onNext = { viewModel.findNext(true) },
                                    onClose = viewModel::closeFindInPage
                                )
                            }
                            BottomBrowserBar(
                                addressInput = addressInput,
                                isAddressFocused = isAddressFocused,
                                suggestions = suggestions,
                                isIncognito = activeTab?.isIncognito == true,
                                isLoading = isLoadingPage,
                                pageProgress = pageProgress,
                                canGoBack = canGoBack,
                                canGoForward = canGoForward,
                                tabCount = tabs.size,
                                blockedOnPage = tabStats[activeTab?.id]?.totalBlocked ?: 0,
                                vpnState = vpnState.connectionState,
                                focusRequester = addressFocusRequester,
                                onAddressChange = viewModel::onAddressInputChanged,
                                onAddressFocusChange = viewModel::onAddressFocusChanged,
                                onSubmitAddress = viewModel::submitAddressOrSearch,
                                onBack = viewModel::goBack,
                                onForward = viewModel::goForward,
                                onRefreshOrStop = viewModel::refreshOrStop,
                                onHome = viewModel::goHome,
                                onOpenTabs = { viewModel.openOverlay(BrowserOverlay.TabSwitcher) },
                                onOpenShield = { viewModel.openOverlay(BrowserOverlay.AdBlockDashboard) },
                                onOpenVpn = { viewModel.openOverlay(BrowserOverlay.VpnPanel) },
                                onOpenAi = { viewModel.openOverlay(BrowserOverlay.AiSidePanel) },
                                onVoiceTap = triggerVoiceCommand,
                                onOpenMenu = { viewModel.openOverlay(BrowserOverlay.Menu) }
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        val tab = activeTab
                        if (tab == null || tab.url == "about:newtab") {
                            NewTabPage(
                                workspace = prefs.activeWorkspace,
                                isIncognito = tab?.isIncognito == true,
                                speedDials = speedDials,
                                totalLifetimeBlocked = prefs.totalLifetimeBlocked,
                                vpnState = vpnState.connectionState,
                                onSearchTap = { addressFocusRequester.requestFocus() },
                                onVoiceTap = triggerVoiceCommand,
                                onSpeedDialClick = viewModel::loadUrlInActiveTab,
                                onAddSpeedDial = viewModel::addSpeedDial,
                                onDeleteSpeedDial = viewModel::deleteSpeedDial,
                                onOpenVpn = { viewModel.openOverlay(BrowserOverlay.VpnPanel) },
                                onOpenAdBlock = { viewModel.openOverlay(BrowserOverlay.AdBlockDashboard) }
                            )
                        } else {
                            androidx.compose.runtime.key(tab.id) {
                                AndroidView(
                                    factory = {
                                        val wv = viewModel.webViewPool.getOrCreateWebView(
                                            tabId = tab.id,
                                            isIncognito = tab.isIncognito,
                                            prefs = prefs,
                                            isSiteAdBlockEnabled = { host -> viewModel.isSiteAdBlockEnabled(host) },
                                            onBlockedRequest = viewModel::onBlockedRequest,
                                            onProgressChanged = viewModel::onWebProgressChanged,
                                            onTitleChanged = viewModel::onWebTitleChanged,
                                            onPageStarted = viewModel::onWebPageStarted,
                                            onPageFinished = viewModel::onWebPageFinished,
                                            onRecordHistory = viewModel::onWebRecordHistory,
                                            onDownloadRequest = viewModel::onDownloadRequest,
                                            onFindResult = viewModel::onFindResultReceived
                                        )
                                        (wv.parent as? ViewGroup)?.removeView(wv)
                                        if (wv.url.isNullOrBlank()) {
                                            wv.loadUrl(tab.url)
                                        }
                                        wv
                                    },
                                    update = { wv: WebView ->
                                        if (tab.url.startsWith("http") &&
                                            wv.url.isNullOrBlank()
                                        ) {
                                            wv.loadUrl(tab.url)
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }

                // Voice Listening Banner
                if (voiceState.isListening) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        tonalElevation = 8.dp,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text(
                                    text = "Listening (${voiceState.languageTag})...",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                if (voiceState.partialTranscript.isNotBlank()) {
                                    Text(
                                        text = voiceState.partialTranscript,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                            TextButton(onClick = { viewModel.voiceCommandManager.stopListening() }) {
                                Text("Stop")
                            }
                        }
                    }
                }

                // Overlays & Sheets
                when (overlay) {
                    BrowserOverlay.TabSwitcher -> TabSwitcherSheet(
                        workspace = prefs.activeWorkspace,
                        tabs = tabs,
                        activeTabId = activeTab?.id.orEmpty(),
                        isOrganizing = isOrganizingTabs,
                        onSelectWorkspace = viewModel::switchWorkspace,
                        onSelectTab = viewModel::selectTab,
                        onCloseTab = viewModel::closeTab,
                        onAddTab = { incog -> viewModel.addNewTab(incog) },
                        onTogglePin = viewModel::togglePinTab,
                        onMoveTab = viewModel::moveTab,
                        onAssignGroup = viewModel::assignTabGroup,
                        onAiOrganizeTabs = viewModel::organizeTabsWithAi,
                        onClose = viewModel::closeOverlay
                    )
                    BrowserOverlay.Menu -> BrowserMenuSheet(
                        isDarkTheme = prefs.isDarkTheme,
                        onToggleTheme = viewModel::setDarkTheme,
                        onNewTab = { viewModel.addNewTab(false) },
                        onIncognitoTab = { viewModel.addNewTab(true) },
                        onBookmarkPage = viewModel::toggleCurrentBookmark,
                        onFindInPage = viewModel::openFindInPage,
                        onShare = {
                            viewModel.shareCurrentPage()?.let { shareIntent ->
                                context.startActivity(Intent.createChooser(shareIntent, "Share via"))
                            } ?: viewModel.showStatus("Open a webpage to share it.")
                        },
                        onScreenshot = viewModel::captureFullPageScreenshot,
                        onOpenReader = viewModel::openSmartReader,
                        onOpenWritingHelper = viewModel::openWritingHelper,
                        onNavigateOverlay = viewModel::openOverlay,
                        onDismiss = viewModel::closeOverlay
                    )
                    BrowserOverlay.AiSidePanel -> AiSidePanelSheet(
                        pageTitle = activeTab?.title ?: "New Tab",
                        pageUrl = activeTab?.url ?: "about:newtab",
                        uiState = aiPanelState,
                        chatHistory = aiChatHistory,
                        onQuickAction = viewModel::runAiQuickAction,
                        onAskQuestion = { q -> viewModel.askAiAboutPage(q) },
                        onClearChat = viewModel::clearAiChat,
                        onClose = viewModel::closeOverlay
                    )
                    BrowserOverlay.SmartReader -> SmartReaderOverlay(
                        readerState = readerState,
                        ttsState = ttsState,
                        onToggleTts = viewModel::toggleReaderTts,
                        onSetRate = viewModel.ttsManager::setSpeechRate,
                        onSetFontSize = viewModel::setReaderFontSize,
                        onSetTheme = viewModel::setReaderTheme,
                        onClose = {
                            viewModel.ttsManager.stop()
                            viewModel.closeOverlay()
                        }
                    )
                    BrowserOverlay.AiWritingHelper -> AiWritingHelperDialog(
                        state = writingState,
                        onGenerate = viewModel::generateWritingHelper,
                        onInsertIntoPage = viewModel::insertWritingOutputIntoPage,
                        onClose = viewModel::closeOverlay
                    )
                    BrowserOverlay.AdBlockDashboard -> {
                        val host = viewModel.getCurrentHost()
                        AdBlockDashboardSheet(
                            currentHost = host,
                            globalEnabled = prefs.adBlockEnabled,
                            siteEnabled = siteSettings[host] ?: true,
                            pageStats = tabStats[activeTab?.id] ?: PageBlockStats(),
                            totalLifetimeBlocked = prefs.totalLifetimeBlocked,
                            filterStatus = filterStatus,
                            onToggleGlobal = viewModel::toggleGlobalAdBlock,
                            onToggleSite = viewModel::toggleSiteAdBlock,
                            onRefreshLists = viewModel::refreshFilterLists,
                            onClose = viewModel::closeOverlay
                        )
                    }
                    BrowserOverlay.VpnPanel -> VpnSheet(
                        vpnState = vpnState,
                        formatBytes = viewModel.wireGuardManager::formatBytes,
                        onToggleConnect = { viewModel.toggleVpnConnection(requestVpnFlow) },
                        onImportConfFile = { confPickerLauncher.launch(arrayOf("*/*")) },
                        onScanQrCode = { viewModel.openOverlay(BrowserOverlay.QrScanner) },
                        onSavePastedConfig = viewModel::importWireGuardConfig,
                        onClose = viewModel::closeOverlay
                    )
                    BrowserOverlay.QrScanner -> QrScannerScreen(
                        onQrScanned = { qrText ->
                            viewModel.importWireGuardConfig(qrText, "RehanVPN_QR")
                            viewModel.openOverlay(BrowserOverlay.VpnPanel)
                        },
                        onClose = { viewModel.openOverlay(BrowserOverlay.VpnPanel) }
                    )
                    BrowserOverlay.Bookmarks -> BookmarksScreen(
                        bookmarks = bookmarks,
                        onOpenUrl = viewModel::loadUrlInActiveTab,
                        onDeleteBookmark = viewModel::deleteBookmark,
                        onClose = viewModel::closeOverlay
                    )
                    BrowserOverlay.History -> HistoryScreen(
                        workspace = prefs.activeWorkspace,
                        history = history,
                        onOpenUrl = viewModel::loadUrlInActiveTab,
                        onDeleteItem = viewModel::deleteHistoryItem,
                        onClearWorkspaceHistory = viewModel::clearWorkspaceHistory,
                        onClose = viewModel::closeOverlay
                    )
                    BrowserOverlay.Downloads -> DownloadsScreen(
                        downloads = downloads,
                        onDeleteDownload = viewModel::deleteDownload,
                        onClose = viewModel::closeOverlay
                    )
                    BrowserOverlay.PrivacySettings -> PrivacySettingsSheet(
                        prefs = prefs,
                        dohTestResult = dohTestResult,
                        isTestingDoh = isTestingDoh,
                        onSetDarkTheme = viewModel::setDarkTheme,
                        onSetLanguage = viewModel::setLanguageCode,
                        onSetSearchEngine = viewModel::setSearchEngine,
                        onSetBlockThirdPartyCookies = viewModel::setBlockThirdPartyCookies,
                        onSetBlockAllCookies = viewModel::setBlockAllCookies,
                        onSetSendDnt = viewModel::setSendDntHeader,
                        onSetHttpsOnly = viewModel::setHttpsOnlyMode,
                        onSetJavaScript = viewModel::setJavaScriptEnabled,
                        onSetDohProvider = viewModel::setDohProvider,
                        onTestDoh = viewModel::testDohLookup,
                        onClearBrowsingData = viewModel::clearBrowsingData,
                        onClose = viewModel::closeOverlay
                    )
                    BrowserOverlay.About -> AboutScreen(onClose = viewModel::closeOverlay)
                    BrowserOverlay.None -> {}
                }

                // Full-page screenshot share prompt
                latestScreenshot?.let { shot ->
                    AlertDialog(
                        onDismissRequest = viewModel::dismissScreenshotPreview,
                        title = { Text("Full-Page Screenshot Saved") },
                        text = {
                            Text("Captured full webpage (${shot.width} × ${shot.height} px) to Pictures/RehanBrowser.")
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "image/png"
                                        putExtra(Intent.EXTRA_STREAM, shot.uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Screenshot"))
                                    viewModel.dismissScreenshotPreview()
                                }
                            ) {
                                Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Share")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = viewModel::dismissScreenshotPreview) {
                                Text("Done")
                            }
                        }
                    )
                }

                // Splash Screen Overlay
                AnimatedVisibility(
                    visible = isSplashVisible,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    SplashScreen()
                }
            }
        }
    }
}
