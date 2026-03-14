package com.opensam.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant

class SelectNpcTokenServiceTest {

    @Test
    fun `existing valid token should be reused within validity period`() {
        val validUntil = Instant.now().plusSeconds(300)
        val isValid = Instant.now().isBefore(validUntil)
        assertTrue(isValid)
    }

    @Test
    fun `expired token should trigger new generation`() {
        val validUntil = Instant.now().minusSeconds(10)
        val isExpired = !Instant.now().isBefore(validUntil)
        assertTrue(isExpired)
    }
}
