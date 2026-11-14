package com.example.browser

import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.adblock.AdBlockEngine
import com.example.privacy.DohProvider
import com.example.privacy.DohResolver
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.util.concurrent.TimeUnit

class BrowserWebViewClient(
    private val tabId: String,
    private val isIncognito: Boolean,
    private val adBlockEngine: AdBlockEngine,
    private val dohResolver: DohResolver,
    private val isGlobalAdBlockEnabled: () -> Boolean,
    private val isSiteAdBlockEnabled: (String) -> Boolean,
    private val isHttpsOnlyEnabled: () -> Boolean,
    private val isSendDntEnabled: () -> Boolean,
    private val onBlockedRequest: (tabId: String) -> Unit,
    private val onPageStartedCallback: (tabId: String, url: String) -> Unit,
    private val onPageFinishedCallback: (tabId: String, url: String, title: String, canGoBack: Boolean, canGoForward: Boolean) -> Unit,
    private val onRecordHistory: (tabId: String, title: String, url: String) -> Unit
) : WebViewClient() {

    @Volatile
    private var currentPageHost: String = ""

    private val dohHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(dohResolver.okHttpDns)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .cookieJar(object : CookieJar {
                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                    val cm = CookieManager.getInstance()
                    if (!cm.acceptCookie()) return
                    for (cookie in cookies) {
                        cm.setCookie(url.toString(), cookie.toString())
                    }
                }

                override fun loadForRequest(url: HttpUrl): List<Cookie> {
                    val cm = CookieManager.getInstance()
                    if (!cm.acceptCookie()) return emptyList()
                    val raw = cm.getCookie(url.toString()) ?: return emptyList()
                    return raw.split(";").mapNotNull { part ->
                        Cookie.parse(url, part.trim())
                    }
                }
            })
            .build()
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url?.toString() ?: return false
        if (isHttpsOnlyEnabled() && url.startsWith("http://")) {
            val upgraded = "https://" + url.removePrefix("http://")
            view?.loadUrl(upgraded)
            return true
        }
        return false
    }

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        val req = request ?: return null
        val url = req.url?.toString() ?: return null
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return null
        }

        if (req.isForMainFrame) {
            currentPageHost = req.url.host?.lowercase().orEmpty()
        }

        val hostForRule = currentPageHost.ifBlank { req.url.host?.lowercase().orEmpty() }

        // 1. Real EasyList & EasyPrivacy network blocking
        if (isGlobalAdBlockEnabled() && isSiteAdBlockEnabled(hostForRule)) {
            val match = adBlockEngine.evaluateRequest(url, hostForRule)
            if (match.blocked) {
                adBlockEngine.recordBlocked(tabId, url, match)
                onBlockedRequest(tabId)
                return WebResourceResponse(
                    "text/plain",
                    "utf-8",
                    ByteArrayInputStream(ByteArray(0))
                )
            }
        }

        // 2. DNS-over-HTTPS routing for GET document/subresource requests when enabled
        if (dohResolver.activeProvider != DohProvider.SYSTEM &&
            req.method.equals("GET", ignoreCase = true) &&
            req.isForMainFrame
        ) {
            try {
                val builder = Request.Builder().url(url)
                req.requestHeaders?.forEach { (k, v) ->
                    if (!k.equals("Host", ignoreCase = true)) {
                        builder.header(k, v)
                    }
                }
                if (isSendDntEnabled()) {
                    builder.header("DNT", "1")
                    builder.header("Sec-GPC", "1")
                }
                val resp = dohHttpClient.newCall(builder.build()).execute()
                val body = resp.body
                if (resp.isSuccessful && body != null) {
                    val contentType = resp.header("Content-Type") ?: "text/html"
                    val mime = contentType.substringBefore(";").trim().ifBlank { "text/html" }
                    val charset = if (contentType.contains("charset=", ignoreCase = true)) {
                        contentType.substringAfter("charset=").substringBefore(";").trim()
                    } else "utf-8"
                    return WebResourceResponse(
                        mime,
                        charset,
                        resp.code,
                        resp.message.ifBlank { "OK" },
                        resp.headers.toMultimap().mapValues { it.value.joinToString(", ") },
                        body.byteStream()
                    )
                } else {
                    resp.close()
                }
            } catch (_: Exception) {
                // Fall back to default WebView loader if DoH fetch fails
            }
        }

        return null
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        val safeUrl = url.orEmpty()
        currentPageHost = try {
            Uri.parse(safeUrl).host?.lowercase().orEmpty()
        } catch (_: Exception) {
            ""
        }
        adBlockEngine.resetTabStats(tabId)
        onPageStartedCallback(tabId, safeUrl)
    }

    override fun onPageCommitVisible(view: WebView?, url: String?) {
        super.onPageCommitVisible(view, url)
        injectCosmeticRules(view, url)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        injectCosmeticRules(view, url)
        val safeUrl = url.orEmpty()
        val title = view?.title?.takeIf { it.isNotBlank() } ?: safeUrl
        onPageFinishedCallback(
            tabId,
            safeUrl,
            title,
            view?.canGoBack() ?: false,
            view?.canGoForward() ?: false
        )
        if (!isIncognito && safeUrl.startsWith("http")) {
            onRecordHistory(tabId, title, safeUrl)
        }
    }

    private fun injectCosmeticRules(view: WebView?, url: String?) {
        if (view == null) return
        val host = try {
            Uri.parse(url.orEmpty()).host?.lowercase().orEmpty()
        } catch (_: Exception) {
            currentPageHost
        }
        if (isGlobalAdBlockEnabled() && isSiteAdBlockEnabled(host)) {
            val js = adBlockEngine.buildCosmeticCssInjectionJs(host)
            if (js.isNotEmpty()) {
                view.evaluateJavascript(js, null)
            }
        }
    }
}
