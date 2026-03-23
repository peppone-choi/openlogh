package com.openlogh.engine.war

import com.openlogh.entity.*
import com.openlogh.model.ArmType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * TDD parity tests for WarUnitCity vs PHP WarUnitCity.php
 *
 * Legacy PHP reference: WarUnitCity::__construct()
 *   cityTrainAtmos = clamp(year - startYear + 59, 60, 110)
 *   getComputedTrain() = cityTrainAtmos + trainBonus
 *   getComputedAtmos() = cityTrainAtmos + atmosBonus
 *   getDex() = (cityTrainAtmos - 60) * 7200
 */
class WarUnitCityParityTest {

    private fun buildPlanet(level: Short = 2): Planet = Planet(
        id = 1,
        sessionId = 1,
        name = "테스트도시",
        factionId = 2,
        level = level,
        orbitalDefense = 500,
        orbitalDefenseMax = 1000,
        fortress = 500,
        fortressMax = 1000,
        population = 10000,
        populationMax = 50000,
    )

    // ── cityTrainAtmos year-based formula ──

    @Test
    fun `cityTrainAtmos is 60 at game start year plus 1`() {
        // PHP: clamp(181 - 180 + 59, 60, 110) = clamp(60, 60, 110) = 60
        val unit = WarUnitCity(buildPlanet(), year = 181, startYear = 180)
        assertEquals(60, unit.training, "Training should equal cityTrainAtmos at game start")
        assertEquals(60, unit.morale, "Morale should equal cityTrainAtmos at game start")
    }

    @Test
    fun `cityTrainAtmos is 80 at year 201 startYear 180`() {
        // PHP: clamp(201 - 180 + 59, 60, 110) = clamp(80, 60, 110) = 80
        val unit = WarUnitCity(buildPlanet(), year = 201, startYear = 180)
        assertEquals(80, unit.training)
        assertEquals(80, unit.morale)
    }

    @Test
    fun `cityTrainAtmos caps at 110`() {
        // PHP: clamp(241 - 180 + 59, 60, 110) = clamp(120, 60, 110) = 110
        val unit = WarUnitCity(buildPlanet(), year = 241, startYear = 180)
        assertEquals(110, unit.training)
        assertEquals(110, unit.morale)
    }

    @Test
    fun `cityTrainAtmos with level 1 city adds 5 to training only`() {
        // PHP: level==1 → trainBonus += 5; getComputedTrain = cityTrainAtmos + 5
        // year=201, startYear=180 → cityTrainAtmos=80; training=80+5=85, morale=80
        val unit = WarUnitCity(buildPlanet(level = 1), year = 201, startYear = 180)
        assertEquals(85, unit.training, "Level 1 planet grants +5 training bonus")
        assertEquals(80, unit.morale, "Level 1 planet does not affect morale")
    }

    @Test
    fun `cityTrainAtmos with level 3 city adds 5 to training only`() {
        // PHP: level==3 → trainBonus += 5
        val unit = WarUnitCity(buildPlanet(level = 3), year = 201, startYear = 180)
        assertEquals(85, unit.training, "Level 3 planet grants +5 training bonus")
        assertEquals(80, unit.morale, "Level 3 planet does not affect morale")
    }

    @Test
    fun `cityTrainAtmos with level 2 city has no bonus`() {
        val unit = WarUnitCity(buildPlanet(level = 2), year = 201, startYear = 180)
        assertEquals(80, unit.training)
        assertEquals(80, unit.morale)
    }

    // ── getDexForArmType ──

    @Test
    fun `getDexForArmType returns cityTrainAtmos based value`() {
        // PHP: getDex() = (cityTrainAtmos - 60) * 7200
        // year=201, startYear=180 → cityTrainAtmos=80 → (80-60)*7200 = 144000
        val unit = WarUnitCity(buildPlanet(), year = 201, startYear = 180)
        assertEquals(144_000, unit.getDexForArmType(ArmType.FOOTMAN))
    }

    @Test
    fun `getDexForArmType returns 0 at game start (cityTrainAtmos=60)`() {
        // PHP: (60-60)*7200 = 0
        val unit = WarUnitCity(buildPlanet(), year = 181, startYear = 180)
        assertEquals(0, unit.getDexForArmType(ArmType.ARCHER))
    }

    @Test
    fun `getDexForArmType returns 360000 at max cityTrainAtmos`() {
        // PHP: (110-60)*7200 = 50*7200 = 360000
        val unit = WarUnitCity(buildPlanet(), year = 241, startYear = 180)
        assertEquals(360_000, unit.getDexForArmType(ArmType.CAVALRY))
    }

    @Test
    fun `getDexForArmType returns same value regardless of arm type`() {
        // PHP: getDex() ignores the crewType argument, only uses cityTrainAtmos
        val unit = WarUnitCity(buildPlanet(), year = 201, startYear = 180)
        val expected = 144_000
        assertEquals(expected, unit.getDexForArmType(ArmType.FOOTMAN))
        assertEquals(expected, unit.getDexForArmType(ArmType.ARCHER))
        assertEquals(expected, unit.getDexForArmType(ArmType.SIEGE))
    }
}
