package com.opensam.engine.map

import com.opensam.entity.General
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProximityDetectorTest {

    private val detector = ProximityDetector()

    private fun makeGeneral(id: Long, nationId: Long, posX: Float, posY: Float, crew: Int = 100): General {
        return General().apply {
            this.id = id
            this.nationId = nationId
            this.posX = posX
            this.posY = posY
            this.crew = crew
        }
    }

    @Test
    fun `two enemies within range produce a contact`() {
        val a = makeGeneral(1L, 1L, 10f, 10f)
        val b = makeGeneral(2L, 2L, 11f, 10f)
        val contacts = detector.findContacts(listOf(a, b)) { _, _ -> true }
        assertEquals(1, contacts.size)
    }

    @Test
    fun `same nation generals do not produce a contact`() {
        val a = makeGeneral(1L, 1L, 10f, 10f)
        val b = makeGeneral(2L, 1L, 11f, 10f)
        val contacts = detector.findContacts(listOf(a, b)) { _, _ -> true }
        assertTrue(contacts.isEmpty())
    }

    @Test
    fun `enemies out of contact range produce no contact`() {
        val a = makeGeneral(1L, 1L, 0f, 0f, crew = 100)
        val b = makeGeneral(2L, 2L, 500f, 500f, crew = 100)
        val contacts = detector.findContacts(listOf(a, b)) { _, _ -> true }
        assertTrue(contacts.isEmpty())
    }

    @Test
    fun `non-hostile nations do not produce a contact`() {
        val a = makeGeneral(1L, 1L, 10f, 10f)
        val b = makeGeneral(2L, 2L, 11f, 10f)
        val contacts = detector.findContacts(listOf(a, b)) { _, _ -> false }
        assertTrue(contacts.isEmpty())
    }
}
