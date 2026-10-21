package com.myrizz.assistant.data

import com.myrizz.assistant.domain.ReplyLanguage
import com.myrizz.assistant.domain.ReplyStyle
import com.myrizz.assistant.domain.UiLanguage
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val AppJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    isLenient = true
}

// ---- Wire format (must match backend/src/app.js) ----------------------------------------------

@Serializable
data class RepliesRequest(
    val conversation: String,
    val style: String,
    val replyLanguage: String,
    val customInstruction: String = "",
    val previousReplies: List<String> = emptyList(),
)

@Serializable
data class RepliesResponse(
    val replies: List<String> = emptyList(),
    val detectedLanguage: String? = null,
    val tone: String? = null,
)

@Serializable
data class ToolRequest(
    val tool: String,
    val text: String,
    val replyLanguage: String,
    val customInstruction: String = "",
    val style: String? = null,
)

@Serializable
data class ToolResponse(val items: List<String> = emptyList())

@Serializable
data class TranscribeRequest(val imageBase64: String, val mimeType: String = "image/jpeg")

@Serializable
data class TranscribedTurn(val speaker: String, val text: String)

@Serializable
data class TranscribeResponse(val turns: List<TranscribedTurn> = emptyList())

@Serializable
data class HealthResponse(
    val ok: Boolean = false,
    val aiConfigured: Boolean = false,
    val provider: String? = null,
    val authRequired: Boolean = false,
)

@Serializable
data class ErrorBody(val error: String = "", val message: String = "")

// ---- Local persisted models --------------------------------------------------------------------

@Serializable
data class Settings(
    val backendUrl: String = "",
    val accessToken: String = "",
    val consentGranted: Boolean = false,
    val replyLanguage: ReplyLanguage = ReplyLanguage.AUTO,
    val uiLanguage: UiLanguage = UiLanguage.SYSTEM,
    val defaultStyle: ReplyStyle = ReplyStyle.SMOOTH,
    val defaultCustomInstruction: String = "",
    val historyEnabled: Boolean = false,
    val bubbleEnabled: Boolean = false,
    val bubbleX: Int = -1,
    val bubbleY: Int = -1,
    val onboardingDone: Boolean = false,
) {
    val isBackendConfigured: Boolean get() = backendUrl.isNotBlank()
}

@Serializable
data class Favorite(val id: String, val text: String, val createdAt: Long)

@Serializable
data class HistoryEntry(
    val id: String,
    val createdAt: Long,
    /** "reply" or a tool apiId such as "improve" */
    val kind: String,
    val style: String? = null,
    val input: String,
    val outputs: List<String>,
)
