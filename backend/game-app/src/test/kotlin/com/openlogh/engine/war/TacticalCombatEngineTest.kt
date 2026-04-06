package com.openlogh.engine.war

import com.openlogh.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TacticalCombatEngineTest {

    private lateinit var engine: TacticalCombatEngine

    @BeforeEach
    fun setUp() {
        engine = TacticalCombatEngine()
    }

    private fun createUnit(
        fleetId: Long = 1,
        officerId: Long = 1,
        factionId: Long = 1,
        posX: Double = 0.0,
        posY: Double = 0.0,
        hp: Int = 1000,
        ships: Int = 1000,
        supplies: Int = 5000,
        commandStat: Int = 50,
        stance: UnitStance = UnitStance.NAVIGATION,
    ): TacticalCombatEngine.TacticalUnit = TacticalCombatEngine.TacticalUnit(
        fleetId = fleetId,
        officerId = officerId,
        officerName = "Officer$officerId",
        factionId = factionId,
        posX = posX,
        posY = posY,
        hp = hp,
        maxHp = hp,
        ships = ships,
        maxShips = ships,
        supplies = supplies,
        training = 80,
        morale = 80,
        commandStat = commandStat,
        attackStat = 50,
        defenseStat = 50,
        mobilityStat = 50,
        intelligenceStat = 50,
        stance = stance,
        commandRange = CommandRange.create(commandStat, 100.0),
    )

    @Test
    fun `stance change request accepted and sets delay`() {
        val unit = createUnit()
        val accepted = engine.requestStanceChange(unit, UnitStance.COMBAT)
        assertTrue(accepted)
        assertEquals(UnitStance.COMBAT, unit.stance)
        assertTrue(unit.stanceChangeTicksRemaining > 0)
    }

    @Test
    fun `stance change rejected when already changing`() {
        val unit = createUnit()
        engine.requestStanceChange(unit, UnitStance.COMBAT)
        val rejected = engine.requestStanceChange(unit, UnitStance.ANCHORING)
        assertFalse(rejected)
    }

    @Test
    fun `stance change to same stance rejected`() {
        val unit = createUnit(stance = UnitStance.NAVIGATION)
        val rejected = engine.requestStanceChange(unit, UnitStance.NAVIGATION)
        assertFalse(rejected)
    }

    @Test
    fun `issuing command resets command range to zero`() {
        val unit = createUnit(commandStat = 50)
        // Simulate range expansion
        unit.commandRange = unit.commandRange.tick().tick().tick()
        assertTrue(unit.commandRange.currentRange > 0.0)

        engine.issueCommand(unit)
        assertEquals(0.0, unit.commandRange.currentRange, 0.001)
    }

    @Test
    fun `command range expands on tick`() {
        val unit = createUnit(commandStat = 50)
        val initialRange = unit.commandRange.currentRange
        unit.commandRange = unit.commandRange.tick()
        assertTrue(unit.commandRange.currentRange > initialRange)
    }

    @Test
    fun `fire weapon applies damage and sets cooldown`() {
        val source = createUnit(factionId = 1, posX = 0.0, posY = 0.0)
        val target = createUnit(factionId = 2, posX = 2.0, posY = 0.0, hp = 1000)
        val initialHp = target.hp

        val event = engine.fireWeapon(source, target, TacticalWeaponType.BEAM, tick = 1)
        assertNotNull(event)
        assertTrue(target.hp < initialHp)
        assertTrue(source.weaponCooldowns.containsKey(TacticalWeaponType.BEAM))
    }

    @Test
    fun `weapon fire rejected when on cooldown`() {
        val source = createUnit(posX = 0.0, posY = 0.0)
        val target = createUnit(factionId = 2, posX = 2.0, posY = 0.0)

        engine.fireWeapon(source, target, TacticalWeaponType.BEAM, tick = 1)
        val secondShot = engine.fireWeapon(source, target, TacticalWeaponType.BEAM, tick = 2)
        assertNull(secondShot)
    }

    @Test
    fun `weapon fire rejected when out of range`() {
        val source = createUnit(posX = 0.0, posY = 0.0)
        val target = createUnit(factionId = 2, posX = 100.0, posY = 100.0)

        val event = engine.fireWeapon(source, target, TacticalWeaponType.BEAM, tick = 1)
        assertNull(event)
    }

    @Test
    fun `missile consumes supplies`() {
        val source = createUnit(posX = 0.0, posY = 0.0, supplies = 1000)
        val target = createUnit(factionId = 2, posX = 5.0, posY = 0.0)
        val initialSupplies = source.supplies

        val event = engine.fireWeapon(source, target, TacticalWeaponType.MISSILE, tick = 1)
        assertNotNull(event)
        assertEquals(initialSupplies - TacticalWeaponType.MISSILE.supplyCostPerUse, source.supplies)
    }

    @Test
    fun `fighter applies speed debuff`() {
        val source = createUnit(posX = 0.0, posY = 0.0, supplies = 1000)
        val target = createUnit(factionId = 2, posX = 3.0, posY = 0.0)

        val event = engine.fireWeapon(source, target, TacticalWeaponType.FIGHTER, tick = 1)
        assertNotNull(event)
        assertTrue(event!!.speedReduction > 0.0)
        assertTrue(target.debuffs.containsKey("fighter_slow"))
    }

    @Test
    fun `command authority resolves highest priority`() {
        val candidates = listOf(
            CommandAuthority(commanderId = 1, fleetId = 1, factionId = 1, isOnline = false, rankLevel = 5, evaluationPoints = 100, meritPoints = 50),
            CommandAuthority(commanderId = 2, fleetId = 2, factionId = 1, isOnline = true, rankLevel = 3, evaluationPoints = 50, meritPoints = 30),
            CommandAuthority(commanderId = 3, fleetId = 3, factionId = 1, isOnline = true, rankLevel = 7, evaluationPoints = 200, meritPoints = 100),
        )
        val commander = engine.resolveCommandAuthority(candidates)
        assertNotNull(commander)
        // Online + highest rank wins
        assertEquals(3L, commander!!.commanderId)
    }

    @Test
    fun `manual transfer rejected when target is moving`() {
        val from = createUnit(fleetId = 1)
        val to = createUnit(fleetId = 2)
        to.velX = 5.0 // moving
        assertFalse(engine.transferCommandAuthority(from, to, listOf(from, to)))
    }

    @Test
    fun `manual transfer accepted when target is stopped and outside command circles`() {
        val from = createUnit(fleetId = 1, posX = 0.0)
        val to = createUnit(fleetId = 2, posX = 200.0) // far away
        assertTrue(engine.transferCommandAuthority(from, to, listOf(from, to)))
    }

    @Test
    fun `processTick detects enemy units`() {
        val units = listOf(
            createUnit(fleetId = 1, officerId = 1, factionId = 1, posX = 0.0, posY = 0.0),
            createUnit(fleetId = 2, officerId = 2, factionId = 2, posX = 3.0, posY = 0.0),
        )
        val result = engine.processTick(1, units, mapOf(1L to 1L, 2L to 2L))
        // Each faction should have detection results for the other
        assertTrue(result.detectionResults.isNotEmpty())
    }

    @Test
    fun `combat stance increases morale decay`() {
        val unit = createUnit(stance = UnitStance.COMBAT)
        unit.morale = 80
        val initialMorale = unit.morale

        val result = engine.processTick(1, listOf(unit), mapOf(1L to 1L))
        // COMBAT stance should have decayed morale
        assertTrue(unit.morale <= initialMorale)
    }

    @Test
    fun `destroyed unit triggers injury event`() {
        val unit = createUnit(hp = 0)
        unit.isAlive = true // still marked alive, tick should process death

        val result = engine.processTick(1, listOf(unit), mapOf(1L to 1L))
        assertEquals(1, result.injuryEvents.size)
        assertFalse(unit.isAlive)
    }
}
