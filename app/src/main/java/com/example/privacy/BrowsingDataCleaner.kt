package com.example.privacy

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewDatabase
import com.example.data.repository.BrowserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ClearDataOptions(
    val clearHistory: Boolean = true,
    val clearCache: Boolean = true,
    val clearCookies: Boolean = true,
    val clearFormData: Boolean = true,
    val clearDownloadsList: Boolean = false
)

object BrowsingDataCleaner {
    suspend fun clear(
        context: Context,
        repository: BrowserRepository,
        webViews: Collection<WebView>,
        options: ClearDataOptions
    ) {
        withContext(Dispatchers.Main) {
            if (options.clearCache) {
                webViews.forEach { it.clearCache(true) }
            }
            if (options.clearCookies) {
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
                WebStorage.getInstance().deleteAllData()
            }
            if (options.clearFormData) {
                webViews.forEach { it.clearFormData() }
                @Suppress("DEPRECATION")
                WebViewDatabase.getInstance(context).clearFormData()
            }
            if (options.clearHistory) {
                webViews.forEach { it.clearHistory() }
            }
        }
        withContext(Dispatchers.IO) {
            if (options.clearHistory) {
                repository.clearHistory(workspace = null)
            }
            if (options.clearDownloadsList) {
                repository.clearDownloads()
            }
        }
    }
}
