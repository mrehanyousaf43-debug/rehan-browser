package com.example.privacy

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.InetAddress
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

enum class DohProvider(
    val id: String,
    val displayName: String,
    val jsonEndpoint: String?,
    val bootstrapIps: List<String>
) {
    SYSTEM("system", "Off (System DNS)", null, emptyList()),
    CLOUDFLARE("cloudflare", "Cloudflare (1.1.1.1)", "https://cloudflare-dns.com/dns-query", listOf("1.1.1.1", "1.0.0.1")),
    GOOGLE("google", "Google Public DNS", "https://dns.google/resolve", listOf("8.8.8.8", "8.8.4.4")),
    QUAD9("quad9", "Quad9 Security", "https://dns.quad9.net:5053/dns-query", listOf("9.9.9.9", "149.112.112.112")),
    ADGUARD("adguard", "AdGuard DoH", "https://dns.adguard-dns.com/resolve", listOf("94.140.14.14", "94.140.15.15"));

    companion object {
        fun fromId(id: String): DohProvider =
            entries.firstOrNull { it.id == id } ?: CLOUDFLARE
    }
}

data class DohLookupResult(
    val host: String,
    val providerName: String,
    val addresses: List<String>,
    val latencyMs: Long,
    val error: String? = null
)

class DohResolver {
    private val cache = ConcurrentHashMap<String, Pair<Long, List<InetAddress>>>()
    private val bootstrapClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    @Volatile
    var activeProvider: DohProvider = DohProvider.CLOUDFLARE

    val okHttpDns: Dns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val provider = activeProvider
            if (provider == DohProvider.SYSTEM || provider.jsonEndpoint == null) {
                return Dns.SYSTEM.lookup(hostname)
            }
            val cached = cache[hostname]
            if (cached != null && System.currentTimeMillis() - cached.first < 120_000L) {
                return cached.second
            }
            val resolved = resolveSync(hostname, provider)
            return if (resolved.isNotEmpty()) {
                cache[hostname] = System.currentTimeMillis() to resolved
                resolved
            } else {
                Dns.SYSTEM.lookup(hostname)
            }
        }
    }

    private fun resolveSync(hostname: String, provider: DohProvider): List<InetAddress> {
        val base = provider.jsonEndpoint ?: return emptyList()
        return try {
            val encodedHost = URLEncoder.encode(hostname, "UTF-8")
            val url = "$base?name=$encodedHost&type=A".toHttpUrlOrNull() ?: return emptyList()
            val req = Request.Builder()
                .url(url)
                .header("Accept", "application/dns-json")
                .build()
            bootstrapClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val body = resp.body?.string() ?: return emptyList()
                val json = JSONObject(body)
                val answers = json.optJSONArray("Answer") ?: return emptyList()
                val result = mutableListOf<InetAddress>()
                for (i in 0 until answers.length()) {
                    val item = answers.getJSONObject(i)
                    if (item.optInt("type") == 1) {
                        val ip = item.optString("data")
                        if (ip.isNotBlank()) {
                            result.add(InetAddress.getByName(ip))
                        }
                    }
                }
                result
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun testLookup(host: String, provider: DohProvider): DohLookupResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        if (provider == DohProvider.SYSTEM) {
            return@withContext try {
                val addrs = Dns.SYSTEM.lookup(host).mapNotNull { it.hostAddress }
                DohLookupResult(host, provider.displayName, addrs, System.currentTimeMillis() - start)
            } catch (e: Exception) {
                DohLookupResult(host, provider.displayName, emptyList(), System.currentTimeMillis() - start, e.localizedMessage)
            }
        }
        return@withContext try {
            val addrs = resolveSync(host, provider).mapNotNull { it.hostAddress }
            val elapsed = System.currentTimeMillis() - start
            if (addrs.isEmpty()) {
                DohLookupResult(host, provider.displayName, emptyList(), elapsed, "No DNS A records returned.")
            } else {
                DohLookupResult(host, provider.displayName, addrs, elapsed)
            }
        } catch (e: Exception) {
            DohLookupResult(host, provider.displayName, emptyList(), System.currentTimeMillis() - start, e.localizedMessage)
        }
    }
}
