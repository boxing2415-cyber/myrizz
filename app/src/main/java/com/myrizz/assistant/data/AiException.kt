package com.myrizz.assistant.data

/** Every way a backend call can fail, mapped to a specific, user-readable state. */
sealed class AiException(message: String? = null) : Exception(message) {
    /** No backend URL saved (or it is not valid HTTPS). Nothing was sent. */
    class NotConfigured : AiException()
    class Offline : AiException()
    class Timeout : AiException()
    class RateLimited(val retryAfterSec: Int?) : AiException()
    class Unauthorized : AiException()

    /** Backend is reachable but has no AI provider key configured. */
    class ServerNotConfigured : AiException()
    class InputTooLong : AiException()
    class NoTextFound : AiException()
    class BadResponse(detail: String? = null) : AiException(detail)
    class Http(val code: Int) : AiException("HTTP $code")
}
