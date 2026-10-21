package com.myrizz.assistant.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UrlValidatorTest {
    @Test fun acceptsAndNormalisesHttps() {
        assertEquals("https://api.example.com", UrlValidator.normalizeHttps("https://api.example.com/"))
        assertEquals("https://example.com:8443/app", UrlValidator.normalizeHttps("  https://Example.COM:8443/app/ "))
        assertEquals("https://localhost", UrlValidator.normalizeHttps("https://localhost"))
    }

    @Test fun rejectsPlainHttp() {
        assertNull(UrlValidator.normalizeHttps("http://api.example.com"))
    }

    @Test fun rejectsGarbage() {
        assertNull(UrlValidator.normalizeHttps(""))
        assertNull(UrlValidator.normalizeHttps("not a url"))
        assertNull(UrlValidator.normalizeHttps("https://nodot"))
        assertNull(UrlValidator.normalizeHttps("ftp://example.com"))
    }

    @Test fun rejectsQueryFragmentAndCredentials() {
        assertNull(UrlValidator.normalizeHttps("https://example.com?a=1"))
        assertNull(UrlValidator.normalizeHttps("https://example.com/#frag"))
        assertNull(UrlValidator.normalizeHttps("https://user:pw@example.com"))
    }
}
