package com.openlogh.engine.tactical.ai

import com.openlogh.engine.ai.PersonalityTrait
import com.openlogh.engine.tactical.BattleSide
import com.openlogh.engine.tactical.TacticalCommand
import com.openlogh.engine.tactical.TacticalUnit
import com.openlogh.model.EnergyAllocation
import com.openlogh.model.Formation
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Comprehensive tests for TacticalAI decision engine.
 *
 * Covers:
 * - Retreat override (D-05)
 * - CONQUEST mission behavior (D-01)
 * - DEFENSE mission behavior (D-02)
 * - SWEEP mission behavior (D-03)
 * - Energy auto-adjustment (D-08)
 * - Formation auto-set (D-09)
 * - Focus-fire vs distributed attack (D-10)
 * - Personality-based combat style (D-04)
 */
class TacticalAITest {

    // ── Test helpers ──

    private fun makeUnit(
        fleetId: Long = 1L,
        officerId: Long = 100L,
        side: BattleSide = BattleSide.ATTACKER,
        posX: Double = 500.0,
        posY: Double = 300.0,
        hp: Int = 1000,
        maxHp: Int = 1000,
        ships: Int = 300,
        maxShips: Int = 300,
        morale: Int = 80,
        attack: Int = 50,
        defense: Int = 50,
        command: Int = 50,
        energy: EnergyAllocation = EnergyAllocation.BALANCED,
        formation: Formation = Formation.MIXED,
        isRetreating: Boolean = false,
        targetFleetId: Long? = null,
    ) = TacticalUnit(
        fleetId = fleetId,
        officerId = officerId,
        officerName = "TestOfficer$officerId",
        factionId = 1L,
        side = side,
        posX = posX,
        posY = posY,
        hp = hp,
        maxHp = maxHp,
        ships = ships,
        maxShips = maxShips,
        morale = morale,
        attack = attack,
        defense = defense,
        command = command,
        energy = energy,
        formation = formation,
        isRetreating = isRetreating,
        targetFleetId = targetFleetId,
    )

    private fun makeCtx(
        unit: TacticalUnit = makeUnit(),
        allies: List<TacticalUnit> = emptyList(),
        enemies: List<TacticalUnit> = emptyList(),
        mission: MissionObjective = MissionObjective.CONQUEST,
        personality: PersonalityTrait = PersonalityTrait.BALANCED,
        anchorX: Double = 900.0,
        anchorY: Double = 300.0,
        currentTick: Int = 10,
    ): TacticalAIContext {
        val profile = TacticalPersonalityConfig.forTrait(personality)
        return TacticalAIContext(
            unit = unit,
            allies = allies,
            enemies = enemies,
            mission = mission,
            personality = personality,
            profile = profile,
            currentTick = currentTick,
            anchorX = anchorX,
            anchorY = anchorY,
        )
    }

    // ── Retreat tests ──

    @Nested
    @DisplayName("Retreat override")
    inner class RetreatTests {

        @Test
        fun `shouldRetreat true generates Retreat command regardless of mission`() {
            // HP at 5% — well below any retreat threshold
            val unit = makeUnit(hp = 50, maxHp = 1000, morale = 80)
            val enemy = makeUnit(fleetId = 2, officerId = 200, side = BattleSide.DEFENDER, posX = 600.0)
            val ctx = makeCtx(
                unit = unit,
                enemies = listOf(enemy),
                mission = MissionObjective.CONQUEST,
                personality = PersonalityTrait.AGGRESSIVE, // even aggressive retreats at 10%
            )

            val commands = TacticalAI.decide(ctx)

            assertTrue(commands.any { it is TacticalCommand.Retreat })
            // Should be only Retreat — nothing else matters when retreating
            assertEquals(1, commands.size)
        }

        @Test
        fun `low morale triggers retreat for cautious personality`() {
            val unit = makeUnit(hp = 800, maxHp = 1000, morale = 30) // morale < 40 threshold for CAUTIOUS
            val enemy = makeUnit(fleetId = 2, officerId = 200, side = BattleSide.DEFENDER, posX = 600.0)
            val ctx = makeCtx(
                unit = unit,
                enemies = listOf(enemy),
                mission = MissionObjective.SWEEP,
                personality = PersonalityTrait.CAUTIOUS,
            )

            val commands = TacticalAI.decide(ctx)
            assertTrue(commands.any { it is TacticalCommand.Retreat })
        }
    }

    // ── CONQUEST mission tests (D-01) ──

    @Nested
    @DisplayName("CONQUEST mission (D-01)")
    inner class ConquestTests {

        @Test
        fun `no enemies - moves toward target anchor`() {
            val unit = makeUnit(posX = 100.0, posY = 300.0)
            val ctx = makeCtx(
                unit = unit,
                enemies = emptyList(),
                mission = MissionObjective.CONQUEST,
                anchorX = 900.0,
                anchorY = 300.0,
            )

            val commands = TacticalAI.decide(ctx)
            val moveCmd = commands.filterIsInstance<TacticalCommand.UnitCommand>()
            assertTrue(moveCmd.isNotEmpty(), "Should have move command toward target")
            // Should move in positive X direction (toward 900 from 100)
            assertTrue(moveCmd.first().dirX > 0, "dirX should be positive (moving right toward target)")
        }

        @Test
        fun `weak enemy nearby - engages with SetAttackTarget`() {
            val unit = makeUnit(posX = 400.0, posY = 300.0, attack = 80)
            // Weak enemy: low HP, low attack
            val weakEnemy = makeUnit(
                fleetId = 2, officerId = 200, side = BattleSide.DEFENDER,
                posX = 500.0, posY = 300.0, hp = 200, maxHp = 1000, attack = 20, ships = 50, maxShips = 300,
            )
            val ctx = makeCtx(
                unit = unit,
                enemies = listOf(weakEnemy),
                mission = MissionObjective.CONQUEST,
            )

            val commands = TacticalAI.decide(ctx)
            val attackCmd = commands.filterIsInstance<TacticalCommand.SetAttackTarget>()
            assertTrue(attackCmd.isNotEmpty(), "Should attack weak enemy")
            assertEquals(2L, attackCmd.first().targetFleetId)
        }

        @Test
        fun `high-threat enemy nearby - bypasses toward target`() {
            val unit = makeUnit(posX = 400.0, posY = 300.0, attack = 30)
            // Strong enemy: full HP, high attack, close
            val strongEnemy = makeUnit(
                fleetId = 2, officerId = 200, side = BattleSide.DEFENDER,
                posX = 450.0, posY = 300.0, hp = 1000, maxHp = 1000, attack = 90, ships = 300, maxShips = 300,
            )
            val ctx = makeCtx(
                unit = unit,
                enemies = listOf(strongEnemy),
                mission = MissionObjective.CONQUEST,
                anchorX = 900.0,
                anchorY = 300.0,
            )

            val commands = TacticalAI.decide(ctx)
            // Should NOT attack the strong enemy, should move to bypass
            val attackCmd = commands.filterIsInstance<TacticalCommand.SetAttackTarget>()
            assertTrue(attackCmd.isEmpty(), "Should NOT attack high-threat enemy in CONQUEST")
            val moveCmd = commands.filterIsInstance<TacticalCommand.UnitCommand>()
            assertTrue(moveCmd.isNotEmpty(), "Should have bypass movement command")
        }
    }

    // ── DEFENSE mission tests (D-02) ──

    @Nested
    @DisplayName("DEFENSE mission (D-02)")
    inner class DefenseTests {

        @Test
        fun `enemy approaching anchor - intercepts`() {
            val unit = makeUnit(posX = 500.0, posY = 300.0)
            val enemy = makeUnit(
                fleetId = 2, officerId = 200, side = BattleSide.DEFENDER,
                posX = 520.0, posY = 310.0, // close to anchor at 500,300
            )
            val ctx = makeCtx(
                unit = unit,
                enemies = listOf(enemy),
                mission = MissionObjective.DEFENSE,
                anchorX = 500.0,
                anchorY = 300.0,
            )

            val commands = TacticalAI.decide(ctx)
            val attackCmd = commands.filterIsInstance<TacticalCommand.SetAttackTarget>()
            assertTrue(attackCmd.isNotEmpty(), "Should intercept enemy near anchor")
        }

        @Test
        fun `no enemies near anchor - returns to anchor`() {
            // Unit drifted away from anchor
            val unit = makeUnit(posX = 200.0, posY = 100.0)
            val ctx = makeCtx(
                unit = unit,
                enemies = emptyList(),
                mission = MissionObjective.DEFENSE,
                anchorX = 500.0,
                anchorY = 300.0,
            )

            val commands = TacticalAI.decide(ctx)
            val moveCmd = commands.filterIsInstance<TacticalCommand.UnitCommand>()
            assertTrue(moveCmd.isNotEmpty(), "Should move back to anchor")
            // Direction should be toward anchor (500,300) from (200,100)
            assertTrue(moveCmd.first().dirX > 0, "Should move toward anchor X")
            assertTrue(moveCmd.first().dirY > 0, "Should move toward anchor Y")
        }

        @Test
        fun `at anchor with no enemies - holds position (no move command)`() {
            val unit = makeUnit(posX = 500.0, posY = 300.0)
            val ctx = makeCtx(
                unit = unit,
                enemies = emptyList(),
                mission = MissionObjective.DEFENSE,
                anchorX = 510.0, // within 50 distance threshold
                anchorY = 300.0,
            )

            val commands = TacticalAI.decide(ctx)
            val moveCmd = commands.filterIsInstance<TacticalCommand.UnitCommand>()
            assertTrue(moveCmd.isEmpty(), "Should hold position when at anchor with no enemies")
        }
    }

    // ── SWEEP mission tests (D-03) ──

    @Nested
    @DisplayName("SWEEP mission (D-03)")
    inner class SweepTests {

        @Test
        fun `pursues highest-threat enemy`() {
            val unit = makeUnit(posX = 500.0, posY = 300.0)
            val weakEnemy = makeUnit(
                fleetId = 2, officerId = 200, side = BattleSide.DEFENDER,
                posX = 600.0, hp = 200, maxHp = 1000, attack = 20, ships = 50, maxShips = 300,
            )
            val strongEnemy = makeUnit(
                fleetId = 3, officerId = 300, side = BattleSide.DEFENDER,
                posX = 550.0, hp = 900, maxHp = 1000, attack = 80, ships = 280, maxShips = 300,
            )
            val ctx = makeCtx(
                unit = unit,
                enemies = listOf(weakEnemy, strongEnemy),
                mission = MissionObjective.SWEEP,
            )

            val commands = TacticalAI.decide(ctx)
            val attackCmd = commands.filterIsInstance<TacticalCommand.SetAttackTarget>()
            assertTrue(attackCmd.isNotEmpty(), "Should attack in SWEEP mode")
            // Strong enemy (fleetId=3) has higher threat score
            assertEquals(3L, attackCmd.first().targetFleetId, "Should target highest-threat enemy")
        }
    }

    // ── Energy adjustment tests (D-08) ──

    @Nested
    @DisplayName("Energy adjustment (D-08)")
    inner class EnergyTests {

        @Test
        fun `aggressive personality at close range gets OFFENSIVE energy`() {
            val unit = makeUnit(posX = 500.0, energy = EnergyAllocation.BALANCED)
            val enemy = makeUnit(
                fleetId = 2, officerId = 200, side = BattleSide.DEFENDER,
                posX = 580.0, posY = 300.0, // distance ~80, well within GUN_RANGE (150)
            )
            val ctx = makeCtx(
                unit = unit,
                enemies = listOf(enemy),
                mission = MissionObjective.SWEEP,
                personality = PersonalityTrait.AGGRESSIVE,
            )

            val commands = TacticalAI.decide(ctx)
            val energyCmd = commands.filterIsInstance<TacticalCommand.SetEnergy>()
            assertTrue(energyCmd.isNotEmpty(), "Should adjust energy")
            // AGGRESSIVE at close range should get OFFENSIVE-like allocation (high beam+gun)
            val alloc = energyCmd.first().allocation
            assertTrue(alloc.beam + alloc.gun >= 50, "Should favor weapons at close range for AGGRESSIVE")
        }

        @Test
        fun `defensive personality far from enemy gets DEFENSIVE energy`() {
            val unit = makeUnit(posX = 100.0, energy = EnergyAllocation.BALANCED)
            val enemy = makeUnit(
                fleetId = 2, officerId = 200, side = BattleSide.DEFENDER,
                posX = 800.0, posY = 300.0, // distance ~700, far beyond BEAM_RANGE
            )
            val ctx = makeCtx(
                unit = unit,
                enemies = listOf(enemy),
                mission = MissionObjective.DEFENSE,
                personality = PersonalityTrait.DEFENSIVE,
            )

            val commands = TacticalAI.decide(ctx)
            val energyCmd = commands.filterIsInstance<TacticalCommand.SetEnergy>()
            assertTrue(energyCmd.isNotEmpty(), "Should adjust energy for defensive personality far from enemy")
            val alloc = energyCmd.first().allocation
            assertTrue(alloc.shield >= 30, "Defensive personality far away should boost shield")
        }

        @Test
        fun `low HP generates survival energy allocation`() {
            val unit = makeUnit(
                posX = 500.0, hp = 300, maxHp = 1000,
                morale = 80, energy = EnergyAllocation.BALANCED,
            )
            val enemy = makeUnit(
                fleetId = 2, officerId = 200, side = BattleSide.DEFENDER,
                posX = 600.0,
            )
            val ctx = makeCtx(
                unit = unit,
                enemies = listOf(enemy),
                mission = MissionObjective.SWEEP,
                personality = PersonalityTrait.BALANCED, // 20% retreat threshold, HP at 30% still above
            )

            val commands = TacticalAI.decide(ctx)
            val energyCmd = commands.filterIsInstance<TacticalCommand.SetEnergy>()
            assertTrue(energyCmd.isNotEmpty(), "Should adjust energy when HP < 40%")
            val alloc = energyCmd.first().allocation
            assertTrue(
                alloc.shield + alloc.engine >= 40,
                "Low HP should boost shield+engine for survival",
            )
        }

        @Test
        fun `no SetEnergy when current allocation matches computed`() {
            // Unit already has DEFENSIVE energy, and computation would also yield DEFENSIVE
            val unit = makeUnit(posX = 100.0, energy = EnergyAllocation.DEFENSIVE)
            val enemy = makeUnit(
                fleetId = 2, officerId = 200, side = BattleSide.DEFENDER,
                posX = 800.0, posY = 300.0,
            )
            val ctx = makeCtx(
                unit = unit,
                enemies = listOf(enemy),
                mission = MissionObjective.DEFENSE,
                personality = PersonalityTrait.DEFENSIVE,
            )

            val commands = TacticalAI.decide(ctx)
            val energyCmd = commands.filterIsInstance<TacticalCommand.SetEnergy>()
            assertTrue(energyCmd.isEmpty(), "Should NOT emit SetEnergy when allocation already matches")
        }
    }

    // ── Formation tests (D-09) ──

    @Nested
    @DisplayName("Formation adjustment (D-09)")
    inner class FormationTests {

        @Test
        fun `aggressive personality in MIXED switches to WEDGE when no enemy nearby`() {
            val unit = makeUnit(posX = 100.0, formation = Formation.MIXED)
            // No enemies within 300 units
            val ctx = makeCtx(
                unit = unit,
                enemies = emptyList(),
                mission = MissionObjective.CONQUEST,
                personality = PersonalityTrait.AGGRESSIVE,
            )

            val commands = TacticalAI.decide(ctx)
            val formCmd = commands.filterIsInstance<TacticalCommand.SetFormation>()
            assertTrue(formCmd.isNotEmpty(), "Should auto-set formation")
            assertEquals(Formation.WEDGE, formCmd.first().formation)
        }

        @Test
        fun `no formation change when already in preferred formation`() {
            val unit = makeUnit(posX = 100.0, formation = Formation.WEDGE)
            val ctx = makeCtx(
                unit = unit,
                enemies = emptyList(),
                mission = MissionObjective.CONQUEST,
                personality = PersonalityTrait.AGGRESSIVE,
            )

            val commands = TacticalAI.decide(ctx)
            val formCmd = commands.filterIsInstance<TacticalCommand.SetFormation>()
            assertTrue(formCmd.isEmpty(), "Should NOT change formation when already preferred")
        }
    }

    // ── Focus-fire vs distributed attack (D-10) ──

    @Nested
    @DisplayName("Focus-fire vs distributed (D-10)")
    inner class FocusFireTests {

        @Test
        fun `CONQUEST focuses fire on single target`() {
            val unit = makeUnit(posX = 400.0)
            val ally = makeUnit(fleetId = 10, officerId = 101, posX = 420.0, targetFleetId = 2L)
            val enemy1 = makeUnit(
                fleetId = 2, officerId = 200, side = BattleSide.DEFENDER,
                posX = 500.0, hp = 300, maxHp = 1000, attack = 30,
            )
            val enemy2 = makeUnit(
                fleetId = 3, officerId = 300, side = BattleSide.DEFENDER,
                posX = 550.0, hp = 400, maxHp = 1000, attack = 40,
            )
            val ctx = makeCtx(
                unit = unit,
                allies = listOf(ally),
                enemies = listOf(enemy1, enemy2),
                mission = MissionObjective.CONQUEST,
            )

            val commands = TacticalAI.decide(ctx)
            val attackCmd = commands.filterIsInstance<TacticalCommand.SetAttackTarget>()
            assertTrue(attackCmd.isNotEmpty(), "CONQUEST should focus fire")
            // CONQUEST picks weakest target (lowest threat)
            assertEquals(2L, attackCmd.first().targetFleetId, "CONQUEST should pick weakest (lowest threat)")
        }

        @Test
        fun `SWEEP with allies distributes targets`() {
            val unit = makeUnit(posX = 400.0)
            // Ally already targeting enemy 3 (highest threat)
            val ally = makeUnit(fleetId = 10, officerId = 101, posX = 420.0, targetFleetId = 3L)
            val enemy1 = makeUnit(
                fleetId = 2, officerId = 200, side = BattleSide.DEFENDER,
                posX = 500.0, hp = 300, maxHp = 1000, attack = 20, ships = 100, maxShips = 300,
            )
            val enemy2 = makeUnit(
                fleetId = 3, officerId = 300, side = BattleSide.DEFENDER,
                posX = 550.0, hp = 900, maxHp = 1000, attack = 80, ships = 280, maxShips = 300,
            )
            val ctx = makeCtx(
                unit = unit,
                allies = listOf(ally),
                enemies = listOf(enemy1, enemy2),
                mission = MissionObjective.SWEEP,
            )

            val commands = TacticalAI.decide(ctx)
            val attackCmd = commands.filterIsInstance<TacticalCommand.SetAttackTarget>()
            assertTrue(attackCmd.isNotEmpty(), "SWEEP should still attack")
            // With ally already on enemy 3, SWEEP should distribute to enemy 2
            assertEquals(2L, attackCmd.first().targetFleetId, "SWEEP should distribute to un-targeted enemy")
        }
    }

    // ── Combined behavior tests ──

    @Nested
    @DisplayName("Combined behaviors")
    inner class CombinedTests {

        @Test
        fun `decide never returns empty list for alive unit with enemies`() {
            val unit = makeUnit()
            val enemy = makeUnit(fleetId = 2, officerId = 200, side = BattleSide.DEFENDER, posX = 600.0)
            val ctx = makeCtx(unit = unit, enemies = listOf(enemy))

            val commands = TacticalAI.decide(ctx)
            assertTrue(commands.isNotEmpty(), "decide() should never return empty for alive unit with enemies")
        }

        @Test
        fun `personality modifies target selection - AGGRESSIVE picks higher HP target`() {
            val unit = makeUnit(posX = 400.0)
            val weakEnemy = makeUnit(
                fleetId = 2, officerId = 200, side = BattleSide.DEFENDER,
                posX = 500.0, hp = 100, maxHp = 1000, attack = 30, ships = 50, maxShips = 300,
            )
            val strongEnemy = makeUnit(
                fleetId = 3, officerId = 300, side = BattleSide.DEFENDER,
                posX = 510.0, hp = 900, maxHp = 1000, attack = 70, ships = 280, maxShips = 300,
            )
            val ctx = makeCtx(
                unit = unit,
                enemies = listOf(weakEnemy, strongEnemy),
                mission = MissionObjective.SWEEP,
                personality = PersonalityTrait.AGGRESSIVE,
            )

            val commands = TacticalAI.decide(ctx)
            val attackCmd = commands.filterIsInstance<TacticalCommand.SetAttackTarget>()
            assertTrue(attackCmd.isNotEmpty())
            // AGGRESSIVE in SWEEP targets highest threat (strong enemy, fleetId=3)
            assertEquals(3L, attackCmd.first().targetFleetId, "AGGRESSIVE should target highest-threat in SWEEP")
        }

        @Test
        fun `all command types use correct battleId and officerId`() {
            val unit = makeUnit(posX = 100.0, formation = Formation.THREE_COLUMN, energy = EnergyAllocation.BALANCED)
            val enemy = makeUnit(
                fleetId = 2, officerId = 200, side = BattleSide.DEFENDER,
                posX = 250.0, hp = 200, maxHp = 1000, attack = 20, ships = 50, maxShips = 300,
            )
            val ctx = makeCtx(
                unit = unit,
                enemies = listOf(enemy),
                mission = MissionObjective.CONQUEST,
                personality = PersonalityTrait.AGGRESSIVE,
            )

            val commands = TacticalAI.decide(ctx)
            // All commands should reference the unit's officerId
            commands.forEach { cmd ->
                assertEquals(100L, cmd.officerId, "All commands should use unit's officerId")
            }
        }
    }
}
