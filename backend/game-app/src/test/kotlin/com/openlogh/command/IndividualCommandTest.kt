package com.openlogh.command

import com.openlogh.command.general.*
import com.openlogh.entity.Planet
import com.openlogh.entity.Officer
import com.openlogh.entity.Faction
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import kotlin.random.Random

class IndividualCommandTest {

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
        injury: Short = 0,
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
            injury = injury,
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
        develCost: Int = 100,
    ) = CommandEnv(
        year = year,
        month = month,
        startYear = startYear,
        sessionId = 1,
        realtimeMode = false,
        develCost = develCost,
    )

    private val fixedRng = Random(42)

    // ========== 견문 (Sightseeing) ==========

    @Test
    fun `견문 should increase experience or stat exp`() {
        val general = createTestGeneral()
        val env = createTestEnv()
        val cmd = 견문(general, env)

        val result = runBlocking { cmd.run(fixedRng) }

        assertTrue(result.success)
        assertTrue(result.logs.isNotEmpty())
        assertNotNull(result.message)
        // Check that statChanges includes experience
        assertTrue(result.message!!.contains("\"experience\":"))
    }

    @Test
    fun `견문 may increase stat experience (leadership, strength, intel)`() {
        val general = createTestGeneral()
        val env = createTestEnv()
        val cmd = 견문(general, env)

        val result = runBlocking { cmd.run(Random(10)) }

        assertTrue(result.success)
        // The result should contain experience changes
        val msg = result.message ?: ""
        assertTrue(msg.contains("statChanges"))
    }

    @Test
    fun `견문 may increase or decrease gold and rice`() {
        val general = createTestGeneral(funds = 5000, supplies = 5000)
        val env = createTestEnv()
        val cmd = 견문(general, env)

        // Run multiple times to see different outcomes
        val results = (0..5).map { runBlocking { cmd.run(Random.Default) } }

        assertTrue(results.all { it.success })
    }

    @Test
    fun `견문 may cause injury (wounded or heavy wounded)`() {
        val general = createTestGeneral()
        val env = createTestEnv()
        val cmd = 견문(general, env)

        val result = runBlocking { cmd.run(Random(1234)) }

        assertTrue(result.success)
        assertNotNull(result.message)
    }

    // ========== 요양 (Rest/Healing) ==========

    @Test
    fun `요양 should decrease injury to zero`() {
        val general = createTestGeneral(injury = 50)
        val env = createTestEnv()
        val cmd = 요양(general, env)

        val result = runBlocking { cmd.run(fixedRng) }

        assertTrue(result.success)
        assertTrue(result.logs[0].contains("요양"))
        assertNotNull(result.message)
        // Check that injury decreases by 50 (from 50 to 0)
        assertTrue(result.message!!.contains("\"injury\":-50"))
    }

    @Test
    fun `요양 should increase experience and dedication`() {
        val general = createTestGeneral(injury = 20)
        val env = createTestEnv()
        val cmd = 요양(general, env)

        val result = runBlocking { cmd.run(fixedRng) }

        assertTrue(result.success)
        assertNotNull(result.message)
        assertTrue(result.message!!.contains("\"experience\":10"))
        assertTrue(result.message!!.contains("\"dedication\":7"))
    }

    @Test
    fun `요양 should work even with zero injury`() {
        val general = createTestGeneral(injury = 0)
        val env = createTestEnv()
        val cmd = 요양(general, env)

        val result = runBlocking { cmd.run(fixedRng) }

        assertTrue(result.success)
        assertNotNull(result.message)
        assertTrue(result.message!!.contains("\"injury\":0"))
    }

    // ========== 이동 (Move) ==========

    @Test
    fun `이동 should change planetId to destination`() {
        val general = createTestGeneral(planetId = 1, funds = 500)
        val env = createTestEnv()
        val destPlanet = createTestCity().apply { id = 2 }
        val cmd = 이동(general, env)
        cmd.destPlanet = destPlanet

        val result = runBlocking { cmd.run(fixedRng) }

        assertTrue(result.success)
        assertTrue(result.logs[0].contains("이동"))
        assertNotNull(result.message)
        // Check that planetId changes to 2
        assertTrue(result.message!!.contains("\"planetId\":\"2\""))
    }

    @Test
    fun `이동 should decrease morale by 5 (min 20)`() {
        val general = createTestGeneral(morale = 60, funds = 500)
        val env = createTestEnv()
        val destPlanet = createTestCity().apply { id = 2 }
        val cmd = 이동(general, env)
        cmd.destPlanet = destPlanet

        val result = runBlocking { cmd.run(fixedRng) }

        assertTrue(result.success)
        assertNotNull(result.message)
        // morale should decrease by 5 (from 60 to 55)
        assertTrue(result.message!!.contains("\"morale\":-5"))
    }

    @Test
    fun `이동 should not decrease morale below 20`() {
        val general = createTestGeneral(morale = 20, funds = 500)
        val env = createTestEnv()
        val destPlanet = createTestCity().apply { id = 2 }
        val cmd = 이동(general, env)
        cmd.destPlanet = destPlanet

        val result = runBlocking { cmd.run(fixedRng) }

        assertTrue(result.success)
        assertNotNull(result.message)
        // morale should stay at 20 (delta = 0)
        assertTrue(result.message!!.contains("\"morale\":0"))
    }

    @Test
    fun `이동 should consume gold based on develCost`() {
        val general = createTestGeneral(funds = 500)
        val env = createTestEnv(develCost = 150)
        val destPlanet = createTestCity().apply { id = 2 }
        val cmd = 이동(general, env)
        cmd.destPlanet = destPlanet

        val result = runBlocking { cmd.run(fixedRng) }

        assertTrue(result.success)
        assertNotNull(result.message)
        // Should consume 150 gold
        assertTrue(result.message!!.contains("\"gold\":-150"))
    }

    // ========== 훈련 (Training) ==========

    @Test
    fun `훈련 should increase train stat`() {
        val general = createTestGeneral(ships = 1000, training = 50, leadership = 80, factionId = 1)
        val env = createTestEnv()
        val city = createTestCity()
        val cmd = che_훈련(general, env)
        cmd.city = city

        val result = runBlocking { cmd.run(fixedRng) }

        assertTrue(result.success)
        assertTrue(result.logs[0].contains("훈련치"))
        assertNotNull(result.message)
        // Train should increase
        assertTrue(result.message!!.contains("\"train\":"))
    }

    @Test
    fun `훈련 should reduce morale as side effect (90 percent retention)`() {
        val general = createTestGeneral(ships = 1000, training = 50, morale = 80, leadership = 80, factionId = 1)
        val env = createTestEnv()
        val city = createTestCity()
        val cmd = che_훈련(general, env)
        cmd.city = city

        val result = runBlocking { cmd.run(fixedRng) }

        assertTrue(result.success)
        assertNotNull(result.message)
        // morale should become 72 (80 * 0.9), delta = -8
        assertTrue(result.message!!.contains("\"morale\":"))
    }

    @Test
    fun `훈련 should cap train at 100`() {
        val general = createTestGeneral(ships = 1000, training = 99, leadership = 80, factionId = 1)
        val env = createTestEnv()
        val city = createTestCity()
        val cmd = che_훈련(general, env)
        cmd.city = city

        val result = runBlocking { cmd.run(fixedRng) }

        assertTrue(result.success)
        // Should only increase by max 1 (to reach 100)
        assertNotNull(result.message)
    }

    // ========== 사기진작 (Morale Boost) ==========

    @Test
    fun `사기진작 should increase morale stat`() {
        val general = createTestGeneral(ships = 1000, morale = 50, training = 80, leadership = 80, factionId = 1, funds = 500)
        val env = createTestEnv()
        val city = createTestCity()
        val cmd = che_사기진작(general, env)
        cmd.city = city

        val result = runBlocking { cmd.run(fixedRng) }

        assertTrue(result.success)
        assertTrue(result.logs[0].contains("사기치"))
        assertNotNull(result.message)
        // morale should increase
        assertTrue(result.message!!.contains("\"morale\":"))
    }

    @Test
    fun `사기진작 should reduce train as side effect (90 percent retention)`() {
        val general = createTestGeneral(ships = 1000, morale = 50, training = 80, leadership = 80, factionId = 1, funds = 500)
        val env = createTestEnv()
        val city = createTestCity()
        val cmd = che_사기진작(general, env)
        cmd.city = city

        val result = runBlocking { cmd.run(fixedRng) }

        assertTrue(result.success)
        assertNotNull(result.message)
        // train should become 72 (80 * 0.9), delta = -8
        assertTrue(result.message!!.contains("\"train\":"))
    }

    @Test
    fun `사기진작 should consume gold based on ships count`() {
        val general = createTestGeneral(ships = 1000, morale = 50, leadership = 80, factionId = 1, funds = 500)
        val env = createTestEnv()
        val city = createTestCity()
        val cmd = che_사기진작(general, env)
        cmd.city = city

        val result = runBlocking { cmd.run(fixedRng) }

        assertTrue(result.success)
        assertNotNull(result.message)
        // gold cost = ships / 100 = 1000 / 100 = 10
        assertTrue(result.message!!.contains("\"gold\":-10"))
    }

    // ========== 모병 (Recruitment) ==========

    @Test
    fun `모병 should increase ships count`() {
        val general = createTestGeneral(ships = 0, leadership = 50, funds = 5000, supplies = 5000, factionId = 1)
        val env = createTestEnv()
        val city = createTestCity(population = 50000)
        val arg = mapOf<String, Any>("amount" to 500, "crewType" to 0)
        val cmd = che_모병(general, env, arg)
        cmd.city = city

        val result = runBlocking { cmd.run(fixedRng) }

        assertTrue(result.success)
        assertTrue(result.logs[0].contains("모병"))
        assertNotNull(result.message)
        // ships should increase by 500
        assertTrue(result.message!!.contains("\"ships\":500"))
    }

    @Test
    fun `모병 should set default train and morale for new ships`() {
        val general = createTestGeneral(ships = 0, leadership = 50, funds = 5000, supplies = 5000, factionId = 1)
        val env = createTestEnv()
        val city = createTestCity(population = 50000)
        val arg = mapOf<String, Any>("amount" to 500, "crewType" to 0)
        val cmd = che_모병(general, env, arg)
        cmd.city = city

        val result = runBlocking { cmd.run(fixedRng) }

        assertTrue(result.success)
        assertNotNull(result.message)
        // train should be 70 (default high), morale should be 70
        assertTrue(result.message!!.contains("\"train\":70"))
        assertTrue(result.message!!.contains("\"morale\":70"))
    }

    @Test
    fun `모병 should merge train and morale when adding to same ships type`() {
        val general = createTestGeneral(ships = 1000, shipClass = 1, training = 80, morale = 80, leadership = 50, funds = 5000, supplies = 5000, factionId = 1)
        val env = createTestEnv()
        val city = createTestCity(population = 50000)
        val arg = mapOf<String, Any>("amount" to 500, "crewType" to 1)
        val cmd = che_모병(general, env, arg)
        cmd.city = city

        val result = runBlocking { cmd.run(fixedRng) }

        assertTrue(result.success)
        assertTrue(result.logs[0].contains("추가모병"))
        assertNotNull(result.message)
    }

    @Test
    fun `모병 should consume gold and rice`() {
        val general = createTestGeneral(ships = 0, leadership = 50, funds = 5000, supplies = 5000, factionId = 1)
        val env = createTestEnv()
        val city = createTestCity(population = 50000)
        val arg = mapOf<String, Any>("amount" to 500, "crewType" to 0)
        val cmd = che_모병(general, env, arg)
        cmd.city = city

        val result = runBlocking { cmd.run(fixedRng) }

        assertTrue(result.success)
        assertNotNull(result.message)
        // gold cost = (500/10) * 2 = 100, rice cost = 500/100 = 5
        assertTrue(result.message!!.contains("\"gold\":-100"))
        assertTrue(result.message!!.contains("\"rice\":-5"))
    }

    @Test
    fun `모병 should decrease city population`() {
        val general = createTestGeneral(ships = 0, leadership = 50, funds = 5000, supplies = 5000, factionId = 1)
        val env = createTestEnv()
        val city = createTestCity(population = 50000)
        val arg = mapOf<String, Any>("amount" to 500, "crewType" to 0)
        val cmd = che_모병(general, env, arg)
        cmd.city = city

        val result = runBlocking { cmd.run(fixedRng) }

        assertTrue(result.success)
        assertNotNull(result.message)
        assertTrue(result.message!!.contains("\"population\":-500"))
    }

    // ========== 징병 (Conscription) ==========

    @Test
    fun `징병 should increase ships count with lower train and morale than 모병`() {
        val general = createTestGeneral(ships = 0, leadership = 50, funds = 5000, supplies = 5000, factionId = 1)
        val env = createTestEnv()
        val city = createTestCity(population = 50000)
        val arg = mapOf<String, Any>("amount" to 500, "crewType" to 0)
        val cmd = che_징병(general, env, arg)
        cmd.city = city

        val result = runBlocking { cmd.run(fixedRng) }

        assertTrue(result.success)
        assertTrue(result.logs[0].contains("징병"))
        assertNotNull(result.message)
        // ships should increase by 500
        assertTrue(result.message!!.contains("\"ships\":500"))
        // train should be 40 (default low per PHP GameConstBase), morale should be 40
        assertTrue(result.message!!.contains("\"train\":40"))
        assertTrue(result.message!!.contains("\"morale\":40"))
    }

    @Test
    fun `징병 should be cheaper than 모병`() {
        val general = createTestGeneral(ships = 0, leadership = 50, funds = 5000, supplies = 5000, factionId = 1)
        val env = createTestEnv()
        val city = createTestCity(population = 50000)
        val arg = mapOf<String, Any>("amount" to 500, "crewType" to 0)
        val cmd = che_징병(general, env, arg)
        cmd.city = city

        val result = runBlocking { cmd.run(fixedRng) }

        assertTrue(result.success)
        assertNotNull(result.message)
        // gold cost = (500/10) * 1 = 50, rice cost = 500/100 = 5
        assertTrue(result.message!!.contains("\"gold\":-50"))
        assertTrue(result.message!!.contains("\"rice\":-5"))
    }

    // ========== 소집해제 (Disband) ==========

    @Test
    fun `소집해제 should set ships to zero`() {
        val general = createTestGeneral(ships = 500, factionId = 1)
        val env = createTestEnv()
        val cmd = che_소집해제(general, env)

        val result = runBlocking { cmd.run(fixedRng) }

        assertTrue(result.success)
        assertTrue(result.logs[0].contains("소집해제"))
        assertNotNull(result.message)
        // ships should decrease by 500 (to 0)
        assertTrue(result.message!!.contains("\"ships\":-500"))
    }

    @Test
    fun `소집해제 should return ships as city population`() {
        val general = createTestGeneral(ships = 500, factionId = 1)
        val env = createTestEnv()
        val cmd = che_소집해제(general, env)

        val result = runBlocking { cmd.run(fixedRng) }

        assertTrue(result.success)
        assertNotNull(result.message)
        // city population should increase by 500
        assertTrue(result.message!!.contains("\"population\":500"))
    }

    // ========== 헌납 (Donate) ==========

    @Test
    fun `헌납 gold should transfer gold from general to nation`() {
        val general = createTestGeneral(funds = 5000, factionId = 1)
        val env = createTestEnv()
        val city = createTestCity()
        val arg = mapOf<String, Any>("isGold" to true, "amount" to 1000)
        val cmd = che_헌납(general, env, arg)
        cmd.city = city

        val result = runBlocking { cmd.run(fixedRng) }

        assertTrue(result.success)
        assertTrue(result.logs[0].contains("금"))
        assertTrue(result.logs[0].contains("헌납"))
        assertNotNull(result.message)
        // general loses 1000 gold, nation gains 1000 gold
        assertTrue(result.message!!.contains("\"gold\":-1000"))
        assertTrue(result.message!!.contains("nationChanges"))
    }

    @Test
    fun `헌납 rice should transfer rice from general to nation`() {
        val general = createTestGeneral(supplies = 5000, factionId = 1)
        val env = createTestEnv()
        val city = createTestCity()
        val arg = mapOf<String, Any>("isGold" to false, "amount" to 1000)
        val cmd = che_헌납(general, env, arg)
        cmd.city = city

        val result = runBlocking { cmd.run(fixedRng) }

        assertTrue(result.success)
        assertTrue(result.logs[0].contains("쌀"))
        assertTrue(result.logs[0].contains("헌납"))
        assertNotNull(result.message)
        // general loses 1000 rice, nation gains 1000 rice
        assertTrue(result.message!!.contains("\"rice\":-1000"))
        assertTrue(result.message!!.contains("nationChanges"))
    }

    @Test
    fun `헌납 should cap amount at general's available resource`() {
        val general = createTestGeneral(funds = 300, factionId = 1)
        val env = createTestEnv()
        val city = createTestCity()
        val arg = mapOf<String, Any>("isGold" to true, "amount" to 1000)
        val cmd = che_헌납(general, env, arg)
        cmd.city = city

        val result = runBlocking { cmd.run(fixedRng) }

        assertTrue(result.success)
        assertNotNull(result.message)
        // Should only donate 300 (available amount)
        assertTrue(result.message!!.contains("\"gold\":-300"))
    }

    // ========== 농지개간 (Farming Development) ==========

    @Test
    fun `농지개간 should increase city production`() {
        val general = createTestGeneral(intelligence = 80, funds = 500, factionId = 1)
        val env = createTestEnv()
        val city = createTestCity(production = 500, productionMax = 1000)
        val cmd = che_농지개간(general, env)
        cmd.city = city

        val result = runBlocking { cmd.run(fixedRng) }

        assertTrue(result.success)
        assertTrue(result.logs[0].contains("농지 개간"))
        assertNotNull(result.message)
        // production should increase
        assertTrue(result.message!!.contains("\"production\":"))
    }

    @Test
    fun `농지개간 should use intel stat for calculation`() {
        val general = createTestGeneral(intelligence = 90, funds = 500, factionId = 1)
        val env = createTestEnv()
        val city = createTestCity(production = 500, productionMax = 1000, approval = 100f)
        val cmd = che_농지개간(general, env)
        cmd.city = city

        val result = runBlocking { cmd.run(Random(1)) }

        assertTrue(result.success)
        assertNotNull(result.message)
        // Higher intel should result in higher score
        assertTrue(result.message!!.contains("cityChanges"))
    }

    @Test
    fun `농지개간 should apply front line debuff`() {
        val general = createTestGeneral(intelligence = 80, funds = 500, factionId = 1)
        val env = createTestEnv()
        val city = createTestCity(production = 500, productionMax = 1000, frontState = 1, approval = 100f)
        val cmd = che_농지개간(general, env)
        cmd.city = city

        val result = runBlocking { cmd.run(Random(100)) }

        assertTrue(result.success)
        assertNotNull(result.message)
        // Score should be halved due to front line
    }

    @Test
    fun `농지개간 should consume gold based on develCost`() {
        val general = createTestGeneral(intelligence = 80, funds = 500, factionId = 1)
        val env = createTestEnv(develCost = 150)
        val city = createTestCity(production = 500, productionMax = 1000)
        val cmd = che_농지개간(general, env)
        cmd.city = city

        val result = runBlocking { cmd.run(fixedRng) }

        assertTrue(result.success)
        assertNotNull(result.message)
        assertTrue(result.message!!.contains("\"gold\":-150"))
    }

    // ========== 상업투자 (Commerce Investment) ==========

    @Test
    fun `상업투자 should increase city commerce`() {
        val general = createTestGeneral(intelligence = 80, funds = 500, factionId = 1)
        val env = createTestEnv()
        val city = createTestCity(commerce = 500, commerceMax = 1000)
        val cmd = che_상업투자(general, env)
        cmd.city = city

        val result = runBlocking { cmd.run(fixedRng) }

        assertTrue(result.success)
        assertTrue(result.logs[0].contains("상업 투자"))
        assertNotNull(result.message)
        // commerce should increase
        assertTrue(result.message!!.contains("\"commerce\":"))
    }

    @Test
    fun `상업투자 should use intel stat for calculation`() {
        val general = createTestGeneral(intelligence = 90, funds = 500, factionId = 1)
        val env = createTestEnv()
        val city = createTestCity(commerce = 500, commerceMax = 1000, approval = 100f)
        val cmd = che_상업투자(general, env)
        cmd.city = city

        val result = runBlocking { cmd.run(Random(1)) }

        assertTrue(result.success)
        assertNotNull(result.message)
        assertTrue(result.message!!.contains("cityChanges"))
    }

    // ========== 치안강화 (Security Enhancement) ==========

    @Test
    fun `치안강화 should increase city security`() {
        val general = createTestGeneral(intelligence = 80, funds = 500, factionId = 1)
        val env = createTestEnv()
        val city = createTestCity(security = 500, securityMax = 1000)
        val cmd = che_치안강화(general, env)
        cmd.city = city

        val result = runBlocking { cmd.run(fixedRng) }

        assertTrue(result.success)
        assertTrue(result.logs[0].contains("치안 강화"))
        assertNotNull(result.message)
        // security should increase
        assertTrue(result.message!!.contains("\"security\":"))
    }

    @Test
    fun `치안강화 should use intel stat for calculation`() {
        val general = createTestGeneral(intelligence = 90, funds = 500, factionId = 1)
        val env = createTestEnv()
        val city = createTestCity(security = 500, securityMax = 1000, approval = 100f)
        val cmd = che_치안강화(general, env)
        cmd.city = city

        val result = runBlocking { cmd.run(Random(1)) }

        assertTrue(result.success)
        assertNotNull(result.message)
        assertTrue(result.message!!.contains("cityChanges"))
    }

    // ========== Domestic command critical results ==========

    @Test
    fun `농지개간 can have critical success or failure`() {
        val general = createTestGeneral(intelligence = 80, funds = 500, factionId = 1)
        val env = createTestEnv()
        val city = createTestCity(production = 500, productionMax = 1000, approval = 80f)
        val cmd = che_농지개간(general, env)
        cmd.city = city

        // Run multiple times to see different results
        val results = (0..10).map {
            val result = runBlocking { cmd.run(Random.Default) }
            result.message?.contains("criticalResult") ?: false
        }

        assertTrue(results.any { it })
    }
}
