package com.myrizz.assistant.data

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class AiClientTest {
    private lateinit var server: MockWebServer

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() { server.shutdown() }

    private fun client(online: Boolean = true, token: String = "", configured: Boolean = true) = AiClient(
        settings = { Settings(backendUrl = server.url("/").toString().trimEnd('/'), accessToken = token) },
        isOnline = { online },
        normalizeUrl = { if (configured) it.trimEnd('/') else null },
        retryDelayMs = 1,
    )

    private fun req() = RepliesRequest("Them: hey", "smooth", "auto")

    private inline fun <reified T : Throwable> failure(block: () -> Unit): T {
        try { block() } catch (e: Throwable) { if (e is T) return e else throw e }
        fail("expected ${T::class.simpleName}")
        throw IllegalStateException()
    }

    @Test fun parsesThreeRepliesAndSendsToken() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"replies":["a","b","c"],"detectedLanguage":"el","tone":"playful"}"""))
        val res = client(token = "secret").replies(req())
        assertEquals(listOf("a", "b", "c"), res.replies)
        val sent = server.takeRequest()
        assertEquals("/v1/replies", sent.path)
        assertEquals("Bearer secret", sent.getHeader("Authorization"))
        assertTrue(sent.body.readUtf8().contains("Them: hey"))
    }

    @Test fun noAuthHeaderWithoutToken() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"replies":["a"]}"""))
        client().replies(req())
        assertEquals(null, server.takeRequest().getHeader("Authorization"))
    }

    @Test fun unauthorized() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"unauthorized"}"""))
        failure<AiException.Unauthorized> { runBlocking { client().replies(req()) } }
    }

    @Test fun retriesTransient503ThenSucceeds() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
        server.enqueue(MockResponse().setBody("""{"replies":["ok"]}"""))
        assertEquals(listOf("ok"), client().replies(req()).replies)
        assertEquals(2, server.requestCount)
    }

    @Test fun missingServerKeyIsNotRetried() {
        server.enqueue(MockResponse().setResponseCode(503).setBody("""{"error":"ai_not_configured"}"""))
        failure<AiException.ServerNotConfigured> { runBlocking { client().replies(req()) } }
        assertEquals(1, server.requestCount)
    }

    @Test fun rateLimitGivesUpAfterRetries() {
        repeat(3) { server.enqueue(MockResponse().setResponseCode(429).setBody("{}")) }
        failure<AiException.RateLimited> { runBlocking { client().replies(req()) } }
        assertEquals(3, server.requestCount)
    }

    @Test fun offlineFailsFastWithoutNetworkCall() {
        failure<AiException.Offline> { runBlocking { client(online = false).replies(req()) } }
        assertEquals(0, server.requestCount)
    }

    @Test fun notConfigured() {
        failure<AiException.NotConfigured> { runBlocking { client(configured = false).replies(req()) } }
        assertEquals(0, server.requestCount)
    }

    @Test fun malformedBody() {
        server.enqueue(MockResponse().setBody("[1,2]"))
        failure<AiException.BadResponse> { runBlocking { client().replies(req()) } }
    }

    @Test fun errorMapping() {
        val c = client()
        assertTrue(c.mapError(413, "", null) is AiException.InputTooLong)
        assertTrue(c.mapError(422, """{"error":"no_text_found"}""", null) is AiException.NoTextFound)
        assertTrue(c.mapError(504, "", null) is AiException.Timeout)
        assertTrue(c.mapError(500, "oops", null) is AiException.Http)
        assertEquals(7, (c.mapError(429, "", 7) as AiException.RateLimited).retryAfterSec)
    }

    @Test fun healthDoesNotNeedConsent() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"ok":true,"aiConfigured":false,"authRequired":true}"""))
        val h = client().health()
        assertTrue(h.ok)
        assertEquals(false, h.aiConfigured)
        assertEquals("/health", server.takeRequest().path)
    }
}
