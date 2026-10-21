package com.myrizz.assistant.domain

/** One recognised line of text with its horizontal position inside the screenshot. */
data class OcrLine(val text: String, val left: Int, val right: Int, val top: Int, val bottom: Int)

enum class Speaker(val label: String) { ME("Me"), THEM("Them") }

data class Turn(val speaker: Speaker, val text: String)

/**
 * Turns OCR lines of a chat screenshot into ordered speaker turns.
 *
 * Chat apps hug the sender's side: the other person's bubbles sit near the LEFT edge and the
 * user's bubbles near the RIGHT edge. We compare each line's left margin with its right margin.
 * When it is ambiguous (long, wide bubbles) we keep the previous speaker.
 */
object ConversationParser {

    private val timeOnly = Regex("""^\d{1,2}[:.]\d{2}(\s?[APap][Mm])?$""")
    private val statusNoise = Regex(
        """(?i)^(seen|delivered|sent|typing\.{0,3}|active( now| \d+\s?[mhd] ago)?|you replied to .*|""" +
            """you sent .*|reply\.{0,3}|message\.{0,3}|today|yesterday|\d{1,3}%|""" +
            """εστάλη|παραδόθηκε|είδε|ενεργός.*|έχετε απαντήσει.*)$"""
    )

    fun isNoise(text: String): Boolean {
        val t = text.trim()
        return t.length < 2 && !t.any { it.isLetterOrDigit() } || timeOnly.matches(t) || statusNoise.matches(t)
    }

    fun parse(lines: List<OcrLine>, imageWidth: Int): List<Turn> {
        if (imageWidth <= 0) return emptyList()
        val sorted = lines.filter { it.text.isNotBlank() && !isNoise(it.text) }.sortedBy { it.top }
        if (sorted.isEmpty()) return emptyList()

        val turns = mutableListOf<Turn>()
        var prevSpeaker = Speaker.THEM
        var prevBottom = Int.MIN_VALUE

        for (line in sorted) {
            val leftMargin = line.left.toFloat() / imageWidth
            val rightMargin = (imageWidth - line.right).toFloat() / imageWidth
            val diff = rightMargin - leftMargin // > 0: hugs the left side -> THEM
            val speaker = when {
                diff > AMBIGUITY -> Speaker.THEM
                diff < -AMBIGUITY -> Speaker.ME
                else -> prevSpeaker
            }
            val height = (line.bottom - line.top).coerceAtLeast(1)
            val gap = line.top - prevBottom
            val sameBubble = turns.isNotEmpty() && speaker == prevSpeaker && gap in -height..(height * 7 / 10)

            if (sameBubble) {
                val last = turns.removeAt(turns.lastIndex)
                turns += last.copy(text = last.text + " " + line.text.trim())
            } else {
                turns += Turn(speaker, line.text.trim())
            }
            prevSpeaker = speaker
            prevBottom = line.bottom
        }
        return turns
    }

    /** "Them: ..." / "Me: ..." transcript, one turn per line. */
    fun format(turns: List<Turn>): String =
        turns.joinToString("\n") { "${it.speaker.label}: ${it.text}" }

    /** Swaps Me/Them labels (used when the sides were detected the wrong way round). */
    fun swapSides(transcript: String): String =
        transcript.lines().joinToString("\n") { l ->
            when {
                l.startsWith("Me:") -> "Them:" + l.removePrefix("Me:")
                l.startsWith("Them:") -> "Me:" + l.removePrefix("Them:")
                else -> l
            }
        }

    private const val AMBIGUITY = 0.04f
}
