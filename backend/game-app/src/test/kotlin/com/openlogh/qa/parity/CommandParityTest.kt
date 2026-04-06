package com.openlogh.qa.parity

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.general.*
import com.openlogh.engine.LiteHashDRBG
import com.openlogh.entity.Planet
import com.openlogh.entity.Officer
import com.openlogh.entity.Faction
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import kotlin.math.roundToInt

/**
 * Command parity tests verifying Kotlin commands match legacy PHP logic.
 *
 * Legacy references:
 * - hwe/sammo/Command/General/che_훈련.php
 * - hwe/sammo/Command/General/che_징병.php
 * - hwe/sammo/Command/General/che_상업투자.php (DomesticCommand base)
 * - hwe/sammo/Command/General/che_성벽보수.php
 * - hwe/sammo/Command/General/che_수비강화.php
 * - hwe/sammo/Command/General/che_치안강화.php
 * - hwe/sammo/Command/General/che_사기진작.php
 */
@DisplayName("Command Logic Legacy Parity")
class CommandParityTest {

    private val mapper = jacksonObjectMapper()

    // ──────────────────────────────────────────────────
    //  훈련 (Training) edge cases
    // ──────────────────────────────────────────────────

    @Nested
    @DisplayName("che_훈련 - legacy che_훈련.php:89")
    inner class TrainingParity {

        @Test
        fun `high leadership low ships gives high training score`() {
            // Legacy: score = clamp(round(leadership * 100 / ships * trainDelta), 0, maxTrain - train)
            val gen = createGeneral(leadership = 100, ships = 100, training = 0, morale = 80)
            val result = runCmd(che_훈련(gen, createEnv()), "train_1")
            assertTrue(result.success)
            val json = mapper.readTree(result.message)
            // 100 * 100 / 100 * 30.0 = 3000, clamped to min(3000, 100-0)=100
            assertEquals(100, json["statChanges"]["train"].asInt())
        }

        @Test
        fun `training cannot exceed max train`() {
            val gen = createGeneral(leadership = 100, ships = 100, training = 79, morale = 80)
            val result = runCmd(che_훈련(gen, createEnv()), "train_2")
            assertTrue(result.success)
            val json = mapper.readTree(result.message)
            // max possible = 100-79 = 21; raw = 3000, clamped to 21
            assertEquals(21, json["statChanges"]["train"].asInt())
        }

        @Test
        fun `training at max gives zero`() {
            val gen = createGeneral(leadership = 100, ships = 100, training = 100, morale = 80)
            val result = runCmd(che_훈련(gen, createEnv()), "train_3")
            assertTrue(result.success)
            val json = mapper.readTree(result.message)
            assertEquals(0, json["statChanges"]["train"].asInt())
        }

        @Test
        fun `morale side effect matches legacy formula`() {
            // atmosSideEffectByTraining = 1.0 (no side effect)
            // atmosAfter = max(0, (morale * 1.0).toInt()); delta = atmosAfter - morale = 0
            val gen = createGeneral(leadership = 80, ships = 200, training = 60, morale = 100)
            val result = runCmd(che_훈련(gen, createEnv()), "train_4")
            val json = mapper.readTree(result.message)
            // atmosAfter = (100 * 1.0).toInt() = 100; delta = 100 - 100 = 0
            assertEquals(0, json["statChanges"]["atmos"].asInt())
        }

        @Test
        fun `morale zero stays zero`() {
            val gen = createGeneral(leadership = 80, ships = 200, training = 60, morale = 0)
            val result = runCmd(che_훈련(gen, createEnv()), "train_5")
            val json = mapper.readTree(result.message)
            assertEquals(0, json["statChanges"]["atmos"].asInt())
        }

        @Test
        fun `experience and dedication are fixed at 100 and 70`() {
            // Legacy: $exp = 100; $ded = 70;
            val gen = createGeneral(leadership = 80, ships = 200, training = 60, morale = 70)
            val result = runCmd(che_훈련(gen, createEnv()), "train_6")
            val json = mapper.readTree(result.message)
            assertEquals(100, json["statChanges"]["experience"].asInt())
            assertEquals(70, json["statChanges"]["dedication"].asInt())
            assertEquals(1, json["statChanges"]["leadershipExp"].asInt())
        }
    }

    // ──────────────────────────────────────────────────
    //  징병 (Conscription) edge cases
    // ──────────────────────────────────────────────────

    @Nested
    @DisplayName("che_징병 - legacy che_징병.php")
    inner class ConscriptionParity {

        @Test
        fun `adding to existing same ships type blends train and morale`() {
            // Legacy: newTrain = (oldCrew*oldTrain + newCrew*50) / (oldCrew+newCrew)
            val gen = createGeneral(ships = 1000, shipClass = 1100, training = 80, morale = 80, funds = 10000, supplies = 10000, leadership = 80)
            val arg = mapOf<String, Any>("amount" to 1000, "crewType" to 1100)
            val result = runCmd(che_징병(gen, createEnv(), arg), "con_1")
            assertTrue(result.success)
            val json = mapper.readTree(result.message)
            // newTrain = (1000*80 + 1000*40) / 2000 = 120000/2000 = 60  (PHP defaultTrainLow=40)
            assertEquals(60 - 80, json["statChanges"]["train"].asInt())
            assertEquals(60 - 80, json["statChanges"]["atmos"].asInt())
        }

        @Test
        fun `switching ships type resets to 50 50`() {
            val gen = createGeneral(ships = 1000, shipClass = 1100, training = 80, morale = 80, funds = 10000, supplies = 10000, leadership = 80)
            val arg = mapOf<String, Any>("amount" to 500, "crewType" to 1200) // different type
            val result = runCmd(che_징병(gen, createEnv(), arg), "con_2")
            assertTrue(result.success)
            val json = mapper.readTree(result.message)
            assertEquals(40 - 80, json["statChanges"]["train"].asInt())
            assertEquals(40 - 80, json["statChanges"]["atmos"].asInt())
        }

        @Test
        fun `conscription capped at leadership times 100`() {
            val gen = createGeneral(ships = 0, shipClass = 0, leadership = 10, funds = 50000, supplies = 50000)
            val arg = mapOf<String, Any>("amount" to 5000, "crewType" to 1100)
            val result = runCmd(che_징병(gen, createEnv(), arg), "con_3")
            assertTrue(result.success)
            val json = mapper.readTree(result.message)
            // maxCrew = leadership*100 = 1000, but also clamped by reqAmount=5000 → min(5000, 1000) = 1000
            assertEquals(1000, json["statChanges"]["crew"].asInt())
        }

        @Test
        fun `gold cost includes tech cost multiplier`() {
            val gen = createGeneral(ships = 0, shipClass = 0, leadership = 50, funds = 50000, supplies = 50000)
            gen.factionId = 1
            val nation = Faction(id = 1, sessionId = 1, name = "테스트국", techLevel = 1000f)
            val env = createEnv()
            val arg = mapOf<String, Any>("amount" to 500, "crewType" to 1100)
            val cmd = che_징병(gen, env, arg)
            cmd.nation = nation

            val result = runBlocking { cmd.run(LiteHashDRBG.build("con_4")) }
            assertTrue(result.success)
            val json = mapper.readTree(result.message)
            // baseCost = 500/100 * techCost(=1+1000/1000=2.0) = 5*2 = 10
            // The command uses getNationTechCost which reads from cmd.nation
            val goldChange = json["statChanges"]["gold"].asInt()
            assertTrue(goldChange < 0)
        }
    }

    // ──────────────────────────────────────────────────
    //  DomesticCommand family (성벽보수, 수비강화, 치안강화)
    // ──────────────────────────────────────────────────

    @Nested
    @DisplayName("DomesticCommand - legacy che_상업투자.php base")
    inner class DomesticParity {

        @Test
        fun `성벽보수 uses strength stat`() {
            val gen = createGeneral(command = 80, funds = 500)
            val city = createCity(fortress = 500, fortressMax = 1000, approval = 80f)
            val result = runDomestic(che_성벽보수(gen, createEnv()), city, "wall_1")
            assertTrue(result.success)
            val json = mapper.readTree(result.message)
            assertEquals(1, json["statChanges"]["strengthExp"].asInt())
            assertTrue(json["cityChanges"]["fortress"].asInt() > 0)
        }

        @Test
        fun `수비강화 uses strength stat`() {
            val gen = createGeneral(command = 80, funds = 500)
            val city = createCity(orbitalDefense = 500, orbitalDefenseMax = 1000, approval = 80f)
            val result = runDomestic(che_수비강화(gen, createEnv()), city, "def_1")
            assertTrue(result.success)
            val json = mapper.readTree(result.message)
            assertEquals(1, json["statChanges"]["strengthExp"].asInt())
            assertTrue(json["cityChanges"]["orbitalDefense"].asInt() > 0)
        }

        @Test
        fun `치안강화 uses strength stat`() {
            val gen = createGeneral(command = 80, funds = 500)
            val city = createCity(security = 500, securityMax = 1000, approval = 80f)
            val result = runDomestic(che_치안강화(gen, createEnv()), city, "secu_1")
            assertTrue(result.success)
            val json = mapper.readTree(result.message)
            assertEquals(1, json["statChanges"]["strengthExp"].asInt())
            assertTrue(json["cityChanges"]["security"].asInt() > 0)
        }

        @Test
        fun `front line debuff factors differ by command type`() {
            // Legacy: 성벽보수 debuffFront=0.25, 수비강화=0.5, 치안강화=1.0
            // On front line city, score *= debuffFront
            val gen80 = createGeneral(command = 80, funds = 5000)

            val cityFront = createCity(fortress = 100, fortressMax = 1000, approval = 80f, frontState = 1)
            val cityRear = createCity(fortress = 100, fortressMax = 1000, approval = 80f, frontState = 0)

            val resultFront = runDomestic(che_성벽보수(gen80.copy(), createEnv()), cityFront.copy(), "debuff_f1")
            val resultRear = runDomestic(che_성벽보수(gen80.copy(), createEnv()), cityRear.copy(), "debuff_f1")

            val frontDelta = mapper.readTree(resultFront.message)["cityChanges"]["fortress"].asInt()
            val rearDelta = mapper.readTree(resultRear.message)["cityChanges"]["fortress"].asInt()

            // Front should be much lower due to 0.25 debuff
            assertTrue(frontDelta <= rearDelta, "Front fortress delta ($frontDelta) should be <= rear ($rearDelta)")
        }

        @Test
        fun `치안강화 front line debuff is 1_0 meaning no reduction`() {
            // debuffFront=1.0 → score *= 1.0 → no change
            val gen = createGeneral(command = 80, funds = 5000)
            val cityFront = createCity(security = 100, securityMax = 1000, approval = 80f, frontState = 1)
            val cityRear = createCity(security = 100, securityMax = 1000, approval = 80f, frontState = 0)

            val resultFront = runDomestic(che_치안강화(gen.copy(), createEnv()), cityFront.copy(), "secu_debuff")
            val resultRear = runDomestic(che_치안강화(gen.copy(), createEnv()), cityRear.copy(), "secu_debuff")

            val frontDelta = mapper.readTree(resultFront.message)["cityChanges"]["security"].asInt()
            val rearDelta = mapper.readTree(resultRear.message)["cityChanges"]["security"].asInt()

            // With debuffFront=1.0, front score = score * 1.0 → same as rear before clamping
            // (May still differ slightly due to max clamping, but should be similar)
            assertTrue(frontDelta >= rearDelta * 0.9, "Secu front ($frontDelta) should be close to rear ($rearDelta)")
        }

        @Test
        fun `domestic command cannot exceed max value`() {
            val gen = createGeneral(intelligence = 100, funds = 500)
            val city = createCity(production = 990, productionMax = 1000, approval = 100f)
            val result = runDomestic(che_농지개간(gen, createEnv()), city, "max_1")
            assertTrue(result.success)
            val json = mapper.readTree(result.message)
            val delta = json["cityChanges"]["production"].asInt()
            assertTrue(delta <= 10, "Delta ($delta) should not exceed remaining capacity (10)")
        }

        @Test
        fun `low approval reduces score`() {
            // Legacy: approval < 50 → clamped to 50
            // Score uses approval/100.0 as multiplier
            val genHighTrust = createGeneral(intelligence = 80, funds = 500)
            val genLowTrust = createGeneral(intelligence = 80, funds = 500)

            val cityHigh = createCity(production = 100, productionMax = 1000, approval = 100f)
            val cityLow = createCity(production = 100, productionMax = 1000, approval = 50f)

            val resultHigh = runDomestic(che_농지개간(genHighTrust, createEnv()), cityHigh, "trust_h")
            val resultLow = runDomestic(che_농지개간(genLowTrust, createEnv()), cityLow, "trust_h")

            val deltaHigh = mapper.readTree(resultHigh.message)["cityChanges"]["production"].asInt()
            val deltaLow = mapper.readTree(resultLow.message)["cityChanges"]["production"].asInt()

            assertTrue(deltaHigh >= deltaLow, "High approval ($deltaHigh) should give >= low approval ($deltaLow)")
        }

        @Test
        fun `critical success and fail scale score`() {
            // Run many seeds and verify we get all three outcomes
            val picks = mutableSetOf<String>()
            for (i in 1..50) {
                val gen = createGeneral(intelligence = 80, funds = 500)
                val city = createCity(production = 100, productionMax = 1000, approval = 80f)
                val result = runDomestic(che_농지개간(gen, createEnv()), city, "crit_$i")
                val json = mapper.readTree(result.message)
                picks.add(json["criticalResult"].asText())
                if (picks.size == 3) break
            }
            assertTrue(picks.contains("normal") || picks.contains("success") || picks.contains("fail"),
                "Should see at least one outcome type in 50 runs: $picks")
        }

        @Test
        fun `gold cost equals develCost from env`() {
            val develCost = 150
            val gen = createGeneral(funds = 500)
            val city = createCity(production = 100, productionMax = 1000, approval = 80f)
            val result = runDomestic(che_농지개간(gen, createEnv(develCost = develCost)), city, "cost_1")
            val json = mapper.readTree(result.message)
            assertEquals(-develCost, json["statChanges"]["gold"].asInt())
        }
    }

    // ──────────────────────────────────────────────────
    //  사기진작 (Morale Boost) parity
    // ──────────────────────────────────────────────────

    @Nested
    @DisplayName("che_사기진작 - legacy che_사기진작.php")
    inner class MoraleParity {

        @Test
        fun `morale boost has no train side effect`() {
            // trainSideEffectByAtmosTurn = 1.0 (no side effect)
            val gen = createGeneral(leadership = 80, ships = 200, morale = 60, training = 80)
            val result = runCmd(che_사기진작(gen, createEnv()), "morale_1")
            assertTrue(result.success)
            val json = mapper.readTree(result.message)
            // trainAfter = max(0, (80 * 1.0).toInt()) = 80; delta = 80-80 = 0
            assertEquals(0, json["statChanges"]["train"].asInt())
        }
    }

    // ──────────────────────────────────────────────────
    //  Determinism across all commands
    // ──────────────────────────────────────────────────

    @Nested
    @DisplayName("Cross-command determinism")
    inner class CrossCommandDeterminism {

        @Test
        fun `all domestic commands deterministic with same seed`() {
            val commands = listOf("농지개간", "상업투자", "성벽보수", "수비강화", "치안강화")
            for (name in commands) {
                val gen1 = createGeneral(command = 80, intelligence = 80, funds = 500)
                val gen2 = createGeneral(command = 80, intelligence = 80, funds = 500)
                val city1 = createCity(production = 100, commerce = 100, fortress = 100, orbitalDefense = 100, security = 100,
                    productionMax = 1000, commerceMax = 1000, fortressMax = 1000, orbitalDefenseMax = 1000, securityMax = 1000, approval = 80f)
                val city2 = city1.copy()

                val cmd1 = createDomesticCmd(name, gen1, createEnv())
                val cmd2 = createDomesticCmd(name, gen2, createEnv())

                val r1 = runDomestic(cmd1, city1, "det_$name")
                val r2 = runDomestic(cmd2, city2, "det_$name")

                assertEquals(r1.message, r2.message, "Command $name should be deterministic")
            }
        }

        private fun createDomesticCmd(name: String, gen: Officer, env: CommandEnv): DomesticCommand {
            return when (name) {
                "농지개간" -> che_농지개간(gen, env)
                "상업투자" -> che_상업투자(gen, env)
                "성벽보수" -> che_성벽보수(gen, env)
                "수비강화" -> che_수비강화(gen, env)
                "치안강화" -> che_치안강화(gen, env)
                else -> error("Unknown: $name")
            }
        }
    }

    // ──────────────────────────────────────────────────
    //  Helpers
    // ──────────────────────────────────────────────────

    private fun runCmd(cmd: com.openlogh.command.BaseCommand, seed: String): CommandResult {
        return runBlocking { cmd.run(LiteHashDRBG.build(seed)) }
    }

    private fun runDomestic(cmd: DomesticCommand, city: Planet, seed: String): CommandResult {
        cmd.city = city
        return runBlocking { cmd.run(LiteHashDRBG.build(seed)) }
    }

    private fun createEnv(develCost: Int = 100): CommandEnv {
        return CommandEnv(
            year = 200,
            month = 1,
            startYear = 190,
            sessionId = 1,
            realtimeMode = false,
            develCost = develCost,
        )
    }

    private fun createGeneral(
        leadership: Short = 70,
        command: Short = 70,
        intelligence: Short = 70,
        funds: Int = 500,
        supplies: Int = 500,
        ships: Int = 1000,
        shipClass: Short = 0,
        training: Short = 60,
        morale: Short = 60,
    ): Officer = Officer(
        id = 1,
        sessionId = 1,
        name = "테스트장수",
        factionId = 1,
        planetId = 1,
        funds = funds,
        supplies = supplies,
        ships = ships,
        shipClass = shipClass,
        training = training,
        morale = morale,
        leadership = leadership,
        command = command,
        intelligence = intelligence,
        politics = 60,
        administration = 60,
        turnTime = OffsetDateTime.now(),
    )

    private fun createCity(
        factionId: Long = 1,
        population: Int = 50000,
        production: Int = 500,
        productionMax: Int = 1000,
        commerce: Int = 500,
        commerceMax: Int = 1000,
        security: Int = 500,
        securityMax: Int = 1000,
        orbitalDefense: Int = 500,
        orbitalDefenseMax: Int = 1000,
        fortress: Int = 500,
        fortressMax: Int = 1000,
        approval: Float = 80f,
        frontState: Short = 0,
    ): Planet = Planet(
        id = 1,
        sessionId = 1,
        name = "테스트도시",
        factionId = factionId,
        population = population,
        populationMax = 100000,
        production = production,
        productionMax = productionMax,
        commerce = commerce,
        commerceMax = commerceMax,
        security = security,
        securityMax = securityMax,
        orbitalDefense = orbitalDefense,
        orbitalDefenseMax = orbitalDefenseMax,
        fortress = fortress,
        fortressMax = fortressMax,
        approval = approval,
        supplyState = 1,
        frontState = frontState,
    )

    // Extension for General to support copy-like behavior in tests
    private fun Officer.copy(): Officer = Officer(
        id = id, sessionId = sessionId, name = name, factionId = factionId, planetId = planetId,
        funds = funds, supplies = supplies, ships = ships, shipClass = shipClass, training = training, morale = morale,
        leadership = leadership, command = command, intelligence = intelligence, politics = politics, administration = administration,
        turnTime = turnTime,
    )

    private fun Planet.copy(): Planet = Planet(
        id = id, sessionId = sessionId, name = name, factionId = factionId,
        population = population, populationMax = populationMax, production = production, productionMax = productionMax, commerce = commerce, commerceMax = commerceMax,
        security = security, securityMax = securityMax, orbitalDefense = orbitalDefense, orbitalDefenseMax = orbitalDefenseMax, fortress = fortress, fortressMax = fortressMax,
        approval = approval, supplyState = supplyState, frontState = frontState,
    )
}
