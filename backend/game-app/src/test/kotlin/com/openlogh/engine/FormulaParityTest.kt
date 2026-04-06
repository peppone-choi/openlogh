package com.openlogh.engine

import com.openlogh.engine.war.BattleEngine
import com.openlogh.engine.war.BattleResult
import com.openlogh.engine.war.WarUnitOfficer
import com.openlogh.entity.Planet
import com.openlogh.entity.Officer
import com.openlogh.repository.PlanetRepository
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.MessageRepository
import com.openlogh.repository.FactionRepository
import com.openlogh.service.InheritanceService
import com.openlogh.service.MapService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.time.OffsetDateTime

class FormulaParityTest {

    private lateinit var economyService: EconomyService
    private lateinit var battleEngine: BattleEngine

    @BeforeEach
    fun setUp() {
        economyService = EconomyService(
            mock(PlanetRepository::class.java),
            mock(FactionRepository::class.java),
            mock(OfficerRepository::class.java),
            mock(MessageRepository::class.java),
            mock(MapService::class.java),
            mock(com.openlogh.service.HistoryService::class.java),
            mock(InheritanceService::class.java),
        )
        battleEngine = BattleEngine()
    }

    @Test
    fun `getDedLevel matches legacy ceil sqrt formula`() {
        assertEquals(1, invokeGetDedLevel(100))
        assertEquals(3, invokeGetDedLevel(900))
        assertEquals(10, invokeGetDedLevel(10000))
    }

    @Test
    fun `calcCityGoldIncome matches legacy formula sample`() {
        val city = createCity(population = 10000, commerce = 500, commerceMax = 1000, approval = 80f, security = 500, securityMax = 1000)

        val income = invokeCalcCityGoldIncome(city, officerCnt = 0, isCapital = false, nationLevel = 1)

        assertEquals(157.5, income, 0.0001)
    }

    @Test
    fun `calcCityRiceIncome matches legacy formula sample`() {
        val city = createCity(population = 10000, production = 500, productionMax = 1000, approval = 80f, security = 500, securityMax = 1000)

        val income = invokeCalcCityRiceIncome(city, officerCnt = 0, isCapital = false, nationLevel = 1)

        assertEquals(157.5, income, 0.0001)
    }

    @Test
    fun `resolveBattle is deterministic with same LiteHashDRBG seed`() {
        val left = runBattle("battle_test")
        val right = runBattle("battle_test")

        assertEquals(left.attackerDamageDealt, right.attackerDamageDealt)
        assertEquals(left.defenderDamageDealt, right.defenderDamageDealt)
        assertEquals(left.attackerWon, right.attackerWon)
        assertEquals(left.cityOccupied, right.cityOccupied)
    }

    @Test
    fun `resolveBattle differs with different LiteHashDRBG seeds`() {
        val baseline = runBattle("battle_test")
        val alternatives = listOf("battle_test_alt_1", "battle_test_alt_2", "battle_test_alt_3")
            .map { runBattle(it) }

        assertTrue(alternatives.any { isBattleOutcomeDifferent(baseline, it) })
    }

    private fun runBattle(seed: String): BattleResult {
        val attackerGeneral = createGeneral(
            id = 1,
            factionId = 1,
            leadership = 75,
            command = 78,
            intelligence = 60,
            ships = 3500,
            training = 78,
            morale = 77,
            supplies = 120000,
            experience = 3000,
            dedication = 2000,
        )
        val defenderGeneral = createGeneral(
            id = 2,
            factionId = 2,
            leadership = 70,
            command = 72,
            intelligence = 65,
            ships = 3200,
            training = 75,
            morale = 74,
            supplies = 100000,
            experience = 2500,
            dedication = 1800,
        )
        val city = createCity(factionId = 2, orbitalDefense = 450, fortress = 500, population = 20000)

        return battleEngine.resolveBattle(
            attacker = WarUnitOfficer(attackerGeneral),
            defenders = listOf(WarUnitOfficer(defenderGeneral)),
            city = city,
            rng = LiteHashDRBG.build(seed),
        )
    }

    private fun isBattleOutcomeDifferent(left: BattleResult, right: BattleResult): Boolean {
        return left.attackerDamageDealt != right.attackerDamageDealt ||
            left.defenderDamageDealt != right.defenderDamageDealt ||
            left.attackerWon != right.attackerWon ||
            left.cityOccupied != right.cityOccupied
    }

    private fun invokeGetDedLevel(dedication: Int): Int {
        val method = EconomyService::class.java.getDeclaredMethod(
            "getDedLevel",
            Int::class.javaPrimitiveType,
        )
        method.isAccessible = true
        return method.invoke(economyService, dedication) as Int
    }

    private fun invokeCalcCityGoldIncome(city: Planet, officerCnt: Int, isCapital: Boolean, nationLevel: Int): Double {
        val method = EconomyService::class.java.getDeclaredMethod(
            "calcCityGoldIncome",
            Planet::class.java,
            Int::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Double::class.javaPrimitiveType,
        )
        method.isAccessible = true
        return method.invoke(economyService, city, officerCnt, isCapital, nationLevel, 1.0) as Double
    }

    private fun invokeCalcCityRiceIncome(city: Planet, officerCnt: Int, isCapital: Boolean, nationLevel: Int): Double {
        val method = EconomyService::class.java.getDeclaredMethod(
            "calcCityRiceIncome",
            Planet::class.java,
            Int::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Double::class.javaPrimitiveType,
        )
        method.isAccessible = true
        return method.invoke(economyService, city, officerCnt, isCapital, nationLevel, 1.0) as Double
    }

    private fun createGeneral(
        id: Long,
        factionId: Long,
        leadership: Short,
        command: Short,
        intelligence: Short,
        ships: Int,
        training: Short,
        morale: Short,
        supplies: Int,
        experience: Int,
        dedication: Int,
    ): Officer {
        return Officer(
            id = id,
            sessionId = 1,
            name = "장수$id",
            factionId = factionId,
            planetId = 1,
            leadership = leadership,
            command = command,
            intelligence = intelligence,
            ships = ships,
            shipClass = 0,
            training = training,
            morale = morale,
            supplies = supplies,
            experience = experience,
            dedication = dedication,
            turnTime = OffsetDateTime.now(),
        )
    }

    private fun createCity(
        factionId: Long = 1,
        population: Int = 10000,
        production: Int = 500,
        productionMax: Int = 1000,
        commerce: Int = 500,
        commerceMax: Int = 1000,
        security: Int = 500,
        securityMax: Int = 1000,
        approval: Float = 80f,
        orbitalDefense: Int = 500,
        fortress: Int = 500,
    ): Planet {
        return Planet(
            id = 1,
            sessionId = 1,
            name = "테스트도시",
            factionId = factionId,
            population = population,
            populationMax = 50000,
            production = production,
            productionMax = productionMax,
            commerce = commerce,
            commerceMax = commerceMax,
            security = security,
            securityMax = securityMax,
            approval = approval,
            orbitalDefense = orbitalDefense,
            orbitalDefenseMax = 1000,
            fortress = fortress,
            fortressMax = 1000,
        )
    }
}
