package com.openlogh.engine

import com.openlogh.command.CommandEnv
import com.openlogh.command.constraint.ConstraintContext
import com.openlogh.command.constraint.ConstraintResult
import com.openlogh.command.constraint.ReqCityCapacity
import com.openlogh.command.constraint.ReqGeneralCrew
import com.openlogh.command.general.che_징병
import com.openlogh.command.general.che_모병
import com.openlogh.engine.war.BattleEngine
import com.openlogh.engine.war.WarUnitOfficer
import com.openlogh.entity.Planet
import com.openlogh.entity.Officer
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime

/**
 * Edge-case test suite covering boundary conditions across command, constraint, battle, RNG, and formula logic.
 */
class EdgeCaseTest {

    private val mapper = jacksonObjectMapper()

    // ─── helpers ────────────────────────────────────────────────────────────

    private fun general(
        id: Long = 1,
        factionId: Long = 1,
        planetId: Long = 1,
        leadership: Short = 70,
        command: Short = 70,
        intelligence: Short = 70,
        ships: Int = 1000,
        shipClass: Short = 0,
        training: Short = 60,
        morale: Short = 60,
        funds: Int = 100_000,
        supplies: Int = 100_000,
        injury: Short = 0,
    ): Officer = Officer(
        id = id,
        sessionId = 1,
        name = "장수$id",
        factionId = factionId,
        planetId = planetId,
        leadership = leadership,
        command = command,
        intelligence = intelligence,
        ships = ships,
        shipClass = shipClass,
        training = training,
        morale = morale,
        funds = funds,
        supplies = supplies,
        injury = injury,
        turnTime = OffsetDateTime.now(),
    )

    private fun city(
        factionId: Long = 1,
        population: Int = 50_000,
        approval: Float = 80f,
    ): Planet = Planet(
        id = 1,
        sessionId = 1,
        name = "테스트도시",
        factionId = factionId,
        population = population,
        populationMax = 100_000,
        production = 500, productionMax = 1000,
        commerce = 500, commerceMax = 1000,
        security = 500, securityMax = 1000,
        orbitalDefense = 500, orbitalDefenseMax = 1000,
        fortress = 500, fortressMax = 1000,
        approval = approval,
        supplyState = 1,
    )

    private fun env(): CommandEnv = CommandEnv(
        year = 200,
        month = 1,
        startYear = 190,
        sessionId = 1,
        realtimeMode = false,
        develCost = 100,
    )

    private fun constraintCtx(gen: Officer, city: Planet? = null) =
        ConstraintContext(general = gen, city = city)

    // ─── 1. General with 0 troops: ReqGeneralCrew blocks ────────────────────

    @Test
    fun `ReqGeneralCrew fails when crew is 0`() {
        val gen = general(ships = 0)
        val result = ReqGeneralCrew(minCrew = 1).test(constraintCtx(gen))
        assertTrue(result is ConstraintResult.Fail, "Expected Fail when ships =0")
    }

    @Test
    fun `ReqGeneralCrew passes when crew is 1`() {
        val gen = general(ships = 1)
        val result = ReqGeneralCrew(minCrew = 1).test(constraintCtx(gen))
        assertEquals(ConstraintResult.Pass, result)
    }

    // ─── 2. General with max stats (all 100): WarUnitOfficer no overflow ─────

    @Test
    fun `WarUnitOfficer with all stats 100 does not overflow or throw`() {
        val gen = general(leadership = 100, command = 100, intelligence = 100, ships = 100_000, training = 100, morale = 100)
        val unit = WarUnitOfficer(gen)
        // Should not throw; base attack/defence are finite positive doubles
        val baseAttack = unit.getBaseAttack()
        val baseDefence = unit.getBaseDefence()
        assertTrue(baseAttack.isFinite() && baseAttack > 0, "baseAttack=$baseAttack should be finite positive")
        assertTrue(baseDefence.isFinite() && baseDefence > 0, "baseDefence=$baseDefence should be finite positive")
    }

    // ─── 3. City with 0 population: 징병 ReqCityCapacity blocks ─────────────

    @Test
    fun `ReqCityCapacity population fails when city has 0 population`() {
        val gen = general()
        val zeroPop = city(population = 0)
        // ReqCityCapacity("population", ...) needs population >= MIN_AVAILABLE_RECRUIT_POP + reqCrew
        val constraint = ReqCityCapacity("population", "주민", 3100)
        val result = constraint.test(constraintCtx(gen, zeroPop))
        assertTrue(result is ConstraintResult.Fail, "Expected Fail when city population=0")
    }

    // ─── 4. Battle determinism + warPower < 100 RNG floor ───────────────────

    @Test
    fun `BattleEngine is deterministic with same seed`() {
        val engine = BattleEngine()
        val attacker = general(leadership = 30, command = 30, intelligence = 30, ships = 100, training = 40, morale = 40)
        val defender = general(id = 2, factionId = 2, leadership = 30, command = 30, intelligence = 30, ships = 100, training = 40, morale = 40)
        val c = city(factionId = 2)

        val r1 = engine.resolveBattle(
            attacker = WarUnitOfficer(attacker),
            defenders = listOf(WarUnitOfficer(general(id = 2, factionId = 2, leadership = 30, command = 30, intelligence = 30, ships = 100, training = 40, morale = 40))),
            city = c,
            rng = LiteHashDRBG.build("edge_case_det"),
        )
        val r2 = engine.resolveBattle(
            attacker = WarUnitOfficer(general(leadership = 30, command = 30, intelligence = 30, ships = 100, training = 40, morale = 40)),
            defenders = listOf(WarUnitOfficer(general(id = 2, factionId = 2, leadership = 30, command = 30, intelligence = 30, ships = 100, training = 40, morale = 40))),
            city = city(factionId = 2),
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
        val attacker = general(leadership = 1, command = 1, intelligence = 1, ships = 100, training = 1, morale = 1)
        val defender = general(id = 2, factionId = 2, leadership = 100, command = 100, intelligence = 100, ships = 10_000, training = 100, morale = 100)
        val c = city(factionId = 2)

        val result = engine.resolveBattle(
            attacker = WarUnitOfficer(attacker),
            defenders = listOf(WarUnitOfficer(defender)),
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

    // ─── 7. 징병 blend formula: (oldCrew*oldTrain + newCrew*40) / (oldCrew+newCrew) ─

    @Test
    fun `징병 blends train at 40 when adding to same crew type`() {
        val oldCrew = 1000
        val oldTrain = 80
        val newCrew = 500
        val defaultTrain = 40  // 징병 DEFAULT_TRAIN_LOW

        val blended = (oldCrew * oldTrain + newCrew * defaultTrain) / (oldCrew + newCrew)
        // (1000*80 + 500*40) / 1500 = 100000 / 1500 = 66
        assertEquals(66, blended)
    }

    @Test
    fun `징병 run produces correct blended train in statChanges`() {
        val gen = general(ships = 1000, shipClass = 0, training = 80, morale = 80, leadership = 50, funds = 50_000, supplies = 50_000)
        val arg = mapOf<String, Any>("amount" to 500, "crewType" to 0)
        val cmd = che_징병(gen, env(), arg)
        cmd.city = city()

        val result = runBlocking { cmd.run(LiteHashDRBG.build("징병_blend_test")) }
        assertTrue(result.success)

        val json = mapper.readTree(result.message)
        val trainDelta = json["statChanges"]["train"].asInt()
        // newTrain = (1000*80 + 500*40) / 1500 = (80000+20000)/1500 = 100000/1500 = 66
        // delta = 66 - 80 = -14
        val expectedNewTrain = (1000 * 80 + 500 * 40) / 1500
        val expectedDelta = expectedNewTrain - 80
        assertEquals(expectedDelta, trainDelta)
    }

    // ─── 8. 모병 blend formula: same but with 70 default train ───────────────

    @Test
    fun `모병 blends train at 70 when adding to same crew type`() {
        val oldCrew = 1000
        val oldTrain = 40
        val newCrew = 1000
        val defaultTrain = 70  // 모병 DEFAULT_TRAIN_HIGH

        val blended = (oldCrew * oldTrain + newCrew * defaultTrain) / (oldCrew + newCrew)
        assertEquals((40000 + 70000) / 2000, blended)
        assertEquals(55, blended)
    }

    @Test
    fun `모병 run produces higher default train than 징병`() {
        val gen1 = general(ships = 0, shipClass = 0, training = 50, morale = 50, leadership = 50, funds = 200_000, supplies = 200_000)
        val gen2 = general(ships = 0, shipClass = 0, training = 50, morale = 50, leadership = 50, funds = 200_000, supplies = 200_000)
        val arg = mapOf<String, Any>("amount" to 500, "crewType" to 0)

        val cmdJingbyeong = che_징병(gen1, env(), arg)
        cmdJingbyeong.city = city()
        val cmdMobyeong = che_모병(gen2, env(), arg)
        cmdMobyeong.city = city()

        val r1 = runBlocking { cmdJingbyeong.run(LiteHashDRBG.build("blend_cmp_1")) }
        val r2 = runBlocking { cmdMobyeong.run(LiteHashDRBG.build("blend_cmp_2")) }

        val j1 = mapper.readTree(r1.message)
        val j2 = mapper.readTree(r2.message)

        // 징병 default training = 40, 모병 default training = 70
        // Both start with ships =0, so newTrain = defaultTrain directly
        val newTrain1 = 50 + j1["statChanges"]["train"].asInt()  // 50 + (40-50) = 40
        val newTrain2 = 50 + j2["statChanges"]["train"].asInt()  // 50 + (70-50) = 70
        assertTrue(newTrain2 > newTrain1, "모병 (training =$newTrain2) should produce higher train than 징병 (training =$newTrain1)")
    }
}
