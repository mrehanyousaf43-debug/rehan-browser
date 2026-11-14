package com.example.ai

/**
 * Single centralized configuration for Gemini AI in Rehan Browser.
 *
 * Authentication uses Firebase AI Logic + App Check attestation
 * (provisioned via google-services.json and AppCheckConfig) so no
 * client-side API key is exposed in source code or APK resources.
 */
object AiConfig {
    const val PROVIDER_NAME = "Gemini API (Firebase AI Logic)"
    const val DEFAULT_MODEL = "gemini-3.8-flash"
    const val FAST_MODEL = "gemini-3.5-flash-lite"
    const val REQUEST_TIMEOUT_MS = 60_000L
    const val MAX_PAGE_CONTEXT_CHARS = 18_000

    const val BROWSER_ASSISTANT_INSTRUCTION =
        "You are the built-in AI browser assistant inside Rehan Browser (made by Mohammad Rehan Yousuf). " +
            "Be concise, accurate, structured, and helpful. When answering questions about a web page, " +
            "ground your answers in the provided page content and clearly state if something is not mentioned on the page."
}
