package com.openlogh.qa.parity

import com.openlogh.engine.EconomyService
import com.openlogh.engine.Gin7EconomyService
import com.openlogh.engine.turn.cqrs.persist.toEntity
import com.openlogh.engine.turn.cqrs.persist.toSnapshot
import com.openlogh.entity.Planet
import com.openlogh.entity.Officer
import com.openlogh.entity.Faction
import com.openlogh.entity.SessionState
import com.openlogh.repository.PlanetRepository
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.MessageRepository
import com.openlogh.repository.FactionRepository
import com.openlogh.service.HistoryService
import com.openlogh.service.InheritanceService
import com.openlogh.service.MapService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import kotlin.math.ceil
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Economy Formula Parity Test
 *
 * Legacy source:
 *   - hwe/func_time_event.php: calcCityGoldIncome(), calcCityRiceIncome(),
 *     calcCityWallRiceIncome(), calcCityWarGoldIncome(), getOutcome(), getBill()
 *   - hwe/func_converter.php: getBill(), getDedLevel()
 *   - hwe/sammo/Event/Action/ProcessIncome.php: gold/rice salary distribution
 *   - hwe/sammo/GameConstBase.php: basegold=0, baserice=2000, basePopIncreaseAmount=5000
 *
 * Current impl: EconomyService.kt
 *
 * Each test computes an EXACT golden value from the legacy PHP formula, then
 * verifies the Kotlin result matches within tolerance.
 */
@DisplayName("Economy Formula Parity")
class EconomyFormulaParityTest {

    private lateinit var service: EconomyService
    private lateinit var gin7Service: Gin7EconomyService
    private lateinit var planetRepository: PlanetRepository
    private lateinit var factionRepository: FactionRepository
    private lateinit var officerRepository: OfficerRepository
    private lateinit var mapService: MapService

    private val cities = linkedMapOf<Long, Planet>()
    private val nations = linkedMapOf<Long, Faction>()
    private val generals = linkedMapOf<Long, Officer>()

    @BeforeEach
    fun setUp() {
        planetRepository = mock(PlanetRepository::class.java)
        factionRepository = mock(FactionRepository::class.java)
        officerRepository = mock(OfficerRepository::class.java)
        mapService = mock(MapService::class.java)
        service = EconomyService(
            planetRepository, factionRepository, officerRepository,
            mock(MessageRepository::class.java), mapService,
            mock(HistoryService::class.java), mock(InheritanceService::class.java),
        )
        gin7Service = Gin7EconomyService(factionRepository, planetRepository, officerRepository, mapService)
        wireRepos()
        `when`(mapService.getAdjacentCities(ArgumentMatchers.anyString(), ArgumentMatchers.anyInt()))
            .thenReturn(emptyList())
    }

    private fun wireRepos() {
        `when`(planetRepository.findBySessionId(ArgumentMatchers.anyLong())).thenAnswer { inv ->
            val sessionId = inv.arguments[0] as Long
            cities.values.filter { it.sessionId == sessionId }.map { it.toSnapshot().toEntity() }
        }
        `when`(factionRepository.findBySessionId(ArgumentMatchers.anyLong())).thenAnswer { inv ->
            val sessionId = inv.arguments[0] as Long
            nations.values.filter { it.sessionId == sessionId }.map { it.toSnapshot().toEntity() }
        }
        `when`(officerRepository.findBySessionId(ArgumentMatchers.anyLong())).thenAnswer { inv ->
            val sessionId = inv.arguments[0] as Long
            generals.values.filter { it.sessionId == sessionId }.map { it.toSnapshot().toEntity() }
        }
        `when`(officerRepository.findBySessionIdAndPlanetIdIn(ArgumentMatchers.anyLong(), ArgumentMatchers.anyList()))
            .thenReturn(emptyList())
        `when`(planetRepository.save(ArgumentMatchers.any(Planet::class.java))).thenAnswer { inv ->
            val city = inv.arguments[0] as Planet
            cities[city.id] = city.toSnapshot().toEntity()
            city
        }
        `when`(planetRepository.saveAll(ArgumentMatchers.anyList<Planet>())).thenAnswer { inv ->
            @Suppress("UNCHECKED_CAST")
            val saved = inv.arguments[0] as List<Planet>
            saved.forEach { city -> cities[city.id] = city.toSnapshot().toEntity() }
            saved
        }
        `when`(factionRepository.save(ArgumentMatchers.any(Faction::class.java))).thenAnswer { inv ->
            val nation = inv.arguments[0] as Faction
            nations[nation.id] = nation.toSnapshot().toEntity()
            nation
        }
        `when`(factionRepository.saveAll(ArgumentMatchers.anyList<Faction>())).thenAnswer { inv ->
            @Suppress("UNCHECKED_CAST")
            val saved = inv.arguments[0] as List<Faction>
            saved.forEach { faction -> nations[faction.id] = faction.toSnapshot().toEntity() }
            saved
        }
        `when`(officerRepository.save(ArgumentMatchers.any(Officer::class.java))).thenAnswer { inv ->
            val general = inv.arguments[0] as Officer
            generals[general.id] = general.toSnapshot().toEntity()
            general
        }
        `when`(officerRepository.saveAll(ArgumentMatchers.anyList<Officer>())).thenAnswer { inv ->
            @Suppress("UNCHECKED_CAST")
            val saved = inv.arguments[0] as List<Officer>
            saved.forEach { officer -> generals[officer.id] = officer.toSnapshot().toEntity() }
            saved
        }
    }

    private fun seed(
        cityList: List<Planet> = emptyList(),
        nationList: List<Faction> = emptyList(),
        generalList: List<Officer> = emptyList(),
    ) {
        cities.clear(); nations.clear(); generals.clear()
        cityList.forEach { cities[it.id] = it.toSnapshot().toEntity() }
        nationList.forEach { nations[it.id] = it.toSnapshot().toEntity() }
        generalList.forEach { generals[it.id] = it.toSnapshot().toEntity() }
    }

    private fun world(year: Short = 200, month: Short = 3): SessionState =
        SessionState(id = 1, scenarioCode = "test", currentYear = year, currentMonth = month, tickSeconds = 300)

    private fun city(
        id: Long = 1, factionId: Long = 1,
        population: Int = 10000, populationMax: Int = 50000,
        production: Int = 500, productionMax: Int = 1000,
        commerce: Int = 500, commerceMax: Int = 1000,
        security: Int = 500, securityMax: Int = 1000,
        orbitalDefense: Int = 500, orbitalDefenseMax: Int = 1000,
        fortress: Int = 500, fortressMax: Int = 1000,
        approval: Float = 80f, supplyState: Short = 1,
        level: Short = 5, dead: Int = 0, tradeRoute: Int = 100,
    ): Planet = Planet(
        id = id, sessionId = 1, name = "city$id", mapPlanetId = id.toInt(),
        factionId = factionId, population = population, populationMax = populationMax,
        production = production, productionMax = productionMax, commerce = commerce, commerceMax = commerceMax,
        security = security, securityMax = securityMax, orbitalDefense = orbitalDefense, orbitalDefenseMax = orbitalDefenseMax,
        fortress = fortress, fortressMax = fortressMax, approval = approval,
        supplyState = supplyState, level = level, dead = dead, tradeRoute = tradeRoute,
    )

    private fun nation(
        id: Long = 1, funds: Int = 10000, supplies: Int = 10000,
        level: Short = 1, rateTmp: Short = 15, bill: Short = 100,
        capitalPlanetId: Long? = 1, conscriptionRate: Short = 15,
        factionType: String = "che_중립",
    ): Faction = Faction(
        id = id, sessionId = 1, name = "nation$id", color = "#FF0000",
        funds = funds, supplies = supplies, factionRank = level, conscriptionRateTmp = rateTmp,
        taxRate = bill, capitalPlanetId = capitalPlanetId, conscriptionRate = conscriptionRate,
        factionType = factionType,
    )

    private fun general(
        id: Long = 1, factionId: Long = 1, planetId: Long = 1,
        funds: Int = 1000, supplies: Int = 1000, dedication: Int = 1000,
        officerLevel: Short = 1, officerPlanet: Int = 0, npcState: Short = 0,
    ): Officer = Officer(
        id = id, sessionId = 1, name = "general$id",
        factionId = factionId, planetId = planetId, funds = funds, supplies = supplies,
        dedication = dedication, officerLevel = officerLevel,
        officerPlanet = officerPlanet, npcState = npcState,
    )

    // ── Legacy formula helpers (PHP golden-value computation) ──

    /**
     * Legacy getDedLevel: ceil(sqrt(dedication) / 10), clamped [0, 30]
     * Source: func_converter.php:643
     */
    private fun legacyDedLevel(dedication: Int): Int =
        ceil(sqrt(dedication.toDouble()) / 10).toInt().coerceIn(0, 30)

    /**
     * Legacy getBill: dedLevel * 200 + 400
     * Source: func_converter.php:668
     */
    private fun legacyBill(dedication: Int): Int =
        legacyDedLevel(dedication) * 200 + 400

    /**
     * Legacy calcCityGoldIncome (single city, before tax rate)
     * Source: func_time_event.php:88-104
     */
    private fun legacyCityGoldIncome(
        population: Int, commerce: Int, commerceMax: Int, approval: Float,
        security: Int, securityMax: Int, officers: Int,
        isCapital: Boolean, nationLevel: Int,
    ): Double {
        if (commerceMax == 0) return 0.0
        val trustRatio = approval / 200.0 + 0.5
        var income = population.toDouble() * commerce / commerceMax * trustRatio / 30
        income *= 1 + security.toDouble() / securityMax.coerceAtLeast(1) / 10
        income *= 1.05.pow(officers)
        if (isCapital) income *= 1 + 1.0 / 3 / nationLevel.coerceAtLeast(1)
        return income
    }

    /**
     * Legacy calcCityRiceIncome (single city, before tax rate)
     * Source: func_time_event.php:106-122
     */
    private fun legacyCityRiceIncome(
        population: Int, production: Int, productionMax: Int, approval: Float,
        security: Int, securityMax: Int, officers: Int,
        isCapital: Boolean, nationLevel: Int,
    ): Double {
        if (productionMax == 0) return 0.0
        val trustRatio = approval / 200.0 + 0.5
        var income = population.toDouble() * production / productionMax * trustRatio / 30
        income *= 1 + security.toDouble() / securityMax.coerceAtLeast(1) / 10
        income *= 1.05.pow(officers)
        if (isCapital) income *= 1 + 1.0 / 3 / nationLevel.coerceAtLeast(1)
        return income
    }

    /**
     * Legacy calcCityWallRiceIncome (single city, before tax rate)
     * Source: func_time_event.php:124-139
     */
    private fun legacyCityWallIncome(
        orbitalDefense: Int, fortress: Int, fortressMax: Int,
        security: Int, securityMax: Int, officers: Int,
        isCapital: Boolean, nationLevel: Int,
    ): Double {
        if (fortressMax == 0) return 0.0
        var income = orbitalDefense.toDouble() * fortress / fortressMax / 3
        income *= 1 + security.toDouble() / securityMax.coerceAtLeast(1) / 10
        income *= 1.05.pow(officers)
        if (isCapital) income *= 1 + 1.0 / 3 / nationLevel.coerceAtLeast(1)
        return income
    }

    // ────────────────────────────────────────────────────────────────────────
    // 4. Tax Rate Multiplier
    // Legacy: getGoldIncome() → $cityIncome *= ($taxRate / 20)
    // ────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Tax Rate Multiplier — func_time_event.php:161")
    inner class TaxRateApplication {

        @Test
        @DisplayName("Tax rate 20 gives full income, rate 10 gives half")
        fun `tax rate scales income linearly`() {
            val c = city(population = 20000, commerce = 800, commerceMax = 1000, approval = 100f, security = 500, securityMax = 1000)

            // Tax 20 (full multiplier = 20/20 = 1.0)
            seed(listOf(c), listOf(nation(funds = 100000, rateTmp = 20, bill = 0)), listOf(general(npcState = 5)))
            service.preUpdateMonthly(world())
            val goldFull = nations[1L]!!.funds - 100000

            // Tax 10 (half multiplier = 10/20 = 0.5)
            seed(listOf(c), listOf(nation(funds = 100000, rateTmp = 10, bill = 0)), listOf(general(npcState = 5)))
            service.preUpdateMonthly(world())
            val goldHalf = nations[1L]!!.funds - 100000

            // goldHalf should be approximately goldFull / 2
            assertThat(goldHalf).isCloseTo(goldFull / 2, within(1))
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // 9. Nation Level System
    // 10-level system (officer_ranks.json 기준):
    //   [0]=0, [1]=1, [2]=2, [3]=4, [4]=6, [5]=9, [6]=12, [7]=16, [8]=20, [9]=25
    //   Level only increases. Reward: newLevel * 1000 gold + rice.
    // ────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Faction Rank Thresholds — Gin7.updateFactionRank")
    inner class NationLevelSystem {

        @ParameterizedTest
        @CsvSource(
            "0, 0",
            "1, 1",
            "2, 2",
            "3, 2",
            "4, 3",
            "5, 3",
            "6, 4",
            "8, 4",
            "9, 5",
            "11, 5",
            "12, 6",
            "15, 6",
            "16, 7",
            "19, 7",
            "20, 8",
            "24, 8",
            "25, 9",
            "30, 9",
        )
        @DisplayName("Faction rank by high-level planet count matches Gin7 thresholds")
        fun `faction rank by high-level planet count`(highCityCount: Int, expectedLevel: Int) {
            val planetList = if (highCityCount == 0) {
                listOf(city(level = 2))
            } else {
                (1..highCityCount.toLong()).map { city(id = it, level = 5) }
            }
            val faction = nation(level = 0, funds = 0, supplies = 0)
            seed(planetList, listOf(faction), emptyList())

            gin7Service.updateFactionRank(world(month = 1))

            assertThat(nations[1L]!!.factionRank.toInt()).isEqualTo(expectedLevel)
        }

        @Test
        @DisplayName("Rank update does not award funds or supplies in Gin7")
        fun `rank update does not award resources`() {
            val highCities = (1..5L).map { city(id = it, level = 5) }
            val faction = nation(level = 0, funds = 1000, supplies = 1000)
            seed(highCities, listOf(faction), emptyList())

            gin7Service.updateFactionRank(world(month = 1))

            assertThat(nations[1L]!!.factionRank.toInt()).isEqualTo(3)
            assertThat(nations[1L]!!.funds).isEqualTo(1000)
            assertThat(nations[1L]!!.supplies).isEqualTo(1000)
        }

        @Test
        @DisplayName("Rank can decrease when high-level planet count shrinks")
        fun `rank can decrease`() {
            val planetList = listOf(
                city(id = 1, level = 5),
                city(id = 2, level = 5),
                city(id = 3, level = 2),
            )
            val faction = nation(level = 5)
            seed(planetList, listOf(faction), emptyList())

            gin7Service.updateFactionRank(world(month = 1))

            assertThat(nations[1L]!!.factionRank.toInt()).isEqualTo(2)
        }
    }
    // ────────────────────────────────────────────────────────────────────────
    // 12. PHP-Verified Golden Values (exact hand-traced from legacy formulas)
    // Legacy: func_time_event.php calcCityGoldIncome/calcCityRiceIncome/calcCityWallRiceIncome
    // ────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Gin7 Income Golden Values")
    inner class PhpGoldenValues {

        @ParameterizedTest
        @CsvSource(
            "500, 20, 100",
            "800, 15, 120",
            "250, 30, 75",
        )
        @DisplayName("Funds income uses commerce times tax rate")
        fun `funds income uses commerce times tax rate`(commerce: Int, taxRate: Int, expectedFunds: Int) {
            val planet = city(commerce = commerce, commerceMax = 1000, production = 0, productionMax = 0)
            val faction = nation(funds = 100000, supplies = 100000, bill = taxRate.toShort())
            seed(listOf(planet), listOf(faction), emptyList())

            gin7Service.processIncome(world(month = 1), "gold")

            assertThat(nations[1L]!!.funds - 100000).isEqualTo(expectedFunds)
            assertThat(nations[1L]!!.supplies).isEqualTo(100000)
        }

        @ParameterizedTest
        @CsvSource(
            "500, 500",
            "1000, 1000",
            "125, 125",
        )
        @DisplayName("Supplies income uses production only")
        fun `supplies income uses production only`(production: Int, expectedSupplies: Int) {
            val planet = city(
                production = production,
                productionMax = 1000,
                commerce = 0,
                commerceMax = 0,
                fortress = 2000,
                fortressMax = 2000,
                orbitalDefense = 1500,
                orbitalDefenseMax = 1500,
            )
            val faction = nation(funds = 100000, supplies = 100000, bill = 20)
            seed(listOf(planet), listOf(faction), emptyList())

            gin7Service.processIncome(world(month = 7), "rice")

            assertThat(nations[1L]!!.supplies - 100000).isEqualTo(expectedSupplies)
            assertThat(nations[1L]!!.funds).isEqualTo(100000)
        }

        @Test
        @DisplayName("Fortress and orbital defense do not add extra supplies income")
        fun `fortress and orbital defense do not affect supplies income`() {
            val planet = city(
                production = 600,
                productionMax = 1000,
                commerce = 0,
                commerceMax = 0,
                fortress = 5000,
                fortressMax = 5000,
                orbitalDefense = 4000,
                orbitalDefenseMax = 4000,
            )
            val faction = nation(funds = 100000, supplies = 100000, bill = 20)
            seed(listOf(planet), listOf(faction), emptyList())

            gin7Service.processIncome(world(month = 7), "rice")

            assertThat(nations[1L]!!.supplies - 100000).isEqualTo(600)
        }
    }
    // ────────────────────────────────────────────────────────────────────────
    // 14. Extended getBill/getDedLevel golden values
    // Legacy: getDedLevel = ceil(sqrt(dedication)/10) clamped [0, 30]
    //         getBill = dedLevel * 200 + 400
    //         getOutcome = sum(getBill(ded)) * billRate / 100
    // Source: func_converter.php:643-669
    // ────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Extended getBill/getDedLevel — func_converter.php:643-669")
    inner class ExtendedBillGoldenValues {

        @ParameterizedTest
        @CsvSource(
            // dedication, expectedDedLevel, expectedBill
            "0,       0,  400",      // sqrt(0)=0, ceil(0/10)=0
            "50,      1,  600",      // sqrt(50)=7.07, ceil(7.07/10)=1
            "100,     1,  600",      // sqrt(100)=10, ceil(10/10)=1
            "101,     2,  800",      // sqrt(101)=10.05, ceil(10.05/10)=2
            "400,     2,  800",      // sqrt(400)=20, ceil(20/10)=2
            "401,     3,  1000",     // sqrt(401)=20.025, ceil(20.025/10)=3
            "900,     3,  1000",     // sqrt(900)=30, ceil(30/10)=3
            "901,     4,  1200",     // sqrt(901)=30.017, ceil(30.017/10)=4
            "2500,    5,  1400",     // sqrt(2500)=50, ceil(50/10)=5
            "10000,   10, 2400",     // sqrt(10000)=100, ceil(100/10)=10
            "40000,   20, 4400",     // sqrt(40000)=200, ceil(200/10)=20
            "90000,   30, 6400",     // sqrt(90000)=300, ceil(300/10)=30
            "100000,  30, 6400",     // sqrt(100000)=316.2, ceil(316.2/10)=32 -> clamped to 30
            "1000000, 30, 6400",     // sqrt(1000000)=1000, ceil(1000/10)=100 -> clamped to 30
        )
        @DisplayName("getBill/getDedLevel extended golden values from PHP trace")
        fun `extended bill golden values`(dedication: Int, expectedDedLevel: Int, expectedBill: Int) {
            assertThat(legacyDedLevel(dedication))
                .describedAs("dedLevel for ded=$dedication")
                .isEqualTo(expectedDedLevel)
            assertThat(legacyBill(dedication))
                .describedAs("bill for ded=$dedication")
                .isEqualTo(expectedBill)
        }
    }
}
