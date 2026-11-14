package com.example.adblock

data class ParsedFilterRules(
    val blockedDomains: Set<String>,
    val exceptionDomains: Set<String>,
    val patternRules: List<String>,
    val globalCosmeticSelectors: List<String>,
    val domainCosmeticSelectors: Map<String, List<String>>,
    val domainCosmeticExceptions: Map<String, Set<String>>,
    val totalRuleCount: Int
)

object AdBlockFilterParser {

    fun parse(lines: Sequence<String>, maxRules: Int = 85_000): ParsedFilterRules {
        val blockedDomains = HashSet<String>(16384)
        val exceptionDomains = HashSet<String>(1024)
        val patternRules = ArrayList<String>(2048)
        val globalCosmetic = LinkedHashSet<String>(512)
        val domainCosmetic = HashMap<String, MutableList<String>>(512)
        val domainCosmeticExceptions = HashMap<String, MutableSet<String>>(128)
        var count = 0

        for (rawLine in lines) {
            if (count >= maxRules) break
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("!") || line.startsWith("[Adblock")) continue

            // Cosmetic element hiding: ##selector or domain1,domain2##selector
            val hideIdx = line.indexOf("##")
            if (hideIdx >= 0) {
                val selector = line.substring(hideIdx + 2).trim()
                if (selector.isNotEmpty() && !selector.startsWith("+js(") && !selector.startsWith("-abp-")) {
                    val domainsPart = line.substring(0, hideIdx).trim()
                    if (domainsPart.isEmpty()) {
                        if (globalCosmetic.size < 600) {
                            globalCosmetic.add(selector)
                            count++
                        }
                    } else {
                        for (d in domainsPart.split(',')) {
                            val clean = d.trim().lowercase()
                            if (clean.startsWith("~")) {
                                val target = clean.removePrefix("~")
                                if (target.isNotEmpty()) {
                                    domainCosmeticExceptions.getOrPut(target) { HashSet() }.add(selector)
                                }
                            } else if (clean.isNotEmpty()) {
                                val list = domainCosmetic.getOrPut(clean) { ArrayList() }
                                if (list.size < 40) {
                                    list.add(selector)
                                    count++
                                }
                            }
                        }
                    }
                }
                continue
            }

            // Cosmetic exception: domain#@#selector
            val unhideIdx = line.indexOf("#@#")
            if (unhideIdx > 0) {
                val domainsPart = line.substring(0, unhideIdx).trim()
                val selector = line.substring(unhideIdx + 3).trim()
                for (d in domainsPart.split(',')) {
                    val clean = d.trim().lowercase()
                    if (clean.isNotEmpty()) {
                        domainCosmeticExceptions.getOrPut(clean) { HashSet() }.add(selector)
                    }
                }
                continue
            }

            // Strip $options for fast network matching while preserving simple domain anchors
            val isException = line.startsWith("@@")
            val body = (if (isException) line.substring(2) else line)
            val optionSplit = body.indexOf('$')
            val rulePattern = (if (optionSplit >= 0) body.substring(0, optionSplit) else body).trim()
            val options = if (optionSplit >= 0) body.substring(optionSplit + 1).lowercase() else ""

            if (rulePattern.isEmpty() || options.contains("popup") && rulePattern.length < 4) continue

            // Pure domain anchor rule: ||example.com^ or ||sub.example.com^
            if (rulePattern.startsWith("||")) {
                val after = rulePattern.substring(2)
                val endIdx = after.indexOfAny(charArrayOf('^', '/', '*'))
                val hostPart = (if (endIdx >= 0) after.substring(0, endIdx) else after).lowercase()
                val isOnlyHost = endIdx < 0 || after[endIdx] == '^' && endIdx == after.length - 1
                if (isOnlyHost && hostPart.contains('.') && !hostPart.contains('*')) {
                    if (isException) exceptionDomains.add(hostPart)
                    else blockedDomains.add(hostPart)
                    count++
                    continue
                }
            }

            // Substring/path rules (only clean, non-regex literals >= 6 chars)
            if (!isException && !rulePattern.startsWith("/") && !rulePattern.endsWith("/")) {
                val cleaned = rulePattern
                    .removePrefix("||")
                    .removePrefix("|")
                    .trim('*')
                    .replace("^", "/")
                    .lowercase()
                if (cleaned.length >= 6 && !cleaned.contains('*') && patternRules.size < 1500) {
                    patternRules.add(cleaned)
                    count++
                }
            } else if (!isException && rulePattern.startsWith("/") && !rulePattern.endsWith("/")) {
                val cleaned = rulePattern.trim('*').replace("^", "").lowercase()
                if (cleaned.length >= 6 && !cleaned.contains('*') && patternRules.size < 1500) {
                    patternRules.add(cleaned)
                    count++
                }
            }
        }

        return ParsedFilterRules(
            blockedDomains = blockedDomains,
            exceptionDomains = exceptionDomains,
            patternRules = patternRules,
            globalCosmeticSelectors = globalCosmetic.toList(),
            domainCosmeticSelectors = domainCosmetic,
            domainCosmeticExceptions = domainCosmeticExceptions,
            totalRuleCount = count
        )
    }
}
