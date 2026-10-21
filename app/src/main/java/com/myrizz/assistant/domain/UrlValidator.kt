package com.myrizz.assistant.domain

import java.net.URI

object UrlValidator {
    /**
     * Returns a normalised base URL (no trailing slash, no query) or null when the input is not a
     * valid https:// address. Plain http:// is rejected: the app only talks to the backend over TLS.
     */
    fun normalizeHttps(input: String): String? {
        val raw = input.trim().trimEnd('/')
        if (raw.isEmpty()) return null
        val uri = try {
            URI(raw)
        } catch (_: Exception) {
            return null
        }
        if (!"https".equals(uri.scheme, ignoreCase = true)) return null
        val host = uri.host ?: return null
        if (host.isBlank() || !host.contains('.') && host != "localhost") return null
        if (uri.query != null || uri.fragment != null || uri.userInfo != null) return null
        val port = if (uri.port == -1) "" else ":${uri.port}"
        val path = uri.rawPath.orEmpty().trimEnd('/')
        return "https://${host.lowercase()}$port$path"
    }
}
