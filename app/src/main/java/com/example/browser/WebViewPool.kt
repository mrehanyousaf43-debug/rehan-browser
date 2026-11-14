package com.example.browser

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.core.content.FileProvider
import com.example.adblock.AdBlockEngine
import com.example.data.local.BrowserPreferencesState
import com.example.privacy.DohResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

data class CapturedScreenshot(
    val uri: Uri,
    val width: Int,
    val height: Int
)

class WebViewPool(
    private val context: Context,
    private val adBlockEngine: AdBlockEngine,
    private val dohResolver: DohResolver
) {
    companion object {
        @Volatile
        private var slowDrawEnabled = false

        fun ensureSlowWholeDocumentDraw() {
            if (!slowDrawEnabled) {
                try {
                    WebView.enableSlowWholeDocumentDraw()
                } catch (_: Exception) {
                }
                slowDrawEnabled = true
            }
        }
    }

    private val webViews = ConcurrentHashMap<String, WebView>()

    @SuppressLint("SetJavaScriptEnabled")
    fun getOrCreateWebView(
        tabId: String,
        isIncognito: Boolean,
        prefs: BrowserPreferencesState,
        isSiteAdBlockEnabled: (String) -> Boolean,
        onBlockedRequest: (String) -> Unit,
        onProgressChanged: (String, Int) -> Unit,
        onTitleChanged: (String, String) -> Unit,
        onPageStarted: (String, String) -> Unit,
        onPageFinished: (String, String, String, Boolean, Boolean) -> Unit,
        onRecordHistory: (String, String, String) -> Unit,
        onDownloadRequest: (url: String, ua: String?, cd: String?, mime: String?) -> Unit,
        onFindResult: (activeMatchOrdinal: Int, numberOfMatches: Int) -> Unit
    ): WebView {
        ensureSlowWholeDocumentDraw()
        val existing = webViews[tabId]
        if (existing != null) {
            applyPrivacySettings(existing, isIncognito, prefs)
            return existing
        }

        val wv = WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            applyPrivacySettings(this, isIncognito, prefs)

            webViewClient = BrowserWebViewClient(
                tabId = tabId,
                isIncognito = isIncognito,
                adBlockEngine = adBlockEngine,
                dohResolver = dohResolver,
                isGlobalAdBlockEnabled = { prefsSnapshot.adBlockEnabled },
                isSiteAdBlockEnabled = isSiteAdBlockEnabled,
                isHttpsOnlyEnabled = { prefsSnapshot.httpsOnlyMode },
                isSendDntEnabled = { prefsSnapshot.sendDntHeader },
                onBlockedRequest = onBlockedRequest,
                onPageStartedCallback = onPageStarted,
                onPageFinishedCallback = onPageFinished,
                onRecordHistory = onRecordHistory
            )

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    onProgressChanged(tabId, newProgress)
                }

                override fun onReceivedTitle(view: WebView?, title: String?) {
                    if (!title.isNullOrBlank()) {
                        onTitleChanged(tabId, title)
                    }
                }
            }

            setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
                onDownloadRequest(url, userAgent, contentDisposition, mimetype)
            }

            setFindListener { activeMatchOrdinal, numberOfMatches, isDoneCounting ->
                if (isDoneCounting) {
                    onFindResult(activeMatchOrdinal, numberOfMatches)
                }
            }
        }
        webViews[tabId] = wv
        return wv
    }

    @Volatile
    private var prefsSnapshot: BrowserPreferencesState = BrowserPreferencesState()

    fun updateAllPrivacySettings(prefs: BrowserPreferencesState) {
        prefsSnapshot = prefs
        webViews.forEach { (_, wv) ->
            // Incognito is preserved via tag
            val incog = (wv.tag as? Boolean) ?: false
            applyPrivacySettings(wv, incog, prefs)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun applyPrivacySettings(
        webView: WebView,
        isIncognito: Boolean,
        prefs: BrowserPreferencesState
    ) {
        webView.tag = isIncognito
        webView.settings.apply {
            javaScriptEnabled = prefs.javaScriptEnabled
            domStorageEnabled = !isIncognito
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            if (isIncognito) {
                cacheMode = WebSettings.LOAD_NO_CACHE
                @Suppress("DEPRECATION")
                saveFormData = false
            } else {
                cacheMode = WebSettings.LOAD_DEFAULT
            }
        }
        val cm = CookieManager.getInstance()
        val acceptCookies = !prefs.blockAllCookies && !isIncognito
        cm.setAcceptCookie(acceptCookies)
        cm.setAcceptThirdPartyCookies(webView, acceptCookies && !prefs.blockThirdPartyCookies)
    }

    fun getWebView(tabId: String): WebView? = webViews[tabId]

    fun allWebViews(): Collection<WebView> = webViews.values

    fun destroyTab(tabId: String) {
        val wv = webViews.remove(tabId) ?: return
        val wasIncognito = (wv.tag as? Boolean) == true
        if (wasIncognito) {
            wv.clearCache(true)
            wv.clearHistory()
            wv.clearFormData()
        }
        (wv.parent as? ViewGroup)?.removeView(wv)
        wv.destroy()
    }

    suspend fun extractPageText(tabId: String): String = suspendCancellableCoroutine { cont ->
        val wv = webViews[tabId]
        if (wv == null) {
            cont.resume("")
            return@suspendCancellableCoroutine
        }
        val script = """
            (function() {
              try {
                var article = document.querySelector('article') || document.querySelector('main') || document.body;
                return article ? article.innerText : '';
              } catch(e) { return ''; }
            })();
        """.trimIndent()
        wv.evaluateJavascript(script) { raw ->
            val unescaped = try {
                if (raw == null || raw == "null") ""
                else JSONArray("[$raw]").getString(0)
            } catch (_: Exception) {
                raw.orEmpty().trim('"')
            }
            if (cont.isActive) cont.resume(unescaped)
        }
    }

    suspend fun extractFocusedFieldText(tabId: String): String = suspendCancellableCoroutine { cont ->
        val wv = webViews[tabId]
        if (wv == null) {
            cont.resume("")
            return@suspendCancellableCoroutine
        }
        val script = """
            (function() {
              var el = document.activeElement;
              if (!el) return '';
              if (el.tagName === 'TEXTAREA' || el.tagName === 'INPUT') return el.value || '';
              if (el.isContentEditable) return el.innerText || '';
              return '';
            })();
        """.trimIndent()
        wv.evaluateJavascript(script) { raw ->
            val unescaped = try {
                if (raw == null || raw == "null") ""
                else JSONArray("[$raw]").getString(0)
            } catch (_: Exception) {
                ""
            }
            if (cont.isActive) cont.resume(unescaped)
        }
    }

    fun insertTextIntoActiveField(tabId: String, text: String) {
        val wv = webViews[tabId] ?: return
        val encoded = JSONArray(listOf(text)).toString().removePrefix("[").removeSuffix("]")
        val script = """
            (function() {
              var val = $encoded;
              var el = document.activeElement;
              if (!el || (el.tagName !== 'TEXTAREA' && el.tagName !== 'INPUT' && !el.isContentEditable)) {
                el = document.querySelector('textarea, input[type="text"], input[type="search"], input:not([type]), [contenteditable="true"]');
              }
              if (!el) return false;
              el.focus();
              if (el.isContentEditable) {
                el.innerText = val;
              } else {
                el.value = val;
                el.dispatchEvent(new Event('input', { bubbles: true }));
                el.dispatchEvent(new Event('change', { bubbles: true }));
              }
              return true;
            })();
        """.trimIndent()
        wv.evaluateJavascript(script, null)
    }

    suspend fun captureFullPageScreenshot(tabId: String, title: String): CapturedScreenshot? {
        val wv = webViews[tabId] ?: return null
        val bitmap = withContext(Dispatchers.Main) {
            val width = wv.width.coerceAtLeast(1)
            @Suppress("DEPRECATION")
            val scale = wv.scale
            val fullHeight = (wv.contentHeight * scale).toInt().coerceIn(wv.height.coerceAtLeast(1), 8192)
            val bmp = Bitmap.createBitmap(width, fullHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val oldScrollX = wv.scrollX
            val oldScrollY = wv.scrollY
            wv.scrollTo(0, 0)
            wv.draw(canvas)
            wv.scrollTo(oldScrollX, oldScrollY)
            bmp
        }

        return withContext(Dispatchers.IO) {
            try {
                val safeName = title.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(30).ifBlank { "page" }
                val fileName = "RehanBrowser_${safeName}_${System.currentTimeMillis()}.png"

                // Also write to cache for FileProvider instant share
                val cacheDir = File(context.cacheDir, "screenshots").apply { mkdirs() }
                val cacheFile = File(cacheDir, fileName)
                FileOutputStream(cacheFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
                }

                // Save to MediaStore Pictures/RehanBrowser on Android 10+ (zero permissions required)
                val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/RehanBrowser")
                    }
                    val resolver = context.contentResolver
                    val inserted = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    if (inserted != null) {
                        resolver.openOutputStream(inserted)?.use { out ->
                            bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
                        }
                        inserted
                    } else {
                        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", cacheFile)
                    }
                } else {
                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", cacheFile)
                }
                CapturedScreenshot(uri, bitmap.width, bitmap.height)
            } catch (_: Exception) {
                null
            } finally {
                bitmap.recycle()
            }
        }
    }

    fun destroyAll() {
        webViews.keys.toList().forEach { destroyTab(it) }
    }
}
