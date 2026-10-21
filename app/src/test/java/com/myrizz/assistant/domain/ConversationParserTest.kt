package com.myrizz.assistant.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationParserTest {
    private val width = 1000

    private fun them(text: String, top: Int, h: Int = 30) = OcrLine(text, 50, 400, top, top + h)
    private fun me(text: String, top: Int, h: Int = 30) = OcrLine(text, 600, 950, top, top + h)

    @Test fun separatesSpeakersByScreenSideAndKeepsOrder() {
        val turns = ConversationParser.parse(
            listOf(me("Then i picked the perfect day", 400), them("lol smooth", 100), them("What r ur intentions??", 500)),
            width,
        )
        assertEquals(listOf(Speaker.THEM, Speaker.ME, Speaker.THEM), turns.map { it.speaker })
        assertEquals("lol smooth", turns[0].text)
    }

    @Test fun dropsStatusNoise() {
        val turns = ConversationParser.parse(
            listOf(them("11:25", 10), them("Active 13m ago", 50), them("You replied to their story", 90), me("hello", 200), me("Seen", 260)),
            width,
        )
        assertEquals(1, turns.size)
        assertEquals("hello", turns[0].text)
    }

    @Test fun mergesWrappedLinesOfOneBubble() {
        val turns = ConversationParser.parse(listOf(me("Then i picked the perfect day to", 100), me("slide in", 135)), width)
        assertEquals(1, turns.size)
        assertEquals("Then i picked the perfect day to slide in", turns[0].text)
    }

    @Test fun keepsSeparateBubblesApart() {
        val turns = ConversationParser.parse(listOf(them("lol smooth", 100), them("What if it is ???", 200)), width)
        assertEquals(2, turns.size)
    }

    @Test fun emptyOrInvalidInput() {
        assertTrue(ConversationParser.parse(emptyList(), width).isEmpty())
        assertTrue(ConversationParser.parse(listOf(them("hi", 0)), 0).isEmpty())
    }

    @Test fun formatAndSwapSides() {
        val text = ConversationParser.format(listOf(Turn(Speaker.THEM, "hi"), Turn(Speaker.ME, "hey")))
        assertEquals("Them: hi\nMe: hey", text)
        assertEquals("Me: hi\nThem: hey", ConversationParser.swapSides(text))
    }

    @Test fun noiseDetection() {
        assertTrue(ConversationParser.isNoise("11:25"))
        assertTrue(ConversationParser.isNoise("Active 13m ago"))
        assertFalse(ConversationParser.isNoise("hello there"))
    }
}
