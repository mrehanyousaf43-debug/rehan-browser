package com.example.ui.localization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

enum class AppLanguage(
    val code: String,
    val displayName: String,
    val nativeName: String,
    val isRtl: Boolean
) {
    ENGLISH("en", "English", "English", false),
    URDU("ur", "Urdu", "اردو", true),
    HINDI("hi", "Hindi", "हिन्दी", false);

    companion object {
        fun fromCode(code: String): AppLanguage =
            entries.firstOrNull { it.code == code } ?: ENGLISH
    }
}

data class BrowserStrings(
    val appName: String,
    val madeBy: String,
    val searchOrUrlHint: String,
    val newTab: String,
    val incognitoTab: String,
    val bookmarks: String,
    val history: String,
    val downloads: String,
    val findInPage: String,
    val share: String,
    val fullPageScreenshot: String,
    val workspaces: String,
    val research: String,
    val editing: String,
    val entertainment: String,
    val adBlocker: String,
    val vpnTitle: String,
    val aiAssistant: String,
    val smartReader: String,
    val voiceCommand: String,
    val writingHelper: String,
    val privacySettings: String,
    val aboutApp: String,
    val tabs: String,
    val aiOrganizeTabs: String,
    val darkTheme: String,
    val languageLabel: String,
    val clearBrowsingData: String,
    val speedDial: String,
    val blockedOnPage: String,
    val siteShieldOn: String,
    val vpnConnected: String,
    val vpnDisconnected: String,
    val summarize: String,
    val translate: String,
    val explainSimply: String,
    val askAboutPage: String
)

private val EnglishStrings = BrowserStrings(
    appName = "Rehan Browser",
    madeBy = "Made by Mohammad Rehan Yousuf",
    searchOrUrlHint = "Search or enter web address",
    newTab = "New Tab",
    incognitoTab = "Incognito Tab",
    bookmarks = "Bookmarks",
    history = "History",
    downloads = "Downloads",
    findInPage = "Find in page",
    share = "Share",
    fullPageScreenshot = "Full-Page Screenshot",
    workspaces = "Workspaces",
    research = "Research",
    editing = "Editing",
    entertainment = "Entertainment",
    adBlocker = "Ad & Tracker Shield",
    vpnTitle = "WireGuard VPN",
    aiAssistant = "Gemini AI Panel",
    smartReader = "Smart Reader + TTS",
    voiceCommand = "Voice Command",
    writingHelper = "AI Writing Helper",
    privacySettings = "Privacy & DoH",
    aboutApp = "About Rehan Browser",
    tabs = "Tabs",
    aiOrganizeTabs = "AI Organize",
    darkTheme = "Dark Theme",
    languageLabel = "Language / RTL",
    clearBrowsingData = "Clear Browsing Data",
    speedDial = "Speed Dial",
    blockedOnPage = "Blocked on this page",
    siteShieldOn = "Shield active for this site",
    vpnConnected = "Connected",
    vpnDisconnected = "Disconnected",
    summarize = "Summarize",
    translate = "Translate",
    explainSimply = "Explain Simply",
    askAboutPage = "Ask a question about this page..."
)

private val UrduStrings = BrowserStrings(
    appName = "ریحان براؤزر",
    madeBy = "Made by Mohammad Rehan Yousuf",
    searchOrUrlHint = "تلاش کریں یا ویب پتہ درج کریں",
    newTab = "نیا ٹیب",
    incognitoTab = "خفیہ ٹیب",
    bookmarks = "بک مارکس",
    history = "تاریخچہ",
    downloads = "ڈاؤن لوڈز",
    findInPage = "صفحے میں تلاش",
    share = "شیئر کریں",
    fullPageScreenshot = "مکمل صفحے کا اسکرین شاٹ",
    workspaces = "ورک اسپیسز",
    research = "تحقیق",
    editing = "ایڈیٹنگ",
    entertainment = "تفریح",
    adBlocker = "ایڈ اور ٹریکر شیلڈ",
    vpnTitle = "وائر گارڈ وی پی این",
    aiAssistant = "جیمنی اے آئی پینل",
    smartReader = "اسمارٹ ریڈر + آواز",
    voiceCommand = "صوتی کمانڈ",
    writingHelper = "اے آئی تحریری مددگار",
    privacySettings = "رازداری اور ڈی او ایچ",
    aboutApp = "ریحان براؤزر کے بارے میں",
    tabs = "ٹیبز",
    aiOrganizeTabs = "اے آئی ترتیب",
    darkTheme = "ڈارک تھیم",
    languageLabel = "زبان / RTL",
    clearBrowsingData = "براؤزنگ ڈیٹا صاف کریں",
    speedDial = "اسپیڈ ڈائل",
    blockedOnPage = "اس صفحے پر بلاک شدہ",
    siteShieldOn = "اس سائٹ کے لیے شیلڈ فعال ہے",
    vpnConnected = "منسلک",
    vpnDisconnected = "منقطع",
    summarize = "خلاصہ",
    translate = "ترجمہ",
    explainSimply = "سادہ وضاحت",
    askAboutPage = "اس صفحے کے بارے میں سوال پوچھیں..."
)

private val HindiStrings = BrowserStrings(
    appName = "रेहान ब्राउज़र",
    madeBy = "Made by Mohammad Rehan Yousuf",
    searchOrUrlHint = "खोजें या वेब पता दर्ज करें",
    newTab = "नया टैब",
    incognitoTab = "गुप्त टैब",
    bookmarks = "बुकमार्क",
    history = "इतिहास",
    downloads = "डाउनलोड",
    findInPage = "पेज में खोजें",
    share = "शेयर करें",
    fullPageScreenshot = "पूरे पेज का स्क्रीनशॉट",
    workspaces = "वर्कस्पेस",
    research = "रिसर्च",
    editing = "एडिटिंग",
    entertainment = "मनोरंजन",
    adBlocker = "विज्ञापन और ट्रैकर शील्ड",
    vpnTitle = "वायरगार्ड वीपीएन",
    aiAssistant = "जेमिनी एआई पैनल",
    smartReader = "स्मार्ट रीडर + टीटीएस",
    voiceCommand = "वॉइस कमांड",
    writingHelper = "एआई लेखन सहायक",
    privacySettings = "गोपनीयता और DoH",
    aboutApp = "रेहान ब्राउज़र के बारे में",
    tabs = "टैब",
    aiOrganizeTabs = "एआई व्यवस्थित",
    darkTheme = "डार्क थीम",
    languageLabel = "भाषा / RTL",
    clearBrowsingData = "ब्राउज़िंग डेटा साफ़ करें",
    speedDial = "स्पीड डायल",
    blockedOnPage = "इस पेज पर ब्लॉक किया गया",
    siteShieldOn = "इस साइट के लिए शील्ड सक्रिय",
    vpnConnected = "कनेक्टेड",
    vpnDisconnected = "डिस्कनेक्टेड",
    summarize = "सारांश",
    translate = "अनुवाद",
    explainSimply = "सरल व्याख्या",
    askAboutPage = "इस पेज के बारे में प्रश्न पूछें..."
)

fun stringsFor(language: AppLanguage): BrowserStrings = when (language) {
    AppLanguage.ENGLISH -> EnglishStrings
    AppLanguage.URDU -> UrduStrings
    AppLanguage.HINDI -> HindiStrings
}

val LocalBrowserStrings = staticCompositionLocalOf { EnglishStrings }

@Composable
fun ProvideBrowserStrings(
    language: AppLanguage,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalBrowserStrings provides stringsFor(language)) {
        content()
    }
}
