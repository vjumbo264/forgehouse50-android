package com.forgebuild.forgehouse50

import com.forgebuild.forgehouse50.ui.AuthResetLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthResetLogicTest {
    @Test
    fun `isValidEmail validates basic email format`() {
        assertTrue(AuthResetLogic.isValidEmail("ada@example.com"))
        assertTrue(AuthResetLogic.isValidEmail("  ada.okafor@church.org  "))
        assertFalse(AuthResetLogic.isValidEmail(""))
        assertFalse(AuthResetLogic.isValidEmail("pastordave"))
        assertFalse(AuthResetLogic.isValidEmail("ada@localhost"))
        assertFalse(AuthResetLogic.isValidEmail("ada @example.com"))
    }

    @Test
    fun `validateResetInput checks 6-digit code, min length 8, and password match`() {
        assertNotNull(AuthResetLogic.validateResetInput("123", "Password123", "Password123"))
        assertNotNull(AuthResetLogic.validateResetInput("12345a", "Password123", "Password123"))
        assertNotNull(AuthResetLogic.validateResetInput("123456", "short", "short"))
        assertNotNull(AuthResetLogic.validateResetInput("123456", "Password123", "Different123"))
        assertNull(AuthResetLogic.validateResetInput("123456", "Password123", "Password123"))
    }

    @Test
    fun `resendRemainingSeconds counts down 30s cooldown cleanly`() {
        val sentAt = 1_000_000L
        assertEquals(30, AuthResetLogic.resendRemainingSeconds(sentAt, sentAt))
        assertEquals(18, AuthResetLogic.resendRemainingSeconds(sentAt, sentAt + 12_000L))
        assertEquals(0, AuthResetLogic.resendRemainingSeconds(sentAt, sentAt + 30_000L))
        assertEquals(0, AuthResetLogic.resendRemainingSeconds(sentAt, sentAt + 90_000L))
        assertEquals(0, AuthResetLogic.resendRemainingSeconds(0L, sentAt))
    }
}
