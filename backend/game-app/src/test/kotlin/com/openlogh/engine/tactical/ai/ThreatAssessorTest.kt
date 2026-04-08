package com.openlogh.engine.tactical.ai

import com.openlogh.engine.ai.PersonalityTrait
import com.openlogh.engine.tactical.BattleSide
import com.openlogh.engine.tactical.TacticalUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Tests for ThreatAssessor: threat scoring, ranking, retreat conditions, high-threat detection.
 */
class ThreatAssessorTest {

    // ── Test helper ──

    private fun makeUnit(
        fleetId: Long = 1L,
        officerId: Long = 1L,
        side: BattleSide = BattleSide.ATTACKER,
        posX: Double = 0.0,
        posY: Double = 0.0,
        hp: Int = 100,
        maxHp: Int = 100,
        ships: Int = 300,
        maxShips: Int = 300,
        morale: Int = 50,
        attack: Int = 50,
        isRetreating: Boolean = false,
    ) = TacticalUnit(
        fleetId = fleetId,
        officerId = officerId,
        officerName = "TestOfficer",
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
        isRetreating = isRetreating,
    )

    private fun makeContext(
        unit: TacticalUnit,
        enemies: List<TacticalUnit>,
        personality: PersonalityTrait = PersonalityTrait.BALANCED,
        allies: List<TacticalUnit> = emptyList(),
        mission: MissionObjective = MissionObjective.CONQUEST,
    ): TacticalAIContext {
        val profile = TacticalPersonalityConfig.forTrait(personality)
        return TacticalAIContext(
            unit = unit,
            allies = allies,
            enemies = enemies,
            mission = mission,
            personality = personality,
            profile = profile,
            currentTick = 0,
        )
    }

    // ── scoreThreat tests ──

    @Test
    fun `scoreThreat scores closer enemy higher than distant enemy`() {
        val self = makeUnit(posX = 0.0, posY = 0.0)
        val closeEnemy = makeUnit(fleetId = 2L, posX = 100.0, posY = 0.0)
        val farEnemy = makeUnit(fleetId = 3L, posX = 800.0, posY = 0.0)

        val closeScore = ThreatAssessor.scoreThreat(self, closeEnemy)
        val farScore = ThreatAssessor.scoreThreat(self, farEnemy)

        assertTrue(closeScore > farScore, "Close enemy ($closeScore) should score higher than far enemy ($farScore)")
    }

    @Test
    fun `scoreThreat scores high-HP enemy higher than low-HP enemy`() {
        val self = makeUnit(posX = 0.0, posY = 0.0)
        val highHpEnemy = makeUnit(fleetId = 2L, posX = 200.0, hp = 100, maxHp = 100)
        val lowHpEnemy = makeUnit(fleetId = 3L, posX = 200.0, hp = 20, maxHp = 100)

        val highScore = ThreatAssessor.scoreThreat(self, highHpEnemy)
        val lowScore = ThreatAssessor.scoreThreat(self, lowHpEnemy)

        assertTrue(highScore > lowScore, "High-HP enemy ($highScore) should score higher than low-HP enemy ($lowScore)")
    }

    @Test
    fun `scoreThreat returns value in 0-100 range`() {
        val self = makeUnit()
        val enemy = makeUnit(fleetId = 2L, posX = 500.0)

        val score = ThreatAssessor.scoreThreat(self, enemy)

        assertTrue(score in 0.0..100.0, "Score should be in 0-100 range, got $score")
    }

    // ── rankThreats tests ──

    @Test
    fun `rankThreats returns enemies sorted by threat score descending`() {
        val self = makeUnit(posX = 0.0)
        val weakFar = makeUnit(fleetId = 2L, posX = 800.0, hp = 30, maxHp = 100, side = BattleSide.DEFENDER)
        val strongClose = makeUnit(fleetId = 3L, posX = 100.0, hp = 100, maxHp = 100, side = BattleSide.DEFENDER)
        val ctx = makeContext(self, listOf(weakFar, strongClose))

        val ranked = ThreatAssessor.rankThreats(ctx)

        assertEquals(2, ranked.size)
        assertEquals(3L, ranked[0].enemyFleetId, "Strong close enemy should rank first")
        assertEquals(2L, ranked[1].enemyFleetId, "Weak far enemy should rank second")
    }

    @Test
    fun `rankThreats filters out retreating enemies`() {
        val self = makeUnit(posX = 0.0)
        val retreating = makeUnit(fleetId = 2L, posX = 100.0, side = BattleSide.DEFENDER, isRetreating = true)
        val active = makeUnit(fleetId = 3L, posX = 200.0, side = BattleSide.DEFENDER)
        val ctx = makeContext(self, listOf(retreating, active))

        val ranked = ThreatAssessor.rankThreats(ctx)

        assertEquals(1, ranked.size)
        assertEquals(3L, ranked[0].enemyFleetId)
    }

    // ── shouldRetreat tests ──

    @Test
    fun `shouldRetreat for AGGRESSIVE at HP 9 percent returns true`() {
        val unit = makeUnit(hp = 9, maxHp = 100)
        val ctx = makeContext(unit, emptyList(), PersonalityTrait.AGGRESSIVE)

        assertTrue(ThreatAssessor.shouldRetreat(ctx))
    }

    @Test
    fun `shouldRetreat for AGGRESSIVE at HP 11 percent returns false`() {
        val unit = makeUnit(hp = 11, maxHp = 100)
        val ctx = makeContext(unit, emptyList(), PersonalityTrait.AGGRESSIVE)

        assertFalse(ThreatAssessor.shouldRetreat(ctx))
    }

    @Test
    fun `shouldRetreat for CAUTIOUS at HP 29 percent returns true`() {
        val unit = makeUnit(hp = 29, maxHp = 100)
        val ctx = makeContext(unit, emptyList(), PersonalityTrait.CAUTIOUS)

        assertTrue(ThreatAssessor.shouldRetreat(ctx))
    }

    @Test
    fun `shouldRetreat for CAUTIOUS at HP 31 percent returns false`() {
        val unit = makeUnit(hp = 31, maxHp = 100)
        val ctx = makeContext(unit, emptyList(), PersonalityTrait.CAUTIOUS)

        assertFalse(ThreatAssessor.shouldRetreat(ctx))
    }

    @Test
    fun `shouldRetreat for BALANCED at morale 25 returns true`() {
        val unit = makeUnit(hp = 100, maxHp = 100, morale = 25)
        val ctx = makeContext(unit, emptyList(), PersonalityTrait.BALANCED)

        assertTrue(ThreatAssessor.shouldRetreat(ctx))
    }

    @Test
    fun `shouldRetreat for BALANCED at morale 35 returns false`() {
        val unit = makeUnit(hp = 100, maxHp = 100, morale = 35)
        val ctx = makeContext(unit, emptyList(), PersonalityTrait.BALANCED)

        assertFalse(ThreatAssessor.shouldRetreat(ctx))
    }

    // ── isHighThreat tests ──

    @Test
    fun `isHighThreat returns true for very close full-HP high-attack enemy`() {
        val self = makeUnit(posX = 0.0)
        val dangerousEnemy = makeUnit(fleetId = 2L, posX = 50.0, hp = 100, maxHp = 100, attack = 90)

        assertTrue(ThreatAssessor.isHighThreat(self, dangerousEnemy))
    }

    @Test
    fun `isHighThreat returns false for distant low-HP low-attack enemy`() {
        val self = makeUnit(posX = 0.0)
        val weakEnemy = makeUnit(fleetId = 2L, posX = 900.0, hp = 10, maxHp = 100, ships = 50, maxShips = 300, attack = 10)

        assertFalse(ThreatAssessor.isHighThreat(self, weakEnemy))
    }
}
