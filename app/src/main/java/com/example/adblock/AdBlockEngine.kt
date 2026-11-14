package com.example.adblock

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class BlockMatchResult(
    val blocked: Boolean,
    val category: BlockCategory = BlockCategory.AD,
    val matchedRule: String = ""
)

class AdBlockEngine(private val context: Context) {

    companion object {
        const val EASYLIST_URL = "https://easylist.to/easylist/easylist.txt"
        const val EASYPRIVACY_URL = "https://easylist.to/easylist/easyprivacy.txt"
        private const val UPDATE_INTERVAL_MS = 24L * 60L * 60L * 1000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var easyListRules = ParsedFilterRules(
        emptySet(), emptySet(), emptyList(), emptyList(), emptyMap(), emptyMap(), 0
    )

    @Volatile
    private var easyPrivacyRules = ParsedFilterRules(
        emptySet(), emptySet(), emptyList(), emptyList(), emptyMap(), emptyMap(), 0
    )

    private val _filterStatus = MutableStateFlow(FilterListStatus())
    val filterStatus: StateFlow<FilterListStatus> = _filterStatus.asStateFlow()

    private val tabStatsMap = ConcurrentHashMap<String, PageBlockStats>()
    private val _tabStats = MutableStateFlow<Map<String, PageBlockStats>>(emptyMap())
    val tabStats: StateFlow<Map<String, PageBlockStats>> = _tabStats.asStateFlow()

    private val filterDir: File by lazy {
        File(context.filesDir, "filter_lists").apply { mkdirs() }
    }

    fun initialize(lastUpdatedMs: Long, autoUpdate: Boolean, onUpdated: (Long) -> Unit) {
        scope.launch {
            loadLocalFilterLists(lastUpdatedMs)
            val isStale = System.currentTimeMillis() - lastUpdatedMs > UPDATE_INTERVAL_MS
            if (autoUpdate && isStale) {
                updateFilterLists(onUpdated)
            }
        }
    }

    private fun loadLocalFilterLists(lastUpdatedMs: Long) {
        val easyListFile = File(filterDir, "easylist.txt")
        val easyPrivacyFile = File(filterDir, "easyprivacy.txt")

        val parsedAd = if (easyListFile.exists() && easyListFile.length() > 500) {
            easyListFile.bufferedReader().useLines { AdBlockFilterParser.parse(it) }
        } else {
            context.assets.open("easylist.txt").bufferedReader().useLines { AdBlockFilterParser.parse(it) }
        }

        val parsedPrivacy = if (easyPrivacyFile.exists() && easyPrivacyFile.length() > 300) {
            easyPrivacyFile.bufferedReader().useLines { AdBlockFilterParser.parse(it) }
        } else {
            context.assets.open("easyprivacy.txt").bufferedReader().useLines { AdBlockFilterParser.parse(it) }
        }

        easyListRules = parsedAd
        easyPrivacyRules = parsedPrivacy
        _filterStatus.value = FilterListStatus(
            easyListRuleCount = parsedAd.totalRuleCount,
            easyPrivacyRuleCount = parsedPrivacy.totalRuleCount,
            cosmeticRuleCount = parsedAd.globalCosmeticSelectors.size + parsedAd.domainCosmeticSelectors.values.sumOf { it.size },
            lastUpdatedMs = lastUpdatedMs,
            isUpdating = false
        )
    }

    suspend fun updateFilterLists(onUpdated: (Long) -> Unit): Boolean = withContext(Dispatchers.IO) {
        _filterStatus.update { it.copy(isUpdating = true, lastError = null) }
        return@withContext try {
            val easyListSuccess = downloadList(EASYLIST_URL, File(filterDir, "easylist.txt"))
            val easyPrivacySuccess = downloadList(EASYPRIVACY_URL, File(filterDir, "easyprivacy.txt"))
            if (easyListSuccess || easyPrivacySuccess) {
                val now = System.currentTimeMillis()
                loadLocalFilterLists(now)
                onUpdated(now)
                true
            } else {
                _filterStatus.update {
                    it.copy(isUpdating = false, lastError = "Could not download filter lists. Using cached rules.")
                }
                false
            }
        } catch (e: Exception) {
            _filterStatus.update {
                it.copy(isUpdating = false, lastError = e.localizedMessage ?: "Filter update failed.")
            }
            false
        }
    }

    private fun downloadList(url: String, target: File): Boolean {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "RehanBrowser/1.0")
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return false
            val body = response.body ?: return false
            val temp = File(target.parentFile, "${target.name}.tmp")
            temp.outputStream().use { out ->
                body.byteStream().copyTo(out)
            }
            if (temp.length() > 1000) {
                temp.renameTo(target)
                return true
            }
            temp.delete()
            return false
        }
    }

    fun evaluateRequest(requestUrl: String, pageHost: String): BlockMatchResult {
        val uri = try {
            Uri.parse(requestUrl)
        } catch (_: Exception) {
            return BlockMatchResult(false)
        }
        val reqHost = uri.host?.lowercase() ?: return BlockMatchResult(false)
        val cleanPageHost = pageHost.lowercase()

        // Never block the main page host itself directly via domain rules
        if (reqHost == cleanPageHost) {
            return BlockMatchResult(false)
        }

        val lowerUrl = requestUrl.lowercase()

        // Check EasyPrivacy first (trackers)
        val privacyMatch = matchAgainstRules(reqHost, lowerUrl, easyPrivacyRules)
        if (privacyMatch != null) {
            return BlockMatchResult(true, BlockCategory.TRACKER, privacyMatch)
        }

        // Check EasyList (ads)
        val adMatch = matchAgainstRules(reqHost, lowerUrl, easyListRules)
        if (adMatch != null) {
            return BlockMatchResult(true, BlockCategory.AD, adMatch)
        }

        return BlockMatchResult(false)
    }

    private fun matchAgainstRules(
        reqHost: String,
        lowerUrl: String,
        rules: ParsedFilterRules
    ): String? {
        if (isHostInSet(reqHost, rules.exceptionDomains)) return null

        val matchedDomain = findMatchingDomain(reqHost, rules.blockedDomains)
        if (matchedDomain != null) return "||$matchedDomain^"

        for (pattern in rules.patternRules) {
            if (lowerUrl.contains(pattern)) {
                return pattern
            }
        }
        return null
    }

    private fun isHostInSet(host: String, set: Set<String>): Boolean =
        findMatchingDomain(host, set) != null

    private fun findMatchingDomain(host: String, set: Set<String>): String? {
        if (set.isEmpty()) return null
        var current = host
        while (true) {
            if (set.contains(current)) return current
            val dot = current.indexOf('.')
            if (dot < 0 || dot == current.length - 1) break
            current = current.substring(dot + 1)
        }
        return null
    }

    fun recordBlocked(tabId: String, requestUrl: String, result: BlockMatchResult) {
        val host = try {
            Uri.parse(requestUrl).host ?: requestUrl
        } catch (_: Exception) {
            requestUrl
        }
        val log = BlockedRequestLog(
            url = requestUrl,
            host = host,
            category = result.category,
            matchedRule = result.matchedRule
        )
        tabStatsMap.compute(tabId) { _, existing ->
            val prev = existing ?: PageBlockStats()
            val updatedLogs = (listOf(log) + prev.recentBlocked).take(40)
            prev.copy(
                adsBlocked = prev.adsBlocked + if (result.category == BlockCategory.AD) 1 else 0,
                trackersBlocked = prev.trackersBlocked + if (result.category == BlockCategory.TRACKER) 1 else 0,
                recentBlocked = updatedLogs
            )
        }
        _tabStats.value = HashMap(tabStatsMap)
    }

    fun resetTabStats(tabId: String) {
        tabStatsMap[tabId] = PageBlockStats()
        _tabStats.value = HashMap(tabStatsMap)
    }

    fun removeTabStats(tabId: String) {
        tabStatsMap.remove(tabId)
        _tabStats.value = HashMap(tabStatsMap)
    }

    fun buildCosmeticCssInjectionJs(pageHost: String): String {
        val cleanHost = pageHost.lowercase()
        val exceptions = HashSet<String>()
        var curr = cleanHost
        while (true) {
            easyListRules.domainCosmeticExceptions[curr]?.let { exceptions.addAll(it) }
            val dot = curr.indexOf('.')
            if (dot < 0 || dot == curr.length - 1) break
            curr = curr.substring(dot + 1)
        }

        val selectors = LinkedHashSet<String>()
        for (sel in easyListRules.globalCosmeticSelectors) {
            if (!exceptions.contains(sel)) selectors.add(sel)
            if (selectors.size >= 350) break
        }

        curr = cleanHost
        while (true) {
            easyListRules.domainCosmeticSelectors[curr]?.forEach { sel ->
                if (!exceptions.contains(sel)) selectors.add(sel)
            }
            val dot = curr.indexOf('.')
            if (dot < 0 || dot == curr.length - 1) break
            curr = curr.substring(dot + 1)
        }

        if (selectors.isEmpty()) return ""
        val cssRule = selectors.joinToString(",") + "{display:none !important;visibility:hidden !important;}"
        val encodedCss = JSONArray(listOf(cssRule)).toString().removePrefix("[").removeSuffix("]")
        return """
            (function() {
              try {
                var id = 'rehan-adblock-cosmetic';
                var existing = document.getElementById(id);
                if (existing) existing.remove();
                var style = document.createElement('style');
                style.id = id;
                style.textContent = $encodedCss;
                (document.head || document.documentElement).appendChild(style);
              } catch(e) {}
            })();
        """.trimIndent()
    }
}
