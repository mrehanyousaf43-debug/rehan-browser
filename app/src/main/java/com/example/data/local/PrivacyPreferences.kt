package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.privacyDataStore by preferencesDataStore(name = "rehan_browser_prefs")

enum class SearchEngine(val id: String, val displayName: String, val searchTemplate: String) {
    DUCKDUCKGO("ddg", "DuckDuckGo", "https://duckduckgo.com/?q=%s"),
    GOOGLE("google", "Google", "https://www.google.com/search?q=%s"),
    BRAVE("brave", "Brave Search", "https://search.brave.com/search?q=%s"),
    WIKIPEDIA("wiki", "Wikipedia", "https://en.wikipedia.org/wiki/Special:Search?search=%s");

    companion object {
        fun fromId(id: String): SearchEngine =
            entries.firstOrNull { it.id == id } ?: DUCKDUCKGO
    }
}

data class BrowserPreferencesState(
    val isDarkTheme: Boolean = true,
    val languageCode: String = "en",
    val activeWorkspace: WorkspaceType = WorkspaceType.RESEARCH,
    val activeTabId: String = "",
    val adBlockEnabled: Boolean = true,
    val blockThirdPartyCookies: Boolean = true,
    val blockAllCookies: Boolean = false,
    val sendDntHeader: Boolean = true,
    val httpsOnlyMode: Boolean = false,
    val javaScriptEnabled: Boolean = true,
    val dohProviderId: String = "cloudflare",
    val searchEngineId: String = SearchEngine.DUCKDUCKGO.id,
    val wireGuardConfigText: String = "",
    val wireGuardTunnelName: String = "RehanVPN",
    val autoUpdateFilters: Boolean = true,
    val lastFilterUpdateMs: Long = 0L,
    val totalLifetimeBlocked: Long = 0L
)

class PrivacyPreferences(private val context: Context) {

    private object Keys {
        val IS_DARK_THEME = booleanPreferencesKey("is_dark_theme")
        val LANGUAGE_CODE = stringPreferencesKey("language_code")
        val ACTIVE_WORKSPACE = stringPreferencesKey("active_workspace")
        val ACTIVE_TAB_ID = stringPreferencesKey("active_tab_id")
        val ADBLOCK_ENABLED = booleanPreferencesKey("adblock_enabled")
        val BLOCK_THIRD_PARTY_COOKIES = booleanPreferencesKey("block_third_party_cookies")
        val BLOCK_ALL_COOKIES = booleanPreferencesKey("block_all_cookies")
        val SEND_DNT_HEADER = booleanPreferencesKey("send_dnt_header")
        val HTTPS_ONLY_MODE = booleanPreferencesKey("https_only_mode")
        val JAVASCRIPT_ENABLED = booleanPreferencesKey("javascript_enabled")
        val DOH_PROVIDER_ID = stringPreferencesKey("doh_provider_id")
        val SEARCH_ENGINE_ID = stringPreferencesKey("search_engine_id")
        val WIREGUARD_CONFIG_TEXT = stringPreferencesKey("wireguard_config_text")
        val WIREGUARD_TUNNEL_NAME = stringPreferencesKey("wireguard_tunnel_name")
        val AUTO_UPDATE_FILTERS = booleanPreferencesKey("auto_update_filters")
        val LAST_FILTER_UPDATE_MS = longPreferencesKey("last_filter_update_ms")
        val TOTAL_LIFETIME_BLOCKED = longPreferencesKey("total_lifetime_blocked")
    }

    val preferencesFlow: Flow<BrowserPreferencesState> = context.privacyDataStore.data.map { prefs ->
        val workspaceName = prefs[Keys.ACTIVE_WORKSPACE] ?: WorkspaceType.RESEARCH.name
        BrowserPreferencesState(
            isDarkTheme = prefs[Keys.IS_DARK_THEME] ?: true,
            languageCode = prefs[Keys.LANGUAGE_CODE] ?: "en",
            activeWorkspace = WorkspaceType.entries.firstOrNull { it.name == workspaceName }
                ?: WorkspaceType.RESEARCH,
            activeTabId = prefs[Keys.ACTIVE_TAB_ID] ?: "",
            adBlockEnabled = prefs[Keys.ADBLOCK_ENABLED] ?: true,
            blockThirdPartyCookies = prefs[Keys.BLOCK_THIRD_PARTY_COOKIES] ?: true,
            blockAllCookies = prefs[Keys.BLOCK_ALL_COOKIES] ?: false,
            sendDntHeader = prefs[Keys.SEND_DNT_HEADER] ?: true,
            httpsOnlyMode = prefs[Keys.HTTPS_ONLY_MODE] ?: false,
            javaScriptEnabled = prefs[Keys.JAVASCRIPT_ENABLED] ?: true,
            dohProviderId = prefs[Keys.DOH_PROVIDER_ID] ?: "cloudflare",
            searchEngineId = prefs[Keys.SEARCH_ENGINE_ID] ?: SearchEngine.DUCKDUCKGO.id,
            wireGuardConfigText = prefs[Keys.WIREGUARD_CONFIG_TEXT] ?: "",
            wireGuardTunnelName = prefs[Keys.WIREGUARD_TUNNEL_NAME] ?: "RehanVPN",
            autoUpdateFilters = prefs[Keys.AUTO_UPDATE_FILTERS] ?: true,
            lastFilterUpdateMs = prefs[Keys.LAST_FILTER_UPDATE_MS] ?: 0L,
            totalLifetimeBlocked = prefs[Keys.TOTAL_LIFETIME_BLOCKED] ?: 0L
        )
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.privacyDataStore.edit { it[Keys.IS_DARK_THEME] = enabled }
    }

    suspend fun setLanguageCode(code: String) {
        context.privacyDataStore.edit { it[Keys.LANGUAGE_CODE] = code }
    }

    suspend fun setActiveWorkspace(workspace: WorkspaceType) {
        context.privacyDataStore.edit { it[Keys.ACTIVE_WORKSPACE] = workspace.name }
    }

    suspend fun setActiveTabId(tabId: String) {
        context.privacyDataStore.edit { it[Keys.ACTIVE_TAB_ID] = tabId }
    }

    suspend fun setAdBlockEnabled(enabled: Boolean) {
        context.privacyDataStore.edit { it[Keys.ADBLOCK_ENABLED] = enabled }
    }

    suspend fun setBlockThirdPartyCookies(enabled: Boolean) {
        context.privacyDataStore.edit { it[Keys.BLOCK_THIRD_PARTY_COOKIES] = enabled }
    }

    suspend fun setBlockAllCookies(enabled: Boolean) {
        context.privacyDataStore.edit { it[Keys.BLOCK_ALL_COOKIES] = enabled }
    }

    suspend fun setSendDntHeader(enabled: Boolean) {
        context.privacyDataStore.edit { it[Keys.SEND_DNT_HEADER] = enabled }
    }

    suspend fun setHttpsOnlyMode(enabled: Boolean) {
        context.privacyDataStore.edit { it[Keys.HTTPS_ONLY_MODE] = enabled }
    }

    suspend fun setJavaScriptEnabled(enabled: Boolean) {
        context.privacyDataStore.edit { it[Keys.JAVASCRIPT_ENABLED] = enabled }
    }

    suspend fun setDohProviderId(id: String) {
        context.privacyDataStore.edit { it[Keys.DOH_PROVIDER_ID] = id }
    }

    suspend fun setSearchEngineId(id: String) {
        context.privacyDataStore.edit { it[Keys.SEARCH_ENGINE_ID] = id }
    }

    suspend fun saveWireGuardConfig(configText: String, tunnelName: String) {
        context.privacyDataStore.edit {
            it[Keys.WIREGUARD_CONFIG_TEXT] = configText
            it[Keys.WIREGUARD_TUNNEL_NAME] = tunnelName
        }
    }

    suspend fun setAutoUpdateFilters(enabled: Boolean) {
        context.privacyDataStore.edit { it[Keys.AUTO_UPDATE_FILTERS] = enabled }
    }

    suspend fun setLastFilterUpdateMs(timestampMs: Long) {
        context.privacyDataStore.edit { it[Keys.LAST_FILTER_UPDATE_MS] = timestampMs }
    }

    suspend fun incrementLifetimeBlocked(delta: Long = 1L) {
        context.privacyDataStore.edit {
            val current = it[Keys.TOTAL_LIFETIME_BLOCKED] ?: 0L
            it[Keys.TOTAL_LIFETIME_BLOCKED] = current + delta
        }
    }
}
