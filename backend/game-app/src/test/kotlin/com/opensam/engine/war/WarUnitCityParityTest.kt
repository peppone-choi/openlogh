package com.opensam.engine.war

import com.opensam.entity.City
import com.opensam.model.ArmType
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

    private fun buildCity(level: Short = 2): City = City(
        id = 1,
        worldId = 1,
        name = "테스트도시",
        nationId = 2,
        level = level,
        def = 500,
        defMax = 1000,
        wall = 500,
        wallMax = 1000,
        pop = 10000,
        popMax = 50000,
    )

    // ── cityTrainAtmos year-based formula ──

    @Test
    fun `cityTrainAtmos is 60 at game start year plus 1`() {
        // PHP: clamp(181 - 180 + 59, 60, 110) = clamp(60, 60, 110) = 60
        val unit = WarUnitCity(buildCity(), year = 181, startYear = 180)
        assertEquals(60, unit.train, "Train should equal cityTrainAtmos at game start")
        assertEquals(60, unit.atmos, "Atmos should equal cityTrainAtmos at game start")
    }

    @Test
    fun `cityTrainAtmos is 80 at year 201 startYear 180`() {
        // PHP: clamp(201 - 180 + 59, 60, 110) = clamp(80, 60, 110) = 80
        val unit = WarUnitCity(buildCity(), year = 201, startYear = 180)
        assertEquals(80, unit.train)
        assertEquals(80, unit.atmos)
    }

    @Test
    fun `cityTrainAtmos caps at 110`() {
        // PHP: clamp(241 - 180 + 59, 60, 110) = clamp(120, 60, 110) = 110
        val unit = WarUnitCity(buildCity(), year = 241, startYear = 180)
        assertEquals(110, unit.train)
        assertEquals(110, unit.atmos)
    }

    @Test
    fun `cityTrainAtmos with level 1 city adds 5 to train only`() {
        // PHP: level==1 → trainBonus += 5; getComputedTrain = cityTrainAtmos + 5
        // year=201, startYear=180 → cityTrainAtmos=80; train=80+5=85, atmos=80
        val unit = WarUnitCity(buildCity(level = 1), year = 201, startYear = 180)
        assertEquals(85, unit.train, "Level 1 city grants +5 train bonus")
        assertEquals(80, unit.atmos, "Level 1 city does not affect atmos")
    }

    @Test
    fun `cityTrainAtmos with level 3 city adds 5 to train only`() {
        // PHP: level==3 → trainBonus += 5
        val unit = WarUnitCity(buildCity(level = 3), year = 201, startYear = 180)
        assertEquals(85, unit.train, "Level 3 city grants +5 train bonus")
        assertEquals(80, unit.atmos, "Level 3 city does not affect atmos")
    }

    @Test
    fun `cityTrainAtmos with level 2 city has no bonus`() {
        val unit = WarUnitCity(buildCity(level = 2), year = 201, startYear = 180)
        assertEquals(80, unit.train)
        assertEquals(80, unit.atmos)
    }

    // ── getDexForArmType ──

    @Test
    fun `getDexForArmType returns cityTrainAtmos based value`() {
        // PHP: getDex() = (cityTrainAtmos - 60) * 7200
        // year=201, startYear=180 → cityTrainAtmos=80 → (80-60)*7200 = 144000
        val unit = WarUnitCity(buildCity(), year = 201, startYear = 180)
        assertEquals(144_000, unit.getDexForArmType(ArmType.FOOTMAN))
    }

    @Test
    fun `getDexForArmType returns 0 at game start (cityTrainAtmos=60)`() {
        // PHP: (60-60)*7200 = 0
        val unit = WarUnitCity(buildCity(), year = 181, startYear = 180)
        assertEquals(0, unit.getDexForArmType(ArmType.ARCHER))
    }

    @Test
    fun `getDexForArmType returns 360000 at max cityTrainAtmos`() {
        // PHP: (110-60)*7200 = 50*7200 = 360000
        val unit = WarUnitCity(buildCity(), year = 241, startYear = 180)
        assertEquals(360_000, unit.getDexForArmType(ArmType.CAVALRY))
    }

    @Test
    fun `getDexForArmType returns same value regardless of arm type`() {
        // PHP: getDex() ignores the crewType argument, only uses cityTrainAtmos
        val unit = WarUnitCity(buildCity(), year = 201, startYear = 180)
        val expected = 144_000
        assertEquals(expected, unit.getDexForArmType(ArmType.FOOTMAN))
        assertEquals(expected, unit.getDexForArmType(ArmType.ARCHER))
        assertEquals(expected, unit.getDexForArmType(ArmType.SIEGE))
    }
}
