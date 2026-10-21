package com.myrizz.assistant.domain

/**
 * Last line of defence before anything is sent to the AI: blocks text that looks like
 * passwords, payment cards, IBANs, one-time codes, CVVs or private keys.
 * Heuristic by design - it errs on the side of blocking.
 */
object SensitiveContentGuard {

    private const val L = """(?<![\p{L}\p{N}])"""   // not preceded by a letter/digit (Unicode-safe word start)
    private const val R = """(?![\p{L}\p{N}])"""        // not followed by a letter/digit (Unicode-safe word end)

    private val cardCandidate = Regex("""(?<!\d)(?:\d[ -]?){12,18}\d(?!\d)""")
    private val iban = Regex("""\b[A-Za-z]{2}\d{2}(?:[ ]?[A-Za-z0-9]{4}){3,7}(?:[ ]?[A-Za-z0-9]{1,3})?\b""")

    // "password is hunter2", "pin: 4821", "ο κωδικός μου είναι ...", "otp = 123456"
    private val secretWord = Regex(
        L + """(?:password|passcode|passwd|pwd|pin|cvv|cvc|otp|one[- ]time(?: code| password)?|""" +
            """verification code|security code|κωδικ\p{L}{0,2})""" + R +
            """[^\n]{0,24}?(?:(?<![\p{L}\p{N}])(?:is|είναι)(?![\p{L}\p{N}])|[:=])\s*\S+""",
                RegexOption.IGNORE_CASE,
    )
    private val codeNearWord = Regex(
        L + """(?:code|κωδικ\p{L}{0,2}|otp|pin)""" + R + """[^\n\d]{0,20}\d{4,8}(?!\d)""",
                RegexOption.IGNORE_CASE,
    )
    private val privateKey = Regex("""-----BEGIN [A-Z ]*PRIVATE KEY-----""")
    private val apiKey = Regex("""(?<![A-Za-z0-9])(?:sk|pk|rk)-[A-Za-z0-9_\-]{20,}""")

    fun isSensitive(text: String): Boolean {
        if (text.isBlank()) return false
        if (privateKey.containsMatchIn(text) || apiKey.containsMatchIn(text)) return true
        if (secretWord.containsMatchIn(text) || codeNearWord.containsMatchIn(text)) return true
        if (iban.findAll(text).any { looksLikeIban(it.value) }) return true
        return cardCandidate.findAll(text).any { luhn(it.value.filter(Char::isDigit)) }
    }

    private fun looksLikeIban(raw: String): Boolean {
        val s = raw.filterNot { it == ' ' }
        return s.length in 15..34 && s.take(2).all(Char::isLetter) && s.count(Char::isDigit) >= 8
    }

    internal fun luhn(digits: String): Boolean {
        if (digits.length !in 13..19) return false
        var sum = 0
        var alt = false
        for (i in digits.length - 1 downTo 0) {
            var n = digits[i] - '0'
            if (alt) {
                n *= 2
                if (n > 9) n -= 9
            }
            sum += n
            alt = !alt
        }
        return sum % 10 == 0
    }
}
