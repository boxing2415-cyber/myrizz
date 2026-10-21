package com.myrizz.assistant.data

import com.myrizz.assistant.domain.UrlValidator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.KSerializer
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Talks to the MyRizz backend proxy. The AI provider key never exists on the device.
 *
 * @param settings   supplies the current backend URL / token on every call
 * @param isOnline   quick connectivity check so we fail fast when offline
 * @param normalizeUrl URL policy (HTTPS only in production; tests may relax it)
 */
class AiClient(
    private val settings: () -> Settings,
    private val isOnline: () -> Boolean,
    private val http: OkHttpClient = defaultHttp(),
    private val normalizeUrl: (String) -> String? = UrlValidator::normalizeHttps,
    private val retryDelayMs: Long = 700,
) {

    suspend fun replies(req: RepliesRequest): RepliesResponse =
        post("/v1/replies", req, RepliesRequest.serializer(), RepliesResponse.serializer())

    suspend fun tool(req: ToolRequest): ToolResponse =
        post("/v1/tools", req, ToolRequest.serializer(), ToolResponse.serializer())

    suspend fun transcribe(req: TranscribeRequest): TranscribeResponse =
        post("/v1/transcribe", req, TranscribeRequest.serializer(), TranscribeResponse.serializer())

    /** Used by Settings -> "Test connection". Does not require consent: it sends no user content. */
    suspend fun health(): HealthResponse {
        val base = baseUrl()
        val request = Request.Builder().url("$base/health").get().header("User-Agent", UA).build()
        return execute(request, maxRetries = 1) { body ->
            AppJson.decodeFromString(HealthResponse.serializer(), body)
        }
    }

    private fun baseUrl(): String {
        val s = settings()
        return normalizeUrl(s.backendUrl) ?: throw AiException.NotConfigured()
    }

    private suspend fun <Q, A> post(
        path: String,
        body: Q,
        qs: KSerializer<Q>,
        rs: KSerializer<A>,
    ): A {
        val base = baseUrl()
        if (!isOnline()) throw AiException.Offline()
        val json = AppJson.encodeToString(qs, body)
        val builder = Request.Builder()
            .url(base + path)
            .header("User-Agent", UA)
            .post(json.toRequestBody(JSON))
        val token = settings().accessToken.trim()
        if (token.isNotEmpty()) builder.header("Authorization", "Bearer $token")
        return execute(builder.build(), maxRetries = 2) { text ->
            AppJson.decodeFromString(rs, text)
        }
    }

    private suspend fun <T> execute(request: Request, maxRetries: Int, parse: (String) -> T): T {
        var attempt = 0
        while (true) {
            try {
                http.newCall(request).await().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (resp.isSuccessful) {
                        return try {
                            parse(text)
                        } catch (e: Exception) {
                            throw AiException.BadResponse(e.message)
                        }
                    }
                    val err = mapError(resp.code, text, resp.header("Retry-After")?.toIntOrNull())
                    if (attempt < maxRetries && isRetryable(resp.code, err)) {
                        val wait = (err as? AiException.RateLimited)?.retryAfterSec?.coerceIn(1, 4)?.times(1000L)
                            ?: (retryDelayMs * (attempt + 1))
                        attempt++
                        delay(wait)
                        return@use
                    }
                    throw err
                }
                continue
            } catch (e: AiException) {
                throw e
            } catch (e: CancellationException) {
                throw e
            } catch (_: SocketTimeoutException) {
                throw AiException.Timeout()
            } catch (e: IOException) {
                if (e.message?.contains("timeout", ignoreCase = true) == true) throw AiException.Timeout()
                if (attempt < maxRetries) {
                    attempt++
                    delay(retryDelayMs * attempt)
                    continue
                }
                throw AiException.Offline()
            }
        }
    }

    private fun isRetryable(code: Int, err: AiException): Boolean =
        err !is AiException.ServerNotConfigured && (code == 429 || code == 502 || code == 503 || code == 504)

    internal fun mapError(code: Int, body: String, retryAfter: Int?): AiException {
        val parsed = runCatching { AppJson.decodeFromString(ErrorBody.serializer(), body) }.getOrNull()
        return when {
            parsed?.error == "ai_not_configured" -> AiException.ServerNotConfigured()
            parsed?.error == "no_text_found" -> AiException.NoTextFound()
            code == 401 || code == 403 -> AiException.Unauthorized()
            code == 413 -> AiException.InputTooLong()
            code == 429 -> AiException.RateLimited(retryAfter)
            code == 504 || parsed?.error == "ai_timeout" -> AiException.Timeout()
            parsed?.error == "bad_ai_response" -> AiException.BadResponse(parsed.message)
            else -> AiException.Http(code)
        }
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private const val UA = "MyRizzAI-Android/1.0"

        fun defaultHttp(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(50, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(70, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { runCatching { cancel() } }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (cont.isActive) cont.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            if (cont.isActive) cont.resume(response) else response.close()
        }
    })
}
