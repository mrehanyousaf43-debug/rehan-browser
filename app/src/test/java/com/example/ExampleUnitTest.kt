package com.example

import com.example.adblock.AdBlockFilterParser
import com.example.data.local.WorkspaceType
import com.example.ui.localization.AppLanguage
import com.example.ui.localization.stringsFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun easyListParser_parsesDomainsExceptionsPatternsAndCosmeticRules() {
        val sampleRules = sequenceOf(
            "! Comment line",
            "||doubleclick.net^",
            "@@||safe.doubleclick.net^",
            "/adserver/*",
            "##.ad-banner",
            "example.com##.sponsored-box",
            "example.com#@#.ad-banner"
        )
        val parsed = AdBlockFilterParser.parse(sampleRules)
        assertTrue(parsed.blockedDomains.contains("doubleclick.net"))
        assertTrue(parsed.exceptionDomains.contains("safe.doubleclick.net"))
        assertTrue(parsed.patternRules.contains("/adserver/"))
        assertTrue(parsed.globalCosmeticSelectors.contains(".ad-banner"))
        assertTrue(parsed.domainCosmeticSelectors["example.com"]?.contains(".sponsored-box") == true)
        assertTrue(parsed.domainCosmeticExceptions["example.com"]?.contains(".ad-banner") == true)
    }

    @Test
    fun workspacesAndLanguages_areConfiguredProperly() {
        assertEquals(3, WorkspaceType.entries.size)
        assertTrue(AppLanguage.URDU.isRtl)
        assertFalse(AppLanguage.HINDI.isRtl)
        assertFalse(AppLanguage.ENGLISH.isRtl)
        assertEquals("Made by Mohammad Rehan Yousuf", stringsFor(AppLanguage.ENGLISH).madeBy)
        assertEquals("Made by Mohammad Rehan Yousuf", stringsFor(AppLanguage.URDU).madeBy)
        assertEquals("Made by Mohammad Rehan Yousuf", stringsFor(AppLanguage.HINDI).madeBy)
    }
}
