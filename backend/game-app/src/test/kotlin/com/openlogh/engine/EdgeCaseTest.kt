package com.openlogh.engine

import com.openlogh.command.CommandEnv
import com.openlogh.command.constraint.ConstraintContext
import com.openlogh.command.constraint.ConstraintResult
import com.openlogh.command.constraint.ReqCityCapacity
import com.openlogh.command.constraint.ReqGeneralCrew
import com.openlogh.engine.war.BattleEngine
import com.openlogh.engine.war.WarUnitGeneral
import com.openlogh.entity.City
import com.openlogh.entity.General
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime

/**
 * Edge-case test suite covering boundary conditions across command, constraint, battle, RNG, and formula logic.
 */
class EdgeCaseTest {

    // ─── helpers ────────────────────────────────────────────────────────────

    private fun general(
        id: Long = 1,
        nationId: Long = 1,
        cityId: Long = 1,
        leadership: Short = 70,
        strength: Short = 70,
        intel: Short = 70,
        crew: Int = 1000,
        crewType: Short = 0,
        train: Short = 60,
        atmos: Short = 60,
        gold: Int = 100_000,
        rice: Int = 100_000,
        injury: Short = 0,
    ): General = General(
        id = id,
        worldId = 1,
        name = "장수$id",
        nationId = nationId,
        cityId = cityId,
        leadership = leadership,
        strength = strength,
        intel = intel,
        crew = crew,
        crewType = crewType,
        train = train,
        atmos = atmos,
        gold = gold,
        rice = rice,
        injury = injury,
        turnTime = OffsetDateTime.now(),
    )

    private fun city(
        nationId: Long = 1,
        pop: Int = 50_000,
        trust: Float = 80f,
    ): City = City(
        id = 1,
        worldId = 1,
        name = "테스트도시",
        nationId = nationId,
        pop = pop,
        popMax = 100_000,
        agri = 500, agriMax = 1000,
        comm = 500, commMax = 1000,
        secu = 500, secuMax = 1000,
        def = 500, defMax = 1000,
        wall = 500, wallMax = 1000,
        trust = trust,
        supplyState = 1,
    )

    private fun env(): CommandEnv = CommandEnv(
        year = 200,
        month = 1,
        startYear = 190,
        worldId = 1,
        realtimeMode = false,
        develCost = 100,
    )

    private fun constraintCtx(gen: General, city: City? = null) =
        ConstraintContext(officer = gen, planet = city)

    // ─── 1. General with 0 troops: ReqGeneralCrew blocks ────────────────────

    @Test
    fun `ReqGeneralCrew fails when crew is 0`() {
        val gen = general(crew = 0)
        val result = ReqGeneralCrew(minCrew = 1).test(constraintCtx(gen))
        assertTrue(result is ConstraintResult.Fail, "Expected Fail when crew=0")
    }

    @Test
    fun `ReqGeneralCrew passes when crew is 1`() {
        val gen = general(crew = 1)
        val result = ReqGeneralCrew(minCrew = 1).test(constraintCtx(gen))
        assertEquals(ConstraintResult.Pass, result)
    }

    // ─── 2. General with max stats (all 100): WarUnitGeneral no overflow ─────

    @Test
    fun `WarUnitGeneral with all stats 100 does not overflow or throw`() {
        val gen = general(leadership = 100, strength = 100, intel = 100, crew = 100_000, train = 100, atmos = 100)
        val unit = WarUnitGeneral(gen)
        // Should not throw; base attack/defence are finite positive doubles
        val baseAttack = unit.getBaseAttack()
        val baseDefence = unit.getBaseDefence()
        assertTrue(baseAttack.isFinite() && baseAttack > 0, "baseAttack=$baseAttack should be finite positive")
        assertTrue(baseDefence.isFinite() && baseDefence > 0, "baseDefence=$baseDefence should be finite positive")
    }

    // ─── 3. City with 0 population: 징병 ReqCityCapacity blocks ─────────────

    @Test
    fun `ReqCityCapacity pop fails when city has 0 population`() {
        val gen = general()
        val zeroPop = city(pop = 0)
        // ReqCityCapacity("pop", ...) needs pop >= MIN_AVAILABLE_RECRUIT_POP + reqCrew
        val constraint = ReqCityCapacity("pop", "주민", 3100)
        val result = constraint.test(constraintCtx(gen, zeroPop))
        assertTrue(result is ConstraintResult.Fail, "Expected Fail when city pop=0")
    }

    // ─── 4. Battle determinism + warPower < 100 RNG floor ───────────────────

    @Test
    fun `BattleEngine is deterministic with same seed`() {
        val engine = BattleEngine()
        val attacker = general(leadership = 30, strength = 30, intel = 30, crew = 100, train = 40, atmos = 40)
        val defender = general(id = 2, nationId = 2, leadership = 30, strength = 30, intel = 30, crew = 100, train = 40, atmos = 40)
        val c = city(nationId = 2)

        val r1 = engine.resolveBattle(
            attacker = WarUnitGeneral(attacker),
            defenders = listOf(WarUnitGeneral(general(id = 2, nationId = 2, leadership = 30, strength = 30, intel = 30, crew = 100, train = 40, atmos = 40))),
            city = c,
            rng = LiteHashDRBG.build("edge_case_det"),
        )
        val r2 = engine.resolveBattle(
            attacker = WarUnitGeneral(general(leadership = 30, strength = 30, intel = 30, crew = 100, train = 40, atmos = 40)),
            defenders = listOf(WarUnitGeneral(general(id = 2, nationId = 2, leadership = 30, strength = 30, intel = 30, crew = 100, train = 40, atmos = 40))),
            city = city(nationId = 2),
            rng = LiteHashDRBG.build("edge_case_det"),
        )

        assertEquals(r1.attackerDamageDealt, r2.attackerDamageDealt)
        assertEquals(r1.defenderDamageDealt, r2.defenderDamageDealt)
        assertEquals(r1.attackerWon, r2.attackerWon)
    }

    @Test
    fun `BattleEngine with very weak attacker does not produce negative damage`() {
        val engine = BattleEngine()
        // Very weak attacker (low stats) triggers warPower < 100 floor path
        val attacker = general(leadership = 1, strength = 1, intel = 1, crew = 100, train = 1, atmos = 1)
        val defender = general(id = 2, nationId = 2, leadership = 100, strength = 100, intel = 100, crew = 10_000, train = 100, atmos = 100)
        val c = city(nationId = 2)

        val result = engine.resolveBattle(
            attacker = WarUnitGeneral(attacker),
            defenders = listOf(WarUnitGeneral(defender)),
            city = c,
            rng = LiteHashDRBG.build("edge_low_wp"),
        )

        assertTrue(result.attackerDamageDealt >= 0, "attackerDamageDealt should be non-negative")
        assertTrue(result.defenderDamageDealt >= 0, "defenderDamageDealt should be non-negative")
    }

    // ─── 5. Injury wound probability formula ────────────────────────────────

    @Test
    fun `wound chance is 0 when no HP loss (hpLossRatio = 0)`() {
        // woundChance = (hpLossRatio * 0.5).coerceAtMost(0.3)
        val hpLossRatio = 0.0
        val woundChance = (hpLossRatio * 0.5).coerceAtMost(0.3)
        assertEquals(0.0, woundChance, 0.0001)
    }

    @Test
    fun `wound chance is capped at 0_3 when full HP loss (hpLossRatio = 1_0)`() {
        val hpLossRatio = 1.0
        val woundChance = (hpLossRatio * 0.5).coerceAtMost(0.3)
        assertEquals(0.3, woundChance, 0.0001)
    }

    @Test
    fun `wound chance is 0_15 at 30 percent HP loss`() {
        val hpLossRatio = 0.3
        val woundChance = (hpLossRatio * 0.5).coerceAtMost(0.3)
        assertEquals(0.15, woundChance, 0.0001)
    }

    // ─── 6. RNG: same seed produces identical output ─────────────────────────

    @Test
    fun `LiteHashDRBG same seed produces identical sequence`() {
        val rng1 = LiteHashDRBG.build("determinism_test_seed")
        val rng2 = LiteHashDRBG.build("determinism_test_seed")

        val seq1 = (1..20).map { rng1.nextDouble() }
        val seq2 = (1..20).map { rng2.nextDouble() }

        assertEquals(seq1, seq2)
    }

    @Test
    fun `LiteHashDRBG different seeds produce different sequences`() {
        val rng1 = LiteHashDRBG.build("seed_alpha")
        val rng2 = LiteHashDRBG.build("seed_beta")

        val seq1 = (1..20).map { rng1.nextDouble() }
        val seq2 = (1..20).map { rng2.nextDouble() }

        assertNotEquals(seq1, seq2)
    }

}
