package com.opensam.command.general

import com.opensam.command.CommandEnv
import com.opensam.engine.war.BattleEngine
import com.opensam.engine.war.FieldBattleService
import com.opensam.engine.war.WarUnitGeneral
import com.opensam.entity.City
import com.opensam.entity.General
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import kotlin.random.Random

class FieldBattleTest {

    private fun createGeneral(
        id: Long = 1,
        nationId: Long = 1,
        cityId: Long = 1,
        crew: Int = 1000,
        rice: Int = 5000,
        leadership: Short = 50,
        strength: Short = 50,
        intel: Short = 50,
        train: Short = 70,
        atmos: Short = 70,
    ): General = General(
        id = id,
        worldId = 1,
        name = "장수$id",
        nationId = nationId,
        cityId = cityId,
        crew = crew,
        rice = rice,
        leadership = leadership,
        strength = strength,
        intel = intel,
        train = train,
        atmos = atmos,
        turnTime = OffsetDateTime.now(),
    )

    private fun createCity(
        nationId: Long = 1,
        def: Int = 500,
        wall: Int = 500,
        level: Short = 0,
    ): City = City(
        id = 1,
        worldId = 1,
        name = "테스트도시",
        nationId = nationId,
        def = def,
        defMax = 1000,
        wall = wall,
        wallMax = 1000,
        pop = 10000,
        popMax = 50000,
        level = level,
    )

    private fun createEnv() = CommandEnv(
        year = 200,
        month = 1,
        startYear = 190,
        worldId = 1,
        realtimeMode = false,
    )

    // ========== 요격 command ==========

    @Test
    fun `요격 saves correct lastTurn fields`() {
        val general = createGeneral(crew = 500, rice = 100)
        val env = createEnv()
        val destCity = createCity()
        val arg = mapOf<String, Any>("destCityId" to 42L)

        val cmd = 요격(general, env, arg)
        cmd.destCity = destCity

        runBlocking { cmd.run(Random(1)) }

        assertEquals("요격", general.lastTurn["action"])
        assertEquals(42L, general.lastTurn["interceptionTargetCityId"])
        assertEquals(1L, general.lastTurn["originCityId"])
    }

    @Test
    fun `요격 logs ambush message with city name`() {
        val general = createGeneral(crew = 500, rice = 100)
        val env = createEnv()
        val destCity = createCity()
        destCity.name = "낙양"
        val arg = mapOf<String, Any>("destCityId" to 5L)

        val cmd = 요격(general, env, arg)
        cmd.destCity = destCity

        val result = runBlocking { cmd.run(Random(1)) }

        assertTrue(result.success)
        assertTrue(result.logs.any { it.contains("낙양") && it.contains("매복") })
    }

    @Test
    fun `요격 costs 50 rice`() {
        val general = createGeneral()
        val env = createEnv()
        val cmd = 요격(general, env)

        assertEquals(0, cmd.getCost().gold)
        assertEquals(50, cmd.getCost().rice)
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
        val general = createGeneral(cityId = 7L, crew = 200, rice = 100)
        val env = createEnv()

        val cmd = 순찰(general, env)
        runBlocking { cmd.run(Random(1)) }

        assertEquals("순찰", general.lastTurn["action"])
        assertEquals(7L, general.lastTurn["patrolCityId"])
    }

    @Test
    fun `순찰 logs patrol start message`() {
        val general = createGeneral(crew = 200, rice = 100)
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

        assertEquals(0, cmd.getCost().gold)
        assertEquals(30, cmd.getCost().rice)
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
        val interceptorGeneral = createGeneral(id = 1, nationId = 1, crew = 3000, rice = 30000, strength = 80.toShort(), leadership = 80.toShort())
        val targetGeneral = createGeneral(id = 2, nationId = 2, crew = 2000, rice = 20000)
        val city = createCity()

        val interceptor = WarUnitGeneral(interceptorGeneral)
        val target = WarUnitGeneral(targetGeneral)

        val initialAttackMultiplier = interceptor.attackMultiplier
        val initialCritChance = interceptor.criticalChance

        // Run the service — it mutates interceptor/target before battle
        service.resolve(interceptor, target, city, Random(42), isAmbush = true, year = 200, startYear = 180)

        // attackMultiplier was 1.0 → after *1.2 it should be 1.2
        assertEquals(initialAttackMultiplier * 1.2, interceptor.attackMultiplier, 0.001)
        assertEquals(initialCritChance + 0.15, interceptor.criticalChance, 0.001)
    }

    @Test
    fun `FieldBattleService ambush reduces target atmos and defenceMultiplier`() {
        val service = FieldBattleService()
        val interceptorGeneral = createGeneral(id = 1, nationId = 1, crew = 3000, rice = 30000, strength = 80.toShort())
        val targetGeneral = createGeneral(id = 2, nationId = 2, crew = 2000, rice = 20000, atmos = 60.toShort())
        val city = createCity()

        val interceptor = WarUnitGeneral(interceptorGeneral)
        val target = WarUnitGeneral(targetGeneral)

        val initialTargetDefMult = target.defenceMultiplier

        service.resolve(interceptor, target, city, Random(42), isAmbush = true, year = 200, startYear = 180)

        // target atmos: 60 - 10 = 50 (applied before battle, then battle may change further)
        // We verify the multiplier was applied (0.85)
        assertEquals(initialTargetDefMult * 0.85, target.defenceMultiplier, 0.001)
    }

    @Test
    fun `FieldBattleService non-ambush does not boost interceptor`() {
        val service = FieldBattleService()
        val interceptorGeneral = createGeneral(id = 1, nationId = 1, crew = 3000, rice = 30000, strength = 80.toShort())
        val targetGeneral = createGeneral(id = 2, nationId = 2, crew = 2000, rice = 20000)
        val city = createCity()

        val interceptor = WarUnitGeneral(interceptorGeneral)
        val target = WarUnitGeneral(targetGeneral)

        val initialAttackMultiplier = interceptor.attackMultiplier
        val initialCritChance = interceptor.criticalChance
        val initialTargetDefMult = target.defenceMultiplier

        service.resolve(interceptor, target, city, Random(42), isAmbush = false, year = 200, startYear = 180)

        // No ambush boost — only the atmos/def reductions still apply
        assertEquals(initialAttackMultiplier, interceptor.attackMultiplier, 0.001)
        assertEquals(initialCritChance, interceptor.criticalChance, 0.001)
        assertEquals(initialTargetDefMult * 0.85, target.defenceMultiplier, 0.001)
    }

    @Test
    fun `FieldBattleService returns valid BattleResult`() {
        val service = FieldBattleService()
        val interceptorGeneral = createGeneral(id = 1, nationId = 1, crew = 5000, rice = 50000, strength = 90.toShort(), leadership = 90.toShort(), train = 80.toShort(), atmos = 80.toShort())
        val targetGeneral = createGeneral(id = 2, nationId = 2, crew = 1000, rice = 5000, strength = 30.toShort(), leadership = 30.toShort())
        val city = createCity()

        val interceptor = WarUnitGeneral(interceptorGeneral)
        val target = WarUnitGeneral(targetGeneral)

        val result = service.resolve(interceptor, target, city, Random(42), isAmbush = true, year = 200, startYear = 180)

        assertNotNull(result)
        assertTrue(result.attackerDamageDealt > 0)
        assertTrue(result.defenderDamageDealt > 0)
    }

    @Test
    fun `FieldBattleService field city has no walls`() {
        // Verify that a very weak city stat does not matter — FieldBattleService sets def=0 wall=0
        // so siege phase adds no resistance. Strong attacker with no defenders should win easily.
        val service = FieldBattleService()
        val interceptorGeneral = createGeneral(id = 1, nationId = 1, crew = 10000, rice = 100000, strength = 99.toShort(), leadership = 99.toShort(), train = 99.toShort(), atmos = 99.toShort())
        val targetGeneral = createGeneral(id = 2, nationId = 2, crew = 10, rice = 1, strength = 10.toShort(), leadership = 10.toShort(), train = 10.toShort(), atmos = 10.toShort())
        val city = createCity(def = 9999, wall = 9999)

        val interceptor = WarUnitGeneral(interceptorGeneral)
        val target = WarUnitGeneral(targetGeneral)

        val result = service.resolve(interceptor, target, city, Random(42), isAmbush = true, year = 200, startYear = 180)

        // With such a strong attacker vs weak target, attacker should win
        assertTrue(result.attackerWon)
    }
}
