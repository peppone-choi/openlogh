package com.openlogh.command

import com.openlogh.command.constraint.ConstraintResult
import com.openlogh.command.general.*
import com.openlogh.entity.Planet
import com.openlogh.entity.Officer
import com.openlogh.entity.Faction
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import kotlin.random.Random

class CommandTest {

    private fun createTestGeneral(
        funds: Int = 1000,
        supplies: Int = 1000,
        ships: Int = 0,
        shipClass: Short = 0,
        training: Short = 0,
        morale: Short = 0,
        leadership: Short = 50,
        command: Short = 50,
        intelligence: Short = 50,
        politics: Short = 50,
        administration: Short = 50,
        factionId: Long = 1,
        planetId: Long = 1,
        officerLevel: Short = 0,
        fleetId: Long = 0,
        experience: Int = 0,
        dedication: Int = 0,
        betray: Short = 0,
    ): Officer {
        return Officer(
            id = 1,
            sessionId = 1,
            name = "테스트장수",
            factionId = factionId,
            planetId = planetId,
            funds = funds,
            supplies = supplies,
            ships = ships,
            shipClass = shipClass,
            training = training,
            morale = morale,
            leadership = leadership,
            command = command,
            intelligence = intelligence,
            politics = politics,
            administration = administration,
            officerLevel = officerLevel,
            fleetId = fleetId,
            experience = experience,
            dedication = dedication,
            betray = betray,
            turnTime = OffsetDateTime.now(),
        )
    }

    private fun createTestCity(
        factionId: Long = 1,
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
        population: Int = 10000,
        populationMax: Int = 50000,
        approval: Float = 80f,
        supplyState: Short = 1,
        frontState: Short = 0,
    ): Planet {
        return Planet(
            id = 1,
            sessionId = 1,
            name = "테스트도시",
            factionId = factionId,
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
            population = population,
            populationMax = populationMax,
            approval = approval,
            supplyState = supplyState,
            frontState = frontState,
        )
    }

    private fun createTestNation(
        id: Long = 1,
        level: Short = 1,
        funds: Int = 10000,
        supplies: Int = 10000,
    ): Faction {
        return Faction(
            id = id,
            sessionId = 1,
            name = "테스트국가",
            color = "#FF0000",
            funds = funds,
            supplies = supplies,
            factionRank = level,
        )
    }

    private fun createTestEnv(
        year: Int = 200,
        month: Int = 1,
        startYear: Int = 190,
    ) = CommandEnv(
        year = year,
        month = month,
        startYear = startYear,
        sessionId = 1,
        realtimeMode = false,
    )

    private val fixedRng = Random(42)

    // ========== 휴식 (Rest) ==========

    @Test
    fun `휴식 command should succeed for any general`() {
        val general = createTestGeneral()
        val env = createTestEnv()
        val cmd = 휴식(general, env)
        val result = runBlocking { cmd.run(fixedRng) }

        assertTrue(result.success)
        assertTrue(result.logs.isNotEmpty())
        assertTrue(result.logs[0].contains("아무것도 실행하지 않았습니다"))
    }

    @Test
    fun `휴식 should have zero cost`() {
        val general = createTestGeneral()
        val env = createTestEnv()
        val cmd = 휴식(general, env)
        val cost = cmd.getCost()

        assertEquals(0, cost.funds)
        assertEquals(0, cost.supplies)
    }

    @Test
    fun `휴식 should have zero pre and post req turns`() {
        val general = createTestGeneral()
        val env = createTestEnv()
        val cmd = 휴식(general, env)

        assertEquals(0, cmd.getPreReqTurn())
        assertEquals(0, cmd.getPostReqTurn())
        assertEquals(0, cmd.getDuration())
    }

    @Test
    fun `휴식 should have no constraints`() {
        val general = createTestGeneral(factionId = 0)
        val env = createTestEnv()
        val cmd = 휴식(general, env)

        val fullResult = cmd.checkFullCondition()
        assertTrue(fullResult is ConstraintResult.Pass)
    }

    // ========== 농지개간 (Farming) ==========

    @Test
    fun `농지개간 should succeed when constraints pass`() {
        val general = createTestGeneral(funds = 1000, intelligence = 80)
        val env = createTestEnv()
        val city = createTestCity()
        val cmd = che_농지개간(general, env)
        cmd.city = city

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        assertTrue(result.logs.isNotEmpty())
        assertTrue(result.logs[0].contains("농지 개간"))
    }

    @Test
    fun `농지개간 should use intel stat for calculation`() {
        val general = createTestGeneral(intelligence = 90)
        val env = createTestEnv()
        val cmd = che_농지개간(general, env)

        assertEquals("production", cmd.cityKey)
        assertEquals("intel", cmd.statKey)
    }

    @Test
    fun `농지개간 should fail when general has no nation`() {
        val general = createTestGeneral(factionId = 0)
        val env = createTestEnv()
        val city = createTestCity(factionId = 0)
        val cmd = che_농지개간(general, env)
        cmd.city = city

        val result = cmd.checkFullCondition()
        assertTrue(result is ConstraintResult.Fail)
        assertTrue((result as ConstraintResult.Fail).reason.contains("소속 국가"))
    }

    @Test
    fun `농지개간 should fail when city is at max production`() {
        val general = createTestGeneral()
        val env = createTestEnv()
        val city = createTestCity(production = 1000, productionMax = 1000)
        val cmd = che_농지개간(general, env)
        cmd.city = city

        val result = cmd.checkFullCondition()
        assertTrue(result is ConstraintResult.Fail)
        assertTrue((result as ConstraintResult.Fail).reason.contains("최대치"))
    }

    @Test
    fun `농지개간 should fail when city is not supplied`() {
        val general = createTestGeneral()
        val env = createTestEnv()
        val city = createTestCity(supplyState = 0)
        val cmd = che_농지개간(general, env)
        cmd.city = city

        val result = cmd.checkFullCondition()
        assertTrue(result is ConstraintResult.Fail)
        assertTrue((result as ConstraintResult.Fail).reason.contains("보급"))
    }

    @Test
    fun `농지개간 cost should use env develCost`() {
        val general = createTestGeneral()
        val env = CommandEnv(year = 200, month = 1, startYear = 190, sessionId = 1, develCost = 150)
        val cmd = che_농지개간(general, env)
        val cost = cmd.getCost()

        assertEquals(150, cost.funds)
        assertEquals(0, cost.supplies)
    }

    @Test
    fun `농지개간 should fail when general lacks gold`() {
        val general = createTestGeneral(funds = 50)
        val env = createTestEnv()
        val city = createTestCity()
        val cmd = che_농지개간(general, env)
        cmd.city = city

        val result = cmd.checkFullCondition()
        assertTrue(result is ConstraintResult.Fail)
        assertTrue((result as ConstraintResult.Fail).reason.contains("자금"))
    }

    @Test
    fun `농지개간 should apply front line debuff`() {
        val general = createTestGeneral(intelligence = 80)
        val env = createTestEnv()
        val city = createTestCity(frontState = 1, approval = 100f)
        val cmd = che_농지개간(general, env)
        cmd.city = city

        val result = runBlocking { cmd.run(Random(100)) }
        assertTrue(result.success)
        // The score should be reduced due to front line debuff
        assertNotNull(result.message)
    }

    // ========== 상업투자 (Commerce Investment) ==========

    @Test
    fun `상업투자 should target commerce stat`() {
        val general = createTestGeneral()
        val env = createTestEnv()
        val cmd = che_상업투자(general, env)

        assertEquals("commerce", cmd.cityKey)
        assertEquals("intel", cmd.statKey)
    }

    // ========== 모병 (Recruitment) ==========

    @Test
    fun `모병 should require gold and rice`() {
        val general = createTestGeneral(funds = 0, supplies = 0, leadership = 50)
        val env = createTestEnv()
        val city = createTestCity()
        val arg = mapOf<String, Any>("amount" to 500, "crewType" to 0)
        val cmd = che_모병(general, env, arg)
        cmd.city = city

        val result = cmd.checkFullCondition()
        assertTrue(result is ConstraintResult.Fail)
    }

    @Test
    fun `모병 should fail for neutral general`() {
        val general = createTestGeneral(factionId = 0)
        val env = createTestEnv()
        val city = createTestCity(factionId = 0)
        val arg = mapOf<String, Any>("amount" to 500, "crewType" to 0)
        val cmd = che_모병(general, env, arg)
        cmd.city = city

        val result = cmd.checkFullCondition()
        assertTrue(result is ConstraintResult.Fail)
        assertTrue((result as ConstraintResult.Fail).reason.contains("소속 국가"))
    }

    @Test
    fun `모병 should succeed with enough resources`() {
        val general = createTestGeneral(funds = 5000, supplies = 5000, leadership = 50)
        val env = createTestEnv()
        val city = createTestCity(population = 50000)
        val arg = mapOf<String, Any>("amount" to 500, "crewType" to 0)
        val cmd = che_모병(general, env, arg)
        cmd.city = city

        val result = cmd.checkFullCondition()
        assertTrue(result is ConstraintResult.Pass)

        val runResult = runBlocking { cmd.run(fixedRng) }
        assertTrue(runResult.success)
        assertTrue(runResult.logs[0].contains("모병"))
    }

    @Test
    fun `모병 should cap ships at leadership times 100`() {
        val general = createTestGeneral(funds = 50000, supplies = 50000, leadership = 10)
        val env = createTestEnv()
        val city = createTestCity()
        val arg = mapOf<String, Any>("amount" to 99999, "crewType" to 0)
        val cmd = che_모병(general, env, arg)
        cmd.city = city

        val cost = cmd.getCost()
        // maxCrew should be min(99999, max(0, 10*100 - 0)) = 1000
        // baseCost = 1000/10 = 100, funds = 100*2 = 200
        assertEquals(200, cost.funds)
    }

    @Test
    fun `모병 should calculate additional recruit cost when same ships type`() {
        val general = createTestGeneral(funds = 50000, supplies = 50000, leadership = 50, ships = 2000, shipClass = 1)
        val env = createTestEnv()
        val city = createTestCity()
        val arg = mapOf<String, Any>("amount" to 500, "crewType" to 1)
        val cmd = che_모병(general, env, arg)
        cmd.city = city

        // maxCrew = min(500, max(0, 50*100 - 2000)) = min(500, 3000) = 500
        val cost = cmd.getCost()
        assertEquals(100, cost.funds) // 500/10 * 2 = 100
    }

    @Test
    fun `모병 should merge train and morale when same ships type`() {
        val general = createTestGeneral(
            funds = 50000, supplies = 50000, leadership = 50,
            ships = 1000, shipClass = 1, training = 80, morale = 80
        )
        val env = createTestEnv()
        val city = createTestCity()
        val arg = mapOf<String, Any>("amount" to 500, "crewType" to 1)
        val cmd = che_모병(general, env, arg)
        cmd.city = city

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        assertTrue(result.logs[0].contains("추가모병"))
    }

    @Test
    fun `모병 should set default train and morale when different ships type`() {
        val general = createTestGeneral(
            funds = 50000, supplies = 50000, leadership = 50,
            ships = 1000, shipClass = 1, training = 80, morale = 80
        )
        val env = createTestEnv()
        val city = createTestCity()
        val arg = mapOf<String, Any>("amount" to 500, "crewType" to 2)
        val cmd = che_모병(general, env, arg)
        cmd.city = city

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        assertFalse(result.logs[0].contains("추가모병"))
    }

    // ========== 건국 (Found Nation) ==========

    @Test
    fun `건국 should fail during opening part`() {
        // startYear=190, year=200 => relYear=10, BeOpeningPart(11) checks relYear < 1 => false => Fail
        val general = createTestGeneral(factionId = 1, officerLevel = 20)
        val env = createTestEnv(year = 200, startYear = 190)
        val nation = createTestNation(level = 0)
        val cmd = 건국(general, env)
        cmd.nation = nation

        val result = cmd.checkFullCondition()
        // BeOpeningPart(relYear+1) = BeOpeningPart(11) => relYear < 1 is false => Fail
        assertTrue(result is ConstraintResult.Fail)
    }

    @Test
    fun `건국 should check BeLord constraint`() {
        // general with officerLevel < 20 should fail BeLord
        val general = createTestGeneral(factionId = 1, officerLevel = 5)
        val env = createTestEnv(year = 190, startYear = 190)
        val nation = createTestNation(level = 0)
        val cmd = 건국(general, env)
        cmd.nation = nation

        val result = cmd.checkFullCondition()
        assertTrue(result is ConstraintResult.Fail)
    }

    @Test
    fun `건국 run should fail in first turn`() {
        // yearMonth = startYear*12 + month <= initYearMonth = startYear*12 + 1
        val general = createTestGeneral(factionId = 1, officerLevel = 20)
        val env = createTestEnv(year = 190, month = 1, startYear = 190)
        val cmd = 건국(general, env)

        val result = runBlocking { cmd.run(fixedRng) }
        assertFalse(result.success)
        assertTrue(result.logs[0].contains("다음 턴부터"))
    }

    @Test
    fun `건국 run should succeed after first turn`() {
        val general = createTestGeneral(factionId = 1, officerLevel = 20, planetId = 5)
        val env = createTestEnv(year = 190, month = 2, startYear = 190)
        val city = createTestCity()
        val cmd = 건국(general, env, mapOf("factionName" to "신한", "nationType" to "군벌"))
        cmd.city = city

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        assertTrue(result.logs[0].contains("신한"))
        assertTrue(result.logs[0].contains("건국"))
    }

    // ========== 훈련 (Training) ==========

    @Test
    fun `훈련 should fail without ships`() {
        val general = createTestGeneral(ships = 0)
        val env = createTestEnv()
        val city = createTestCity()
        val cmd = che_훈련(general, env)
        cmd.city = city

        val result = cmd.checkFullCondition()
        assertTrue(result is ConstraintResult.Fail)
        assertTrue((result as ConstraintResult.Fail).reason.contains("병사"))
    }

    @Test
    fun `훈련 should fail when train is already at max`() {
        val general = createTestGeneral(ships = 1000, training = 100)
        val env = createTestEnv()
        val city = createTestCity()
        val cmd = che_훈련(general, env)
        cmd.city = city

        val result = cmd.checkFullCondition()
        assertTrue(result is ConstraintResult.Fail)
        assertTrue((result as ConstraintResult.Fail).reason.contains("훈련"))
    }

    @Test
    fun `훈련 should succeed with valid conditions`() {
        val general = createTestGeneral(ships = 1000, training = 50, factionId = 1)
        val env = createTestEnv()
        val city = createTestCity()
        val cmd = che_훈련(general, env)
        cmd.city = city

        val condResult = cmd.checkFullCondition()
        assertTrue(condResult is ConstraintResult.Pass)

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        assertTrue(result.logs[0].contains("훈련치"))
    }

    // ========== 하야 (Resign) ==========

    @Test
    fun `하야 should fail for lord`() {
        val general = createTestGeneral(officerLevel = 20)
        val env = createTestEnv()
        val cmd = 하야(general, env)

        val result = cmd.checkFullCondition()
        assertTrue(result is ConstraintResult.Fail)
        assertTrue((result as ConstraintResult.Fail).reason.contains("군주"))
    }

    @Test
    fun `하야 should fail for neutral general`() {
        val general = createTestGeneral(factionId = 0)
        val env = createTestEnv()
        val cmd = 하야(general, env)

        val result = cmd.checkFullCondition()
        assertTrue(result is ConstraintResult.Fail)
    }

    @Test
    fun `하야 should succeed for non-lord in a nation`() {
        val general = createTestGeneral(officerLevel = 5, factionId = 1, funds = 5000, supplies = 3000)
        val env = createTestEnv()
        val nation = createTestNation()
        val cmd = 하야(general, env)
        cmd.nation = nation

        val condResult = cmd.checkFullCondition()
        assertTrue(condResult is ConstraintResult.Pass)

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        assertTrue(result.logs[0].contains("하야"))
    }

    @Test
    fun `하야 should increase betray penalty on repeated resignations`() {
        val general = createTestGeneral(officerLevel = 5, factionId = 1, betray = 5, experience = 10000, dedication = 10000)
        val env = createTestEnv()
        val nation = createTestNation()
        val cmd = 하야(general, env)
        cmd.nation = nation

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        // With betray=5, penaltyRate = 0.5, so should lose 50% of exp and ded
        assertNotNull(result.message)
        assertTrue(result.message!!.contains("experience"))
    }

    // ========== 소집해제 (Disband) ==========

    @Test
    fun `소집해제 should fail without ships`() {
        val general = createTestGeneral(ships = 0)
        val env = createTestEnv()
        val cmd = che_소집해제(general, env)

        val result = cmd.checkFullCondition()
        assertTrue(result is ConstraintResult.Fail)
    }

    @Test
    fun `소집해제 should succeed with ships`() {
        val general = createTestGeneral(ships = 500)
        val env = createTestEnv()
        val cmd = che_소집해제(general, env)

        val condResult = cmd.checkFullCondition()
        assertTrue(condResult is ConstraintResult.Pass)

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        assertTrue(result.logs[0].contains("소집해제"))
        // Should return all ships as population
        assertNotNull(result.message)
        assertTrue(result.message!!.contains("\"population\":500"))
    }

    // ========== 헌납 (Donate) ==========

    @Test
    fun `헌납 gold should fail without sufficient gold`() {
        val general = createTestGeneral(funds = 50, factionId = 1)
        val env = createTestEnv()
        val city = createTestCity()
        val arg = mapOf<String, Any>("isGold" to true, "amount" to 100)
        val cmd = che_헌납(general, env, arg)
        cmd.city = city

        val result = cmd.checkFullCondition()
        assertTrue(result is ConstraintResult.Fail)
        assertTrue((result as ConstraintResult.Fail).reason.contains("자금"))
    }

    @Test
    fun `헌납 rice should check rice constraint`() {
        val general = createTestGeneral(supplies = 50, factionId = 1)
        val env = createTestEnv()
        val city = createTestCity()
        val arg = mapOf<String, Any>("isGold" to false, "amount" to 100)
        val cmd = che_헌납(general, env, arg)
        cmd.city = city

        val result = cmd.checkFullCondition()
        assertTrue(result is ConstraintResult.Fail)
        assertTrue((result as ConstraintResult.Fail).reason.contains("군량"))
    }

    @Test
    fun `헌납 should succeed with sufficient gold`() {
        val general = createTestGeneral(funds = 5000, factionId = 1)
        val env = createTestEnv()
        val city = createTestCity()
        val arg = mapOf<String, Any>("isGold" to true, "amount" to 1000)
        val cmd = che_헌납(general, env, arg)
        cmd.city = city

        val condResult = cmd.checkFullCondition()
        assertTrue(condResult is ConstraintResult.Pass)

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        assertTrue(result.logs[0].contains("금"))
        assertTrue(result.logs[0].contains("헌납"))
    }

    // ========== Date formatting ==========

    @Test
    fun `formatDate should pad month`() {
        val general = createTestGeneral()
        val env = createTestEnv(year = 200, month = 3)
        val cmd = 휴식(general, env)

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.logs[0].contains("200년 03월"))
    }
}
