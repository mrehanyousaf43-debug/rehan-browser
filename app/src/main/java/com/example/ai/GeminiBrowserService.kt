package com.example.ai

import android.util.Log
import com.example.data.local.TabEntity
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

data class TabGroupAssignment(
    val groupName: String,
    val colorIndex: Int,
    val tabIds: List<String>
)

data class ParsedVoiceIntent(
    val action: String,
    val queryOrUrl: String,
    val languageResponse: String
)

class GeminiBrowserService {

    companion object {
        private const val TAG = "GeminiBrowserService"
    }

    private val defaultModel by lazy {
        Firebase.ai.generativeModel(
            modelName = AiConfig.DEFAULT_MODEL,
            systemInstruction = content { text(AiConfig.BROWSER_ASSISTANT_INSTRUCTION) }
        )
    }

    private val fastModel by lazy {
        Firebase.ai.generativeModel(
            modelName = AiConfig.FAST_MODEL
        )
    }

    private val tabOrganizerModel by lazy {
        val schema = Schema.obj(
            properties = mapOf(
                "groups" to Schema.array(
                    items = Schema.obj(
                        properties = mapOf(
                            "groupName" to Schema.string(description = "Short 1-2 word category name"),
                            "colorIndex" to Schema.integer(description = "Color index from 0 to 5"),
                            "tabIds" to Schema.array(items = Schema.string())
                        )
                    ),
                    description = "Groups of related browser tab IDs"
                )
            )
        )
        Firebase.ai.generativeModel(
            modelName = AiConfig.FAST_MODEL,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema
            }
        )
    }

    private val voiceIntentModel by lazy {
        val schema = Schema.obj(
            properties = mapOf(
                "action" to Schema.enumeration(
                    values = listOf(
                        "OPEN_URL",
                        "SEARCH",
                        "NEW_TAB",
                        "INCOGNITO_TAB",
                        "CLOSE_TAB",
                        "SUMMARIZE_PAGE",
                        "TRANSLATE_URDU",
                        "TRANSLATE_HINDI",
                        "TRANSLATE_ENGLISH",
                        "READER_MODE",
                        "TOGGLE_VPN",
                        "TOGGLE_ADBLOCK",
                        "BOOKMARK_PAGE",
                        "DARK_THEME",
                        "LIGHT_THEME",
                        "WORKSPACE_RESEARCH",
                        "WORKSPACE_EDITING",
                        "WORKSPACE_ENTERTAINMENT",
                        "GO_BACK",
                        "GO_FORWARD",
                        "REFRESH",
                        "HOME"
                    ),
                    description = "The browser action to execute"
                ),
                "queryOrUrl" to Schema.string(
                    description = "URL, domain, or search terms if action is OPEN_URL or SEARCH; otherwise empty string"
                ),
                "languageResponse" to Schema.string(
                    description = "Short confirmation message in the user's spoken language (Urdu, Hindi, or English)"
                )
            )
        )
        Firebase.ai.generativeModel(
            modelName = AiConfig.FAST_MODEL,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema
            }
        )
    }

    fun streamPageAction(
        prompt: String,
        pageTitle: String,
        pageUrl: String,
        pageText: String
    ): Flow<String> = flow {
        val clipped = pageText.take(AiConfig.MAX_PAGE_CONTEXT_CHARS)
        val fullPrompt = buildString {
            appendLine("Page Title: $pageTitle")
            appendLine("Page URL: $pageUrl")
            appendLine("--- PAGE CONTENT ---")
            appendLine(clipped.ifBlank { "(No extractable body text on this page.)" })
            appendLine("--- USER REQUEST ---")
            appendLine(prompt)
        }
        val acc = StringBuilder()
        try {
            withTimeout(AiConfig.REQUEST_TIMEOUT_MS) {
                defaultModel.generateContentStream(fullPrompt).collect { chunk ->
                    val piece = chunk.text.orEmpty()
                    if (piece.isNotEmpty()) {
                        acc.append(piece)
                        emit(acc.toString())
                    }
                }
            }
            if (acc.isBlank()) {
                throw IllegalStateException("Gemini returned an empty response.")
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI request timed out", e)
            throw IllegalStateException("Request timed out after 60 seconds. Please try again.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI stream failed", e)
            throw e
        }
    }

    fun streamWritingHelper(
        instruction: String,
        existingText: String,
        tone: String,
        targetLanguage: String
    ): Flow<String> = flow {
        val fullPrompt = buildString {
            appendLine("Writing task: $instruction")
            appendLine("Desired tone: $tone")
            appendLine("Language: $targetLanguage")
            if (existingText.isNotBlank()) {
                appendLine("Existing draft / field text:")
                appendLine(existingText)
            }
            appendLine("Return ONLY the ready-to-paste text without meta commentary or markdown fences.")
        }
        val acc = StringBuilder()
        try {
            withTimeout(AiConfig.REQUEST_TIMEOUT_MS) {
                fastModel.generateContentStream(fullPrompt).collect { chunk ->
                    val piece = chunk.text.orEmpty()
                    if (piece.isNotEmpty()) {
                        acc.append(piece)
                        emit(acc.toString())
                    }
                }
            }
            if (acc.isBlank()) {
                throw IllegalStateException("Writing helper returned an empty response.")
            }
        } catch (e: TimeoutCancellationException) {
            throw IllegalStateException("Writing helper timed out. Please try again.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Writing helper failed", e)
            throw e
        }
    }

    suspend fun formatSmartReaderArticle(
        pageTitle: String,
        rawText: String
    ): String {
        val prompt = buildString {
            appendLine("Clean and format the following web article into readable paragraphs for Reader Mode.")
            appendLine("Keep all factual information, headings, and bullet points intact. Remove navigation clutter and boilerplate.")
            appendLine("Title: $pageTitle")
            appendLine("Content:")
            appendLine(rawText.take(AiConfig.MAX_PAGE_CONTEXT_CHARS))
        }
        return try {
            val response = withTimeout(AiConfig.REQUEST_TIMEOUT_MS) {
                fastModel.generateContent(prompt)
            }
            response.text?.takeIf { it.isNotBlank() } ?: rawText
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Smart Reader AI formatting failed, falling back to extracted text", e)
            rawText
        }
    }

    suspend fun organizeTabs(tabs: List<TabEntity>): List<TabGroupAssignment> {
        if (tabs.size < 2) return emptyList()
        val tabListPrompt = tabs.joinToString("\n") { tab ->
            "id=${tab.id} | title=${tab.title} | url=${tab.url}"
        }
        val prompt = "Organize these browser tabs into logical topic groups:\n$tabListPrompt"
        return try {
            val response = withTimeout(AiConfig.REQUEST_TIMEOUT_MS) {
                tabOrganizerModel.generateContent(prompt)
            }
            val jsonStr = response.text ?: throw IllegalStateException("Empty organizer response")
            val json = JSONObject(jsonStr)
            val groupsArray = json.getJSONArray("groups")
            (0 until groupsArray.length()).map { i ->
                val obj = groupsArray.getJSONObject(i)
                val idsArr = obj.getJSONArray("tabIds")
                val ids = (0 until idsArr.length()).map { j -> idsArr.getString(j) }
                TabGroupAssignment(
                    groupName = obj.getString("groupName"),
                    colorIndex = obj.optInt("colorIndex", i % 6).coerceIn(0, 5),
                    tabIds = ids
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Tab organizer failed", e)
            throw e
        }
    }

    suspend fun parseVoiceCommand(transcript: String): ParsedVoiceIntent {
        val prompt = "Interpret this browser voice command (which may be in English, Urdu, or Hindi): \"$transcript\""
        return try {
            val response = withTimeout(AiConfig.REQUEST_TIMEOUT_MS) {
                voiceIntentModel.generateContent(prompt)
            }
            val jsonStr = response.text ?: throw IllegalStateException("Empty voice command response")
            val json = JSONObject(jsonStr)
            ParsedVoiceIntent(
                action = json.getString("action"),
                queryOrUrl = json.optString("queryOrUrl", ""),
                languageResponse = json.optString("languageResponse", "Done.")
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Voice command parsing failed", e)
            throw e
        }
    }
}
