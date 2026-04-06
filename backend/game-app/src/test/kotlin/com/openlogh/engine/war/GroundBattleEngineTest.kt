package com.openlogh.engine.war

import com.openlogh.model.GroundUnitType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.random.Random

class GroundBattleEngineTest {

    private lateinit var engine: GroundBattleEngine

    @BeforeEach
    fun setUp() {
        engine = GroundBattleEngine()
    }

    @Test
    fun `defense coverage is ratio of garrison to max 30`() {
        assertEquals(0.0, engine.calculateDefenseCoverage(0), 0.001)
        assertEquals(0.5, engine.calculateDefenseCoverage(15), 0.001)
        assertEquals(1.0, engine.calculateDefenseCoverage(30), 0.001)
        assertEquals(1.0, engine.calculateDefenseCoverage(50), 0.001) // clamped
    }

    @Test
    fun `gas planet only allows grenadier and light infantry`() {
        val allowed = engine.validateUnitTypes(GroundUnitType.entries, isGasPlanet = true, isFortress = false)
        assertEquals(2, allowed.size)
        assertTrue(GroundUnitType.ARMORED_INFANTRY !in allowed)
        assertTrue(GroundUnitType.GRENADIER in allowed)
        assertTrue(GroundUnitType.LIGHT_INFANTRY in allowed)
    }

    @Test
    fun `fortress only allows grenadier and light infantry`() {
        val allowed = engine.validateUnitTypes(GroundUnitType.entries, isGasPlanet = false, isFortress = true)
        assertEquals(2, allowed.size)
        assertTrue(GroundUnitType.ARMORED_INFANTRY !in allowed)
    }

    @Test
    fun `normal planet allows all ground unit types`() {
        val allowed = engine.validateUnitTypes(GroundUnitType.entries, isGasPlanet = false, isFortress = false)
        assertEquals(3, allowed.size)
    }

    @Test
    fun `empty defenders means instant capture`() {
        val attackers = listOf(
            GroundBattleUnit(factionId = 1, unitType = GroundUnitType.GRENADIER, unitCount = 10, hp = 100, maxHp = 100),
        )
        val result = engine.resolveGroundBattle(attackers, emptyList(), Random(42))
        assertTrue(result.attackerWon)
        assertTrue(result.captureComplete)
        assertEquals(10, result.attackerRemainingUnits)
    }

    @Test
    fun `empty attackers means no battle`() {
        val defenders = listOf(
            GroundBattleUnit(factionId = 2, unitType = GroundUnitType.GRENADIER, unitCount = 10, hp = 100, maxHp = 100),
        )
        val result = engine.resolveGroundBattle(emptyList(), defenders, Random(42))
        assertFalse(result.attackerWon)
        assertFalse(result.captureComplete)
    }

    @Test
    fun `ground battle resolves with winner`() {
        val attackers = listOf(
            GroundBattleUnit(factionId = 1, unitType = GroundUnitType.ARMORED_INFANTRY, unitCount = 20, hp = 500, maxHp = 500),
        )
        val defenders = listOf(
            GroundBattleUnit(factionId = 2, unitType = GroundUnitType.LIGHT_INFANTRY, unitCount = 5, hp = 50, maxHp = 50),
        )
        val result = engine.resolveGroundBattle(attackers, defenders, Random(42))
        // Overwhelming attacker advantage should win
        assertTrue(result.attackerWon)
        assertTrue(result.captureComplete)
        assertTrue(result.logs.isNotEmpty())
    }
}
