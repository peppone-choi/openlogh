package com.openlogh.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ShipSubtypeTest {

    @Test
    fun `all subtypes have valid ship class reference`() {
        for (subtype in ShipSubtype.entries) {
            assertNotNull(subtype.shipClass)
        }
    }

    @Test
    fun `faction-specific ships are correctly restricted`() {
        assertEquals("empire", ShipSubtype.FAST_BATTLESHIP.factionRestriction)
        assertEquals("alliance", ShipSubtype.STRIKE_CRUISER.factionRestriction)
        assertEquals("empire", ShipSubtype.TORPEDO_BOAT_CARRIER.factionRestriction)
    }

    @Test
    fun `empire filter excludes alliance ships`() {
        val empireShips = ShipSubtype.availableForFaction("empire")
        assertTrue(ShipSubtype.FAST_BATTLESHIP in empireShips)
        assertFalse(ShipSubtype.STRIKE_CRUISER in empireShips)
    }

    @Test
    fun `alliance filter excludes empire ships`() {
        val allianceShips = ShipSubtype.availableForFaction("alliance")
        assertTrue(ShipSubtype.STRIKE_CRUISER in allianceShips)
        assertFalse(ShipSubtype.FAST_BATTLESHIP in allianceShips)
        assertFalse(ShipSubtype.TORPEDO_BOAT_CARRIER in allianceShips)
    }

    @Test
    fun `fromCode returns correct subtype`() {
        assertEquals(ShipSubtype.BATTLESHIP_I, ShipSubtype.fromCode(2001))
        assertEquals(ShipSubtype.FAST_BATTLESHIP, ShipSubtype.fromCode(2004))
        assertNull(ShipSubtype.fromCode(9999))
    }

    @Test
    fun `byShipClass groups correctly`() {
        val battleships = ShipSubtype.byShipClass(ShipClass.BATTLESHIP)
        assertTrue(battleships.size >= 3)
        assertTrue(battleships.all { it.shipClass == ShipClass.BATTLESHIP })
    }

    @Test
    fun `all ship classes have at least one subtype`() {
        for (shipClass in ShipClass.entries) {
            val subtypes = ShipSubtype.byShipClass(shipClass)
            assertTrue(subtypes.isNotEmpty(), "Ship class ${shipClass.name} has no subtypes")
        }
    }
}
