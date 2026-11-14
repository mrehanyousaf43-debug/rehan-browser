package com.example.adblock

enum class BlockCategory {
    AD,
    TRACKER
}

data class BlockedRequestLog(
    val url: String,
    val host: String,
    val category: BlockCategory,
    val matchedRule: String,
    val timestampMs: Long = System.currentTimeMillis()
)

data class PageBlockStats(
    val adsBlocked: Int = 0,
    val trackersBlocked: Int = 0,
    val recentBlocked: List<BlockedRequestLog> = emptyList()
) {
    val totalBlocked: Int get() = adsBlocked + trackersBlocked
}

data class FilterListStatus(
    val easyListRuleCount: Int = 0,
    val easyPrivacyRuleCount: Int = 0,
    val cosmeticRuleCount: Int = 0,
    val lastUpdatedMs: Long = 0L,
    val isUpdating: Boolean = false,
    val lastError: String? = null
)
