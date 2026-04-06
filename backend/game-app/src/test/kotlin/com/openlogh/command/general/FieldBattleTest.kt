package com.openlogh.command.general

import com.openlogh.command.CommandEnv
import com.openlogh.engine.war.BattleEngine
import com.openlogh.engine.war.FieldBattleService
import com.openlogh.engine.war.WarUnitOfficer
import com.openlogh.entity.Planet
import com.openlogh.entity.Officer
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import kotlin.random.Random

class FieldBattleTest {

    private fun createGeneral(
        id: Long = 1,
        factionId: Long = 1,
        planetId: Long = 1,
        ships: Int = 1000,
        supplies: Int = 5000,
        leadership: Short = 50,
        command: Short = 50,
        intelligence: Short = 50,
        training: Short = 70,
        morale: Short = 70,
    ): Officer = Officer(
        id = id,
        sessionId = 1,
        name = "장수$id",
        factionId = factionId,
        planetId = planetId,
        ships = ships,
        supplies = supplies,
        leadership = leadership,
        command = command,
        intelligence = intelligence,
        training = training,
        morale = morale,
        turnTime = OffsetDateTime.now(),
    )

    private fun createCity(
        factionId: Long = 1,
        orbitalDefense: Int = 500,
        fortress: Int = 500,
        level: Short = 0,
    ): Planet = Planet(
        id = 1,
        sessionId = 1,
        name = "테스트도시",
        factionId = factionId,
        orbitalDefense = orbitalDefense,
        orbitalDefenseMax = 1000,
        fortress = fortress,
        fortressMax = 1000,
        population = 10000,
        populationMax = 50000,
        level = level,
    )

    private fun createEnv() = CommandEnv(
        year = 200,
        month = 1,
        startYear = 190,
        sessionId = 1,
        realtimeMode = false,
    )

    // ========== 요격 command ==========

    @Test
    fun `요격 saves correct lastTurn fields`() {
        val general = createGeneral(ships = 500, supplies = 100)
        val env = createEnv()
        val destPlanet = createCity()
        val arg = mapOf<String, Any>("destCityId" to 42L)

        val cmd = 요격(general, env, arg)
        cmd.destPlanet = destPlanet

        runBlocking { cmd.run(Random(1)) }

        assertEquals("요격", general.lastTurn["action"])
        assertEquals(42L, general.lastTurn["interceptionTargetCityId"])
        assertEquals(1L, general.lastTurn["originCityId"])
    }

    @Test
    fun `요격 logs ambush message with city name`() {
        val general = createGeneral(ships = 500, supplies = 100)
        val env = createEnv()
        val destPlanet = createCity()
        destPlanet.name = "낙양"
        val arg = mapOf<String, Any>("destCityId" to 5L)

        val cmd = 요격(general, env, arg)
        cmd.destPlanet = destPlanet

        val result = runBlocking { cmd.run(Random(1)) }

        assertTrue(result.success)
        assertTrue(result.logs.any { it.contains("낙양") && it.contains("매복") })
    }

    @Test
    fun `요격 costs 50 rice`() {
        val general = createGeneral()
        val env = createEnv()
        val cmd = 요격(general, env)

        assertEquals(0, cmd.getCost().funds)
        assertEquals(50, cmd.getCost().supplies)
    }

    @Test
    fun `요격 has zero preReqTurn and postReqTurn`() {
        val general = createGeneral()
        val env = createEnv()
        val cmd = 요격(general, env)

        assertEquals(0, cmd.getPreReqTurn())
        assertEquals(0, cmd.getPostReqTurn())
    }

    // ========== 순찰 command ==========

    @Test
    fun `순찰 saves correct lastTurn fields`() {
        val general = createGeneral(planetId = 7L, ships = 200, supplies = 100)
        val env = createEnv()

        val cmd = 순찰(general, env)
        runBlocking { cmd.run(Random(1)) }

        assertEquals("순찰", general.lastTurn["action"])
        assertEquals(7L, general.lastTurn["patrolCityId"])
    }

    @Test
    fun `순찰 logs patrol start message`() {
        val general = createGeneral(ships = 200, supplies = 100)
        val env = createEnv()

        val cmd = 순찰(general, env)
        val result = runBlocking { cmd.run(Random(1)) }

        assertTrue(result.success)
        assertTrue(result.logs.any { it.contains("순찰") })
    }

    @Test
    fun `순찰 costs 30 rice`() {
        val general = createGeneral()
        val env = createEnv()
        val cmd = 순찰(general, env)

        assertEquals(0, cmd.getCost().funds)
        assertEquals(30, cmd.getCost().supplies)
    }

    @Test
    fun `순찰 has zero preReqTurn and postReqTurn`() {
        val general = createGeneral()
        val env = createEnv()
        val cmd = 순찰(general, env)

        assertEquals(0, cmd.getPreReqTurn())
        assertEquals(0, cmd.getPostReqTurn())
    }

    // ========== FieldBattleService ==========

    @Test
    fun `FieldBattleService ambush increases interceptor attack multiplier`() {
        val service = FieldBattleService()
        val interceptorGeneral = createGeneral(id = 1, factionId = 1, ships = 3000, supplies = 30000, command = 80.toShort(), leadership = 80.toShort())
        val targetGeneral = createGeneral(id = 2, factionId = 2, ships = 2000, supplies = 20000)
        val city = createCity()

        val interceptor = WarUnitOfficer(interceptorGeneral)
        val target = WarUnitOfficer(targetGeneral)

        val initialAttackMultiplier = interceptor.attackMultiplier
        val initialCritChance = interceptor.criticalChance

        // Run the service — it mutates interceptor/target before battle
        service.resolve(interceptor, target, city, Random(42), isAmbush = true, year = 200, startYear = 180)

        // attackMultiplier was 1.0 → after *1.2 it should be 1.2
        assertEquals(initialAttackMultiplier * 1.2, interceptor.attackMultiplier, 0.001)
        assertEquals(initialCritChance + 0.15, interceptor.criticalChance, 0.001)
    }

    @Test
    fun `FieldBattleService ambush reduces target morale and defenceMultiplier`() {
        val service = FieldBattleService()
        val interceptorGeneral = createGeneral(id = 1, factionId = 1, ships = 3000, supplies = 30000, command = 80.toShort())
        val targetGeneral = createGeneral(id = 2, factionId = 2, ships = 2000, supplies = 20000, morale = 60.toShort())
        val city = createCity()

        val interceptor = WarUnitOfficer(interceptorGeneral)
        val target = WarUnitOfficer(targetGeneral)

        val initialTargetDefMult = target.defenceMultiplier

        service.resolve(interceptor, target, city, Random(42), isAmbush = true, year = 200, startYear = 180)

        // target morale: 60 - 10 = 50 (applied before battle, then battle may change further)
        // We verify the multiplier was applied (0.85)
        assertEquals(initialTargetDefMult * 0.85, target.defenceMultiplier, 0.001)
    }

    @Test
    fun `FieldBattleService non-ambush does not boost interceptor`() {
        val service = FieldBattleService()
        val interceptorGeneral = createGeneral(id = 1, factionId = 1, ships = 3000, supplies = 30000, command = 80.toShort())
        val targetGeneral = createGeneral(id = 2, factionId = 2, ships = 2000, supplies = 20000)
        val city = createCity()

        val interceptor = WarUnitOfficer(interceptorGeneral)
        val target = WarUnitOfficer(targetGeneral)

        val initialAttackMultiplier = interceptor.attackMultiplier
        val initialCritChance = interceptor.criticalChance
        val initialTargetDefMult = target.defenceMultiplier

        service.resolve(interceptor, target, city, Random(42), isAmbush = false, year = 200, startYear = 180)

        // No ambush boost — only the morale/orbitalDefense reductions still apply
        assertEquals(initialAttackMultiplier, interceptor.attackMultiplier, 0.001)
        assertEquals(initialCritChance, interceptor.criticalChance, 0.001)
        assertEquals(initialTargetDefMult * 0.85, target.defenceMultiplier, 0.001)
    }

    @Test
    fun `FieldBattleService returns valid BattleResult`() {
        val service = FieldBattleService()
        val interceptorGeneral = createGeneral(id = 1, factionId = 1, ships = 5000, supplies = 50000, command = 90.toShort(), leadership = 90.toShort(), training = 80.toShort(), morale = 80.toShort())
        val targetGeneral = createGeneral(id = 2, factionId = 2, ships = 1000, supplies = 5000, command = 30.toShort(), leadership = 30.toShort())
        val city = createCity()

        val interceptor = WarUnitOfficer(interceptorGeneral)
        val target = WarUnitOfficer(targetGeneral)

        val result = service.resolve(interceptor, target, city, Random(42), isAmbush = true, year = 200, startYear = 180)

        assertNotNull(result)
        assertTrue(result.attackerDamageDealt > 0)
        assertTrue(result.defenderDamageDealt > 0)
    }

    @Test
    fun `FieldBattleService field city has no walls`() {
        // Verify that a very weak city stat does not matter — FieldBattleService sets orbitalDefense=0 fortress=0
        // so siege phase adds no resistance. Strong attacker with no defenders should win easily.
        val service = FieldBattleService()
        val interceptorGeneral = createGeneral(id = 1, factionId = 1, ships = 10000, supplies = 100000, command = 99.toShort(), leadership = 99.toShort(), training = 99.toShort(), morale = 99.toShort())
        val targetGeneral = createGeneral(id = 2, factionId = 2, ships = 10, supplies = 1, command = 10.toShort(), leadership = 10.toShort(), training = 10.toShort(), morale = 10.toShort())
        val city = createCity(orbitalDefense = 9999, fortress = 9999)

        val interceptor = WarUnitOfficer(interceptorGeneral)
        val target = WarUnitOfficer(targetGeneral)

        val result = service.resolve(interceptor, target, city, Random(42), isAmbush = true, year = 200, startYear = 180)

        // With such a strong attacker vs weak target, attacker should win
        assertTrue(result.attackerWon)
    }
}
