package com.myrizz.assistant.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitiveContentGuardTest {

    @Test fun normalChatIsAllowed() {
        assertFalse(SensitiveContentGuard.isSensitive("Hey, are we still on for dinner at 8?"))
        assertFalse(SensitiveContentGuard.isSensitive("see you at 7:30 ok"))
        assertFalse(SensitiveContentGuard.isSensitive("Γεια σου! Τι κάνεις σήμερα;"))
        assertFalse(SensitiveContentGuard.isSensitive("call me on 6912345678"))
        assertFalse(SensitiveContentGuard.isSensitive("I need a pin for my hair"))
    }

    @Test fun blankIsAllowed() {
        assertFalse(SensitiveContentGuard.isSensitive(""))
        assertFalse(SensitiveContentGuard.isSensitive("   \n "))
    }

    @Test fun passwordsAreBlocked() {
        assertTrue(SensitiveContentGuard.isSensitive("my password is hunter2"))
        assertTrue(SensitiveContentGuard.isSensitive("PIN: 4821"))
        assertTrue(SensitiveContentGuard.isSensitive("ο κωδικός μου είναι kalimera99"))
    }

    @Test fun oneTimeCodesAreBlocked() {
        assertTrue(SensitiveContentGuard.isSensitive("your verification code 482913"))
        assertTrue(SensitiveContentGuard.isSensitive("otp = 123456"))
    }

    @Test fun paymentCardsAreBlocked() {
        assertTrue(SensitiveContentGuard.isSensitive("card 4111 1111 1111 1111 exp 12/29"))
        assertTrue(SensitiveContentGuard.isSensitive("4111-1111-1111-1111"))
    }

    @Test fun longDigitStringThatFailsLuhnIsAllowed() {
        assertFalse(SensitiveContentGuard.isSensitive("order number 4111111111111112"))
    }

    @Test fun ibanIsBlocked() {
        assertTrue(SensitiveContentGuard.isSensitive("send it to GR1601101250000000012300695"))
    }

    @Test fun keysAreBlocked() {
        assertTrue(SensitiveContentGuard.isSensitive("-----BEGIN RSA PRIVATE KEY-----"))
        assertTrue(SensitiveContentGuard.isSensitive("key sk-abcdefghijklmnopqrstuvwxyz123456"))
    }

    @Test fun luhnChecksum() {
        assertTrue(SensitiveContentGuard.luhn("4111111111111111"))
        assertFalse(SensitiveContentGuard.luhn("4111111111111112"))
        assertFalse(SensitiveContentGuard.luhn("1234"))
    }
}
