package com.forgebuild.forgehouse50

import com.forgebuild.forgehouse50.data.ApiErrorMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ApiErrorMappingTest {

    @Test
    fun `401 on login shows invalid credentials message instead of session expired`() {
        val msg = ApiErrorMapper.mapHttpError("/auth/login", 401, "Invalid email or password")
        assertEquals("Invalid email or password", msg)
        assertFalse(msg.contains("Session expired", ignoreCase = true))

        val adminMsg = ApiErrorMapper.mapHttpError("/admin/login", 401, "Invalid username or password")
        assertEquals("Invalid username or password", adminMsg)
        assertFalse(adminMsg.contains("Session expired", ignoreCase = true))

        val fallbackLogin = ApiErrorMapper.mapHttpError("/auth/login", 401, null)
        assertEquals("Invalid email or password.", fallbackLogin)
    }

    @Test
    fun `401 on authenticated endpoints shows session expired`() {
        assertEquals(
            "Session expired. Please sign in again.",
            ApiErrorMapper.mapHttpError("/auth/me", 401, "Not authenticated")
        )
        assertEquals(
            "Session expired. Please sign in again.",
            ApiErrorMapper.mapHttpError("/today", 401, "Session expired. Please sign in again.")
        )
    }

    @Test
    fun `5xx server faults map to honest server problem message`() {
        for (code in listOf(500, 502, 503, 504)) {
            val msg = ApiErrorMapper.mapHttpError("/auth/login", code, "D1 internal error")
            assertEquals("Server problem, please try again", msg)
            assertFalse(msg.contains("Session expired", ignoreCase = true))
        }
    }

    @Test
    fun `429 and 403 preserve backend guidance`() {
        assertEquals(
            "Please wait 25s before requesting another reset code.",
            ApiErrorMapper.mapHttpError("/auth/forgot_password", 429, "Please wait 25s before requesting another reset code.")
        )
        assertEquals(
            "Email not verified",
            ApiErrorMapper.mapHttpError("/auth/login", 403, "Email not verified")
        )
    }
}
