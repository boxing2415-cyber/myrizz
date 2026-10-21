package com.myrizz.assistant

import com.myrizz.assistant.data.AiException
import com.myrizz.assistant.data.Favorite
import com.myrizz.assistant.data.RepliesRequest
import com.myrizz.assistant.data.Settings
import com.myrizz.assistant.data.ToolRequest
import com.myrizz.assistant.data.TranscribeRequest
import com.myrizz.assistant.domain.ConversationParser
import com.myrizz.assistant.domain.ReplyLanguage
import com.myrizz.assistant.domain.ReplyStyle
import com.myrizz.assistant.domain.SensitiveContentGuard
import com.myrizz.assistant.domain.ToolType
import com.myrizz.assistant.domain.UrlValidator
import com.myrizz.assistant.ml.ImageIntake
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

const val MAX_INPUT_CHARS = 6000

sealed interface UiError {
    data object EmptyInput : UiError
    data object TooLong : UiError
    data object Sensitive : UiError
    data object NotConfigured : UiError
    data object Offline : UiError
    data object Timeout : UiError
    data object RateLimited : UiError
    data object Unauthorized : UiError
    data object ServerNotConfigured : UiError
    data object BadResponse : UiError
    data object NoTextFound : UiError
    data object OcrFailed : UiError
    data class Http(val code: Int) : UiError
}

data class ReplyItem(val id: Long, val text: String)

data class ShotState(
    val file: File? = null,
    val loading: Boolean = false,
    val text: String = "",
    val usedVision: Boolean = false,
    val lowConfidence: Boolean = false,
)

data class AssistantState(
    val input: String = "",
    val style: ReplyStyle = ReplyStyle.SMOOTH,
    val customInstruction: String = "",
    val replies: List<ReplyItem> = emptyList(),
    val detectedLanguage: String? = null,
    val tool: ToolType = ToolType.IMPROVE,
    val toolInput: String = "",
    val toolItems: List<ReplyItem> = emptyList(),
    val loading: Boolean = false,
    val busyItemId: Long? = null,
    val error: UiError? = null,
    val consentNeeded: Boolean = false,
    val shot: ShotState? = null,
)

/**
 * All assistant behaviour in one place, shared by the main app, the floating panel and the
 * text-selection sheet. AI is only ever called from an explicit user action, and only after:
 *  1. the text passes [SensitiveContentGuard],
 *  2. a valid HTTPS backend is configured,
 *  3. the user has given consent.
 */
class AssistantController(
    private val scope: CoroutineScope,
    private val container: AppContainer,
) {
    private val initial: Settings = container.settings.current
    private val _state = MutableStateFlow(
        AssistantState(style = initial.defaultStyle, customInstruction = initial.defaultCustomInstruction),
    )
    val state: StateFlow<AssistantState> = _state.asStateFlow()

    val settings: StateFlow<Settings> get() = container.settings.state
    val favorites: StateFlow<List<Favorite>> get() = container.favorites.items

    private var job: Job? = null
    private var pending: (() -> Unit)? = null
    private var ids = 0L

    // ---- simple setters ----------------------------------------------------------------------

    fun setInput(text: String) = _state.update { it.copy(input = text) }
    fun setStyle(style: ReplyStyle) = _state.update { it.copy(style = style) }
    fun setCustomInstruction(text: String) = _state.update { it.copy(customInstruction = text.take(300)) }
    fun setTool(tool: ToolType) = _state.update { it.copy(tool = tool, toolItems = emptyList(), error = null) }
    fun setToolInput(text: String) = _state.update { it.copy(toolInput = text) }
    fun clearError() = _state.update { it.copy(error = null) }
    fun setReplyLanguage(lang: ReplyLanguage) = container.settings.update { it.copy(replyLanguage = lang) }

    fun clearConversation() = _state.update {
        it.copy(input = "", replies = emptyList(), error = null, detectedLanguage = null)
    }

    fun editReply(id: Long, text: String) = _state.update { s ->
        s.copy(
            replies = s.replies.map { if (it.id == id) it.copy(text = text) else it },
            toolItems = s.toolItems.map { if (it.id == id) it.copy(text = text) else it },
        )
    }

    fun toggleFavorite(text: String): Boolean = container.favorites.toggle(text)

    // ---- consent -----------------------------------------------------------------------------

    fun acceptConsent() {
        container.settings.update { it.copy(consentGranted = true) }
        _state.update { it.copy(consentNeeded = false) }
        val action = pending
        pending = null
        action?.invoke()
    }

    fun declineConsent() {
        pending = null
        _state.update { it.copy(consentNeeded = false) }
    }

    // ---- AI: replies -------------------------------------------------------------------------

    fun generate(regenerate: Boolean = false) {
        val st = _state.value
        if (!precheck(st.input, required = true) { generate(regenerate) }) return
        job?.cancel()
        _state.update { it.copy(loading = true, error = null) }
        job = scope.launch {
            try {
                val s = container.settings.current
                val res = container.ai.replies(
                    RepliesRequest(
                        conversation = st.input.trim(),
                        style = st.style.apiId,
                        replyLanguage = s.replyLanguage.apiId,
                        customInstruction = st.customInstruction.trim(),
                        previousReplies = if (regenerate) st.replies.map { it.text } else emptyList(),
                    ),
                )
                val items = res.replies.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(3)
                if (items.isEmpty()) throw AiException.BadResponse()
                _state.update {
                    it.copy(
                        loading = false,
                        replies = items.map { t -> ReplyItem(++ids, t) },
                        detectedLanguage = res.detectedLanguage,
                    )
                }
                container.history.record("reply", st.style, st.input, items)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    // ---- AI: tools ---------------------------------------------------------------------------

    fun runTool() {
        val st = _state.value
        if (st.tool == ToolType.REPLY) {
            _state.update { it.copy(input = st.toolInput) }
            generate()
            return
        }
        if (!precheck(st.toolInput, st.tool.textRequired) { runTool() }) return
        job?.cancel()
        _state.update { it.copy(loading = true, error = null, toolItems = emptyList()) }
        job = scope.launch {
            try {
                val items = callTool(st.tool, st.toolInput, st.style, st.customInstruction)
                _state.update { it.copy(loading = false, toolItems = items.map { t -> ReplyItem(++ids, t) }) }
                container.history.record(st.tool.apiId, st.style, st.toolInput, items)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    /** Rewrites one reply card with a tool (default: make it shorter). */
    fun transformReply(id: Long, tool: ToolType = ToolType.SHORTER) {
        val st = _state.value
        val item = st.replies.firstOrNull { it.id == id } ?: st.toolItems.firstOrNull { it.id == id } ?: return
        if (!precheck(item.text, required = true) { transformReply(id, tool) }) return
        job?.cancel()
        _state.update { it.copy(busyItemId = id, error = null) }
        job = scope.launch {
            try {
                val out = callTool(tool, item.text, st.style, st.customInstruction).first()
                _state.update { s ->
                    s.copy(
                        busyItemId = null,
                        replies = s.replies.map { if (it.id == id) it.copy(text = out) else it },
                        toolItems = s.toolItems.map { if (it.id == id) it.copy(text = out) else it },
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    private suspend fun callTool(tool: ToolType, text: String, style: ReplyStyle, custom: String): List<String> {
        val s = container.settings.current
        val res = container.ai.tool(
            ToolRequest(
                tool = tool.apiId,
                text = text.trim(),
                replyLanguage = s.replyLanguage.apiId,
                customInstruction = custom.trim(),
                style = style.apiId,
            ),
        )
        val items = res.items.map { it.trim() }.filter { it.isNotEmpty() }.take(3)
        if (items.isEmpty()) throw AiException.BadResponse()
        return items
    }

    // ---- Screenshots -------------------------------------------------------------------------

    /** Runs on-device OCR on a prepared screenshot copy. Nothing is uploaded here. */
    fun onScreenshotFile(file: File) {
        _state.value.shot?.file?.takeIf { it != file }?.delete()
        _state.update { it.copy(shot = ShotState(file = file, loading = true), error = null) }
        scope.launch {
            try {
                val outcome = withContext(Dispatchers.Default) {
                    val bmp = ImageIntake.decode(file) ?: error("unreadable image")
                    try {
                        container.ocr.read(bmp)
                    } finally {
                        bmp.recycle()
                    }
                }
                _state.update {
                    it.copy(
                        shot = it.shot?.copy(
                            loading = false,
                            text = outcome.transcript,
                            lowConfidence = outcome.letterCount < 12,
                        ),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update {
                    it.copy(shot = it.shot?.copy(loading = false, lowConfidence = true), error = UiError.OcrFailed)
                }
            }
        }
    }

    fun setShotText(text: String) = _state.update { s -> s.copy(shot = s.shot?.copy(text = text)) }

    fun swapShotSides() = _state.update { s ->
        s.copy(shot = s.shot?.copy(text = ConversationParser.swapSides(s.shot.text)))
    }

    /** Optional: uploads the screenshot (after consent) so the AI can transcribe it, e.g. Greek text. */
    fun improveWithVision() {
        val file = _state.value.shot?.file ?: return
        if (!precheck("", required = false) { improveWithVision() }) return
        job?.cancel()
        _state.update { it.copy(shot = it.shot?.copy(loading = true), error = null) }
        job = scope.launch {
            try {
                val b64 = withContext(Dispatchers.IO) { ImageIntake.toBase64(file) }
                val res = container.ai.transcribe(TranscribeRequest(imageBase64 = b64))
                val text = res.turns.joinToString("\n") {
                    (if (it.speaker == "me") "Me" else "Them") + ": " + it.text.trim()
                }
                if (text.isBlank()) throw AiException.NoTextFound()
                _state.update {
                    it.copy(shot = it.shot?.copy(loading = false, text = text, usedVision = true, lowConfidence = false))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    /** Moves the (possibly corrected) screenshot text into the conversation box and generates replies. */
    fun generateFromShot() {
        val text = _state.value.shot?.text.orEmpty()
        if (text.isBlank()) {
            fail(AiException.NoTextFound())
            return
        }
        _state.update { it.copy(input = text) }
        clearShot()
        generate()
    }

    fun clearShot() {
        _state.value.shot?.file?.delete()
        _state.update { it.copy(shot = null) }
    }

    // ---- internals ---------------------------------------------------------------------------

    /**
     * Gate for every AI call. Returns true only when the call may proceed right now.
     * If consent is missing, [retry] is remembered and re-run once the user agrees.
     */
    private fun precheck(text: String, required: Boolean, retry: () -> Unit): Boolean {
        if (required && text.isBlank()) return failWith(UiError.EmptyInput)
        if (text.length > MAX_INPUT_CHARS) return failWith(UiError.TooLong)
        if (SensitiveContentGuard.isSensitive(text)) return failWith(UiError.Sensitive)
        val s = container.settings.current
        if (UrlValidator.normalizeHttps(s.backendUrl) == null) return failWith(UiError.NotConfigured)
        if (!s.consentGranted) {
            pending = retry
            _state.update { it.copy(consentNeeded = true, error = null) }
            return false
        }
        return true
    }

    private fun failWith(error: UiError): Boolean {
        _state.update { it.copy(loading = false, busyItemId = null, error = error, shot = it.shot?.copy(loading = false)) }
        return false
    }

    private fun fail(e: Exception) {
        failWith(e.toUiError())
    }

    /** The chosen image could not be copied or decoded (nothing was sent anywhere). */
    fun reportImageUnreadable() {
        failWith(UiError.OcrFailed)
    }

    /** Stops work and deletes temporary screenshot copies. Call when the owner goes away. */
    fun release() {
        job?.cancel()
        _state.value.shot?.file?.delete()
    }
}

/** Maps any failure to the specific, user-readable [UiError] shown in the UI. */
fun Throwable.toUiError(): UiError = when (this) {
    is AiException.NotConfigured -> UiError.NotConfigured
    is AiException.Offline -> UiError.Offline
    is AiException.Timeout -> UiError.Timeout
    is AiException.RateLimited -> UiError.RateLimited
    is AiException.Unauthorized -> UiError.Unauthorized
    is AiException.ServerNotConfigured -> UiError.ServerNotConfigured
    is AiException.InputTooLong -> UiError.TooLong
    is AiException.NoTextFound -> UiError.NoTextFound
    is AiException.Http -> UiError.Http(code)
    else -> UiError.BadResponse
}
