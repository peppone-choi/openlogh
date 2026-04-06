package com.openlogh.qa.parity

import com.openlogh.engine.EconomyService
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
        `when`(factionRepository.save(ArgumentMatchers.any(Faction::class.java))).thenAnswer { inv ->
            val nation = inv.arguments[0] as Faction
            nations[nation.id] = nation.toSnapshot().toEntity()
            nation
        }
        `when`(officerRepository.save(ArgumentMatchers.any(Officer::class.java))).thenAnswer { inv ->
            val general = inv.arguments[0] as Officer
            generals[general.id] = general.toSnapshot().toEntity()
            general
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
    // 1. City Gold Income
    // Legacy: func_time_event.php:88 calcCityGoldIncome()
    // ────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("City Gold Income — func_time_event.php:88")
    inner class CityGoldIncome {

        @ParameterizedTest
        @CsvSource(
            // population, commerce, commerceMax, approval, security, securityMax, officers, isCapital, nationLevel, taxRate
            "10000, 500, 1000, 80,  500, 1000, 0, false, 1, 20",
            "50000, 800, 1000, 100, 900, 1000, 3, true,  3, 20",
            "5000,  200, 1000, 50,  100, 1000, 0, false, 5, 10",
            "30000, 1000,1000, 100, 1000,1000, 2, true,  7, 30",
            "1000,  100, 500,  30,  50,  200,  1, false, 2, 15",
        )
        @DisplayName("Gold income golden value matches legacy formula exactly")
        fun `gold income golden values`(
            population: Int, commerce: Int, commerceMax: Int, approval: Float, security: Int, securityMax: Int,
            officers: Int, isCapital: Boolean, nationLevel: Int, taxRate: Int,
        ) {
            val capitalPlanetId = if (isCapital) 1L else 99L
            val c = city(population = population, commerce = commerce, commerceMax = commerceMax, approval = approval, security = security, securityMax = securityMax)
            val n = nation(
                funds = 100000, supplies = 100000,  // large treasury to avoid salary affecting result
                rateTmp = taxRate.toShort(), capitalPlanetId = capitalPlanetId,
                level = nationLevel.toShort(), bill = 0,  // zero bill so no salary deduction
            )
            val officerGenerals = (1..officers).map { idx ->
                general(id = (10 + idx).toLong(), officerLevel = 3, officerPlanet = 1)
            }
            val allGenerals = listOf(general(npcState = 5)) + officerGenerals +
                listOf(general(id = 99, dedication = 0, npcState = 0)) // keep nationGenerals non-empty

            seed(listOf(c), listOf(n), allGenerals)
            service.preUpdateMonthly(world())

            val expected = legacyCityGoldIncome(population, commerce, commerceMax, approval, security, securityMax, officers, isCapital, nationLevel)
            val expectedTaxed = (expected * taxRate / 20).toInt()
            val actual = nations[1L]!!.funds - 100000

            assertThat(actual)
                .describedAs("Gold income: population=$population commerce=$commerce/$commerceMax approval=$approval security=$security/$securityMax off=$officers cap=$isCapital lv=$nationLevel tax=$taxRate -> expected=$expectedTaxed")
                .isCloseTo(expectedTaxed, within(1))
        }

        @Test
        @DisplayName("Unsupplied city produces zero income — legacy supply==0 guard")
        fun `unsupplied city zero income`() {
            val c = city(supplyState = 0)
            val n = nation(funds = 100000, bill = 0)
            seed(listOf(c), listOf(n), listOf(general(npcState = 5)))

            service.preUpdateMonthly(world())
            assertThat(nations[1L]!!.funds).isEqualTo(100000)
        }

        @Test
        @DisplayName("Zero commerceMax produces zero gold income (no division by zero)")
        fun `zero commerceMax safe`() {
            val c = city(commerceMax = 0)
            val n = nation(funds = 100000, bill = 0)
            seed(listOf(c), listOf(n), listOf(general(npcState = 5)))

            service.preUpdateMonthly(world())
            // Should not throw, no gold income from commerce
            assertThat(nations[1L]!!.funds).isEqualTo(100000)
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // 2. City Rice Income (production-based)
    // Legacy: func_time_event.php:106 calcCityRiceIncome()
    // ────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("City Rice Income — func_time_event.php:106")
    inner class CityRiceIncome {

        @ParameterizedTest
        @CsvSource(
            "10000, 500, 1000, 80,  500, 1000, 0, false, 1, 20",
            "50000, 800, 1000, 100, 900, 1000, 2, true,  3, 20",
            "30000, 1000,1000, 60,  800, 1000, 1, false, 5, 15",
        )
        @DisplayName("Rice income golden value matches legacy formula exactly")
        fun `rice income golden values`(
            population: Int, production: Int, productionMax: Int, approval: Float, security: Int, securityMax: Int,
            officers: Int, isCapital: Boolean, nationLevel: Int, taxRate: Int,
        ) {
            val capitalPlanetId = if (isCapital) 1L else 99L
            val c = city(population = population, production = production, productionMax = productionMax, approval = approval, security = security, securityMax = securityMax,
                fortress = 0, fortressMax = 0)  // zero fortress to isolate rice-from-production
            val n = nation(
                funds = 100000, supplies = 100000,
                rateTmp = taxRate.toShort(), capitalPlanetId = capitalPlanetId,
                level = nationLevel.toShort(), bill = 0,
            )
            val officerGenerals = (1..officers).map { idx ->
                general(id = (10 + idx).toLong(), officerLevel = 3, officerPlanet = 1)
            }

            seed(listOf(c), listOf(n), listOf(general(npcState = 5)) + officerGenerals +
                listOf(general(id = 99, dedication = 0, npcState = 0))) // keep nationGenerals non-empty
            service.preUpdateMonthly(world())

            val expected = legacyCityRiceIncome(population, production, productionMax, approval, security, securityMax, officers, isCapital, nationLevel)
            val expectedTaxed = (expected * taxRate / 20).toInt()
            val actual = nations[1L]!!.supplies - 100000

            assertThat(actual)
                .describedAs("Rice income: population=$population production=$production/$productionMax")
                .isCloseTo(expectedTaxed, within(1))
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // 3. Wall Income (defense-based rice)
    // Legacy: func_time_event.php:124 calcCityWallRiceIncome()
    // ────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Wall Income — func_time_event.php:124")
    inner class WallIncome {

        @ParameterizedTest
        @CsvSource(
            "500, 500, 1000, 500, 1000, 0, false, 1, 20",
            "800, 800, 1000, 900, 1000, 2, true,  3, 20",
            "1000,1000,1000, 1000,1000, 3, true,  7, 15",
        )
        @DisplayName("Wall income golden value matches legacy formula exactly")
        fun `fortress income golden values`(
            orbitalDefense: Int, fortress: Int, fortressMax: Int,
            security: Int, securityMax: Int, officers: Int,
            isCapital: Boolean, nationLevel: Int, taxRate: Int,
        ) {
            val capitalPlanetId = if (isCapital) 1L else 99L
            // Zero production/commerce to isolate fortress income
            val c = city(population = 1000, production = 0, productionMax = 0, commerce = 0, commerceMax = 0,
                orbitalDefense = orbitalDefense, fortress = fortress, fortressMax = fortressMax, security = security, securityMax = securityMax)
            val n = nation(
                funds = 100000, supplies = 100000,
                rateTmp = taxRate.toShort(), capitalPlanetId = capitalPlanetId,
                level = nationLevel.toShort(), bill = 0,
            )
            val officerGenerals = (1..officers).map { idx ->
                general(id = (10 + idx).toLong(), officerLevel = 3, officerPlanet = 1)
            }

            seed(listOf(c), listOf(n), listOf(general(npcState = 5)) + officerGenerals +
                listOf(general(id = 99, dedication = 0, npcState = 0))) // keep nationGenerals non-empty
            service.preUpdateMonthly(world())

            val expected = legacyCityWallIncome(orbitalDefense, fortress, fortressMax, security, securityMax, officers, isCapital, nationLevel)
            val expectedTaxed = (expected * taxRate / 20).toInt()
            val actual = nations[1L]!!.supplies - 100000

            assertThat(actual)
                .describedAs("Wall income: orbitalDefense=$orbitalDefense fortress=$fortress/$fortressMax")
                .isCloseTo(expectedTaxed, within(1))
        }
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
    // 5. War Income
    // Legacy: calcCityWarGoldIncome() → dead / 10
    // Legacy: dead resets to 0; population += dead * 0.2 (capped at populationMax)
    // ────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("War Income — func_time_event.php:78")
    inner class WarIncome {

        @ParameterizedTest
        @CsvSource(
            "1000,  10000, 50000, 100",   // dead=1000 -> gold+=100, population+=200
            "5000,  30000, 50000, 500",   // dead=5000 -> gold+=500, population+=1000
            "500,   49900, 50000, 50",    // dead=500  -> gold+=50, population+=100 (no cap)
        )
        @DisplayName("War gold income = dead / 10")
        fun `war income from dead`(dead: Int, population: Int, populationMax: Int, expectedGold: Int) {
            val c = city(dead = dead, population = population, populationMax = populationMax, commerce = 0, commerceMax = 0, production = 0, productionMax = 0, orbitalDefense = 0, orbitalDefenseMax = 0, fortress = 0, fortressMax = 0)
            val n = nation(funds = 100000, supplies = 100000, bill = 0)
            seed(listOf(c), listOf(n), listOf(general(npcState = 0, dedication = 0)))

            service.preUpdateMonthly(world())

            assertThat(nations[1L]!!.funds - 100000).isEqualTo(expectedGold)
            assertThat(cities[1L]!!.dead).isEqualTo(0)
        }

        @Test
        @DisplayName("Pop gain from dead troops capped by populationMax")
        fun `population gain capped at populationMax`() {
            val c = city(dead = 50000, population = 49900, populationMax = 50000, commerce = 0, commerceMax = 0, production = 0, productionMax = 0, orbitalDefense = 0, orbitalDefenseMax = 0, fortress = 0, fortressMax = 0)
            val n = nation(funds = 100000, bill = 0)
            seed(listOf(c), listOf(n), listOf(general(npcState = 0, dedication = 0)))

            service.preUpdateMonthly(world())

            assertThat(cities[1L]!!.population).isLessThanOrEqualTo(50000)
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // 6. Salary (Bill) Formulas
    // Legacy: getDedLevel() = ceil(sqrt(dedication)/10) clamped [0,30]
    //         getBill() = getDedLevel(dedication) * 200 + 400
    //         getOutcome() = sum(getBill(ded)) * billRate / 100
    // ────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Salary Formula — func_converter.php:643,668")
    inner class SalaryFormula {

        @ParameterizedTest
        @CsvSource(
            "0,     0, 400",     // dedLevel=ceil(0/10)=0, bill=0*200+400=400
            "100,   1, 600",     // dedLevel=ceil(10/10)=1, bill=1*200+400=600
            "1000,  4, 1200",    // dedLevel=ceil(31.62/10)=4, bill=4*200+400=1200
            "10000, 10, 2400",   // dedLevel=ceil(100/10)=10
            "90000, 30, 6400",   // dedLevel=ceil(300/10)=30 (max)
            "100000,30, 6400",   // dedLevel=ceil(316.2/10)=32 -> clamped to 30
        )
        @DisplayName("getBill(dedication) golden values")
        fun `bill golden values`(dedication: Int, expectedDedLevel: Int, expectedBill: Int) {
            assertThat(legacyDedLevel(dedication)).isEqualTo(expectedDedLevel)
            assertThat(legacyBill(dedication)).isEqualTo(expectedBill)
        }

        @Test
        @DisplayName("Salary distributed at ratio = realOutcome / totalBill")
        fun `salary distribution ratio`() {
            // Nation: funds = 50000, BASE_GOLD = 0
            // 1 general: ded=1000, bill=1200
            // outcome = bill * (nation.taxRate/100) = 1200 * 1.0 = 1200
            // Nation gold after: 50000 + income - 1200
            // General gold after: 1000 + 1200 * ratio
            val c = city(population = 20000, commerce = 800, commerceMax = 1000, approval = 100f)
            val n = nation(funds = 50000, supplies = 50000, bill = 100)
            val g = general(dedication = 1000, funds = 1000, supplies = 1000)
            seed(listOf(c), listOf(n), listOf(g))

            service.preUpdateMonthly(world())

            val updatedGeneral = generals[1L]!!
            // General should receive salary based on getBill(1000) = 1200
            assertThat(updatedGeneral.funds).isGreaterThan(1000)
            assertThat(updatedGeneral.supplies).isGreaterThan(1000)
        }

        @Test
        @DisplayName("Nation gold below BASE_GOLD(0) pays zero salary")
        fun `zero salary when treasury empty`() {
            // Treasury at 0, no cities -> no income -> gold stays at 0
            val n = nation(funds = 0, supplies = 0, bill = 100)
            val g = general(funds = 500, supplies = 500, dedication = 1000)
            seed(emptyList(), listOf(n), listOf(g))

            service.preUpdateMonthly(world())

            // General should receive 0 salary (nation has no income, gold < BASE_GOLD after no income)
            assertThat(generals[1L]!!.funds).isEqualTo(500)
        }

        @Test
        @DisplayName("Partial salary when treasury insufficient: ratio = (gold - BASE_GOLD) / totalBill")
        fun `partial salary when partially broke`() {
            // Nation funds = 100, bill = 100, 1 general ded=1000 -> totalBill=1200, outcome=1200
            // gold < outcome: realOutcome = 100 - 0 = 100, ratio = 100/1200
            // No city income -> general gets 100 * 100/1200 = 8.33 -> 8
            val n = nation(funds = 100, supplies = 100, bill = 100)
            val g = general(funds = 0, supplies = 0, dedication = 1000)
            // Need at least one city so nationCities is non-null; zero commerceMax/productionMax/fortressMax = zero income
            val zeroCity = city(commerceMax = 0, productionMax = 0, fortressMax = 0)
            seed(listOf(zeroCity), listOf(n), listOf(g))

            service.preUpdateMonthly(world())

            assertThat(generals[1L]!!.funds).isLessThan(legacyBill(1000))
            assertThat(nations[1L]!!.funds).isEqualTo(0) // BASE_GOLD
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // 7. General Resource Decay
    // Legacy ProcessSemiAnnual.php:89-91:
    //   IF resource > 10000 THEN resource * 0.97
    //   ELSE IF resource > 1000 THEN resource * 0.99
    // ────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("General Resource Decay — ProcessSemiAnnual.php:89")
    inner class GeneralResourceDecay {

        @ParameterizedTest
        @CsvSource(
            "20000, 19400",    // > 10000: 20000 * 0.97 = 19400
            "10001, 9700",     // > 10000: 10001 * 0.97 = 9700 (truncated)
            "10000, 9900",     // == 10000: NOT > 10000, but > 1000: 10000 * 0.99 = 9900
            "5000,  4950",     // > 1000: 5000 * 0.99 = 4950
            "1001,  990",      // > 1000: 1001 * 0.99 = 990 (truncated)
            "1000,  1000",     // == 1000: no decay
            "500,   500",      // < 1000: no decay
            "0,     0",        // 0: no decay
        )
        @DisplayName("General gold decay tiers match legacy thresholds")
        fun `general gold decay`(initial: Int, expected: Int) {
            val c = city()
            val n = nation()
            val g = general(id = 1, funds = initial, supplies = 0)
            seed(listOf(c), listOf(n), listOf(g))

            service.postUpdateMonthly(world(month = 1))

            assertThat(generals[1L]!!.funds).isEqualTo(expected)
        }

        @ParameterizedTest
        @CsvSource(
            "15000, 14550",    // > 10000: 15000 * 0.97 = 14550
            "3000,  2970",     // > 1000: 3000 * 0.99 = 2970
            "800,   800",      // <= 1000: no decay
        )
        @DisplayName("General rice decay tiers match legacy thresholds")
        fun `general rice decay`(initial: Int, expected: Int) {
            val c = city()
            val n = nation()
            val g = general(id = 1, funds = 0, supplies = initial)
            seed(listOf(c), listOf(n), listOf(g))

            service.postUpdateMonthly(world(month = 1))

            assertThat(generals[1L]!!.supplies).isEqualTo(expected)
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // 8. Nation Resource Decay
    // Legacy ProcessSemiAnnual.php:94-96:
    //   IF resource > 100000 THEN * 0.95
    //   ELSE IF > 10000 THEN * 0.97
    //   ELSE IF > 1000 THEN * 0.99
    // ────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Nation Resource Decay — ProcessSemiAnnual.php:94")
    inner class NationResourceDecay {

        @ParameterizedTest
        @CsvSource(
            "200000, 190000",    // > 100000: 200000 * 0.95 = 190000
            "100001, 95000",     // > 100000: 100001 * 0.95 = 95000 (truncated)
            "100000, 97000",     // == 100000: NOT > 100000, > 10000: 100000 * 0.97
            "50000,  48500",     // > 10000: 50000 * 0.97 = 48500
            "10001,  9700",      // > 10000: 10001 * 0.97 = 9700 (truncated)
            "10000,  9900",      // == 10000: > 1000: 10000 * 0.99 = 9900
            "5000,   4950",      // > 1000: 5000 * 0.99 = 4950
            "1000,   1000",      // == 1000: no decay
            "500,    500",       // < 1000: no decay
        )
        @DisplayName("Nation gold decay tiers match legacy 3-tier thresholds")
        fun `nation gold decay`(initial: Int, expected: Int) {
            val c = city()
            val g = general()
            seed(listOf(c), listOf(nation(funds = initial)), listOf(g))

            service.postUpdateMonthly(world(month = 1))

            assertThat(nations[1L]!!.funds).isEqualTo(expected)
        }

        @ParameterizedTest
        @CsvSource(
            "200000, 190000",
            "50000,  48500",
            "5000,   4950",
            "500,    500",
        )
        @DisplayName("Nation rice decay tiers match legacy")
        fun `nation rice decay`(initial: Int, expected: Int) {
            val c = city()
            val g = general()
            seed(listOf(c), listOf(nation(supplies = initial)), listOf(g))

            service.postUpdateMonthly(world(month = 1))

            assertThat(nations[1L]!!.supplies).isEqualTo(expected)
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // 9. Nation Level System
    // 10-level system (officer_ranks.json 기준):
    //   [0]=0, [1]=1, [2]=2, [3]=4, [4]=6, [5]=9, [6]=12, [7]=16, [8]=20, [9]=25
    //   Level only increases. Reward: newLevel * 1000 gold + rice.
    // ────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Nation Level — 10-level system (officer_ranks.json)")
    inner class NationLevelSystem {

        @ParameterizedTest
        @CsvSource(
            "0, 0",    // 0 high cities -> level 0 (방랑군)
            "1, 1",    // 1 high city -> level 1 (도위)
            "2, 2",    // 2 high cities -> level 2 (주자사)
            "3, 2",    // 3 high cities -> still level 2 (next threshold is 4)
            "4, 3",    // 4 high cities -> level 3 (주목)
            "5, 3",    // 5 -> still level 3
            "6, 4",    // 6 -> level 4 (중랑장)
            "8, 4",    // 8 -> still level 4
            "9, 5",    // 9 -> level 5 (대장군)
            "11, 5",   // 11 -> still level 5
            "12, 6",   // 12 -> level 6 (대사마)
            "15, 6",   // 15 -> still level 6
            "16, 7",   // 16 -> level 7 (공)
            "19, 7",   // 19 -> still level 7
            "20, 8",   // 20 -> level 8 (왕)
            "24, 8",   // 24 -> still level 8
            "25, 9",   // 25 -> level 9 (황제)
            "30, 9",   // 30 -> still level 9 (max)
        )
        @DisplayName("Nation level by high city count matches threshold table")
        fun `nation level by high city count`(highCityCount: Int, expectedLevel: Int) {
            // Create highCityCount cities with level >= 4
            val cityList = if (highCityCount == 0) {
                listOf(city(level = 2))  // one low-level city
            } else {
                (1..highCityCount.toLong()).map { city(id = it, level = 5) }
            }
            val n = nation(level = 0, funds = 0, supplies = 0)
            val g = general()
            seed(cityList, listOf(n), listOf(g))

            service.postUpdateMonthly(world(month = 3))

            assertThat(nations[1L]!!.factionRank.toInt()).isEqualTo(expectedLevel)
        }

        @Test
        @DisplayName("Level-up reward: newLevel * 1000 gold and rice")
        fun `level up reward`() {
            // 5 high cities -> level 3, reward = 3 * 1000 = 3000
            val highCities = (1..5L).map { city(id = it, level = 5) }
            val n = nation(level = 0, funds = 1000, supplies = 1000)
            val g = general()
            seed(highCities, listOf(n), listOf(g))

            service.postUpdateMonthly(world(month = 3))

            assertThat(nations[1L]!!.funds).isGreaterThanOrEqualTo(1000 + 3000)
            assertThat(nations[1L]!!.supplies).isGreaterThanOrEqualTo(1000 + 3000)
        }

        @Test
        @DisplayName("Level only increases, never decreases")
        fun `level never decreases`() {
            // 1 high city = level 1, but nation is already level 5
            val cityList = listOf(city(level = 5))
            val n = nation(level = 5)
            val g = general()
            seed(cityList, listOf(n), listOf(g))

            service.postUpdateMonthly(world(month = 3))

            assertThat(nations[1L]!!.factionRank.toInt()).isEqualTo(5)
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // 10. Officer Bonus
    // Legacy: income *= pow(1.05, officerCnt)
    //   Officers = generals with officer_level IN (2,3,4) AND city == officer_city
    // ────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Officer Bonus — func_time_event.php:97")
    inner class OfficerBonus {

        @Test
        @DisplayName("Officers (level 2-4) in assigned city give 1.05^N multiplier")
        fun `officer count multiplier`() {
            val c = city(population = 20000, commerce = 800, commerceMax = 1000, approval = 100f)

            val dummy = general(id = 99, dedication = 0, npcState = 0) // keep nationGenerals non-empty

            // No officers
            seed(listOf(c), listOf(nation(funds = 100000, bill = 0)), listOf(general(npcState = 5), dummy))
            service.preUpdateMonthly(world())
            val goldNoOfficer = nations[1L]!!.funds - 100000

            // 3 officers assigned to city 1
            val officers = (10..12L).map { general(id = it, officerLevel = 3, officerPlanet = 1) }
            seed(listOf(c), listOf(nation(funds = 100000, bill = 0)), listOf(general(npcState = 5), dummy) + officers)
            service.preUpdateMonthly(world())
            val goldWithOfficers = nations[1L]!!.funds - 100000

            // 1.05^3 ≈ 1.1576
            val expectedRatio = 1.05.pow(3)
            assertThat(goldWithOfficers.toDouble() / goldNoOfficer)
                .isCloseTo(expectedRatio, within(0.01))
        }

        @Test
        @DisplayName("Officers NOT in their assigned city do not count")
        fun `officers elsewhere ignored`() {
            val c = city(population = 20000, commerce = 800, commerceMax = 1000, approval = 100f)

            val dummy = general(id = 99, dedication = 0, npcState = 0) // keep nationGenerals non-empty

            // Officer assigned to city 1 but located in city 99
            val officer = general(id = 10, planetId = 99, officerLevel = 3, officerPlanet = 1, dedication = 0)
            seed(listOf(c), listOf(nation(funds = 100000, bill = 0)), listOf(general(npcState = 5), dummy, officer))
            service.preUpdateMonthly(world())
            val goldMismatch = nations[1L]!!.funds

            // No officers at all
            seed(listOf(c), listOf(nation(funds = 100000, bill = 0)), listOf(general(npcState = 5), dummy))
            service.preUpdateMonthly(world())
            val goldNone = nations[1L]!!.funds

            assertThat(goldMismatch).isEqualTo(goldNone)
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // 11. Capital Bonus
    // Legacy: income *= (1 + 1/3/nationLevel)
    // ────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Capital Bonus — func_time_event.php:98-100")
    inner class CapitalBonus {

        @Test
        @DisplayName("Capital city gets (1 + 1/3/nationLevel) multiplier")
        fun `capital bonus applied`() {
            val c = city(population = 20000, commerce = 800, commerceMax = 1000, approval = 100f)

            val dummy = general(id = 99, dedication = 0, npcState = 0) // keep nationGenerals non-empty

            // Capital city (capitalPlanetId = 1)
            seed(listOf(c), listOf(nation(funds = 100000, bill = 0, capitalPlanetId = 1)), listOf(general(npcState = 5), dummy))
            service.preUpdateMonthly(world())
            val goldCapital = nations[1L]!!.funds - 100000

            // Non-capital (capitalPlanetId = 99)
            seed(listOf(c), listOf(nation(funds = 100000, bill = 0, capitalPlanetId = 99)), listOf(general(npcState = 5), dummy))
            service.preUpdateMonthly(world())
            val goldNonCapital = nations[1L]!!.funds - 100000

            // level = 1: bonus = 1 + 1/3/1 = 1.333
            val expectedRatio = 1 + 1.0 / 3 / 1
            assertThat(goldCapital.toDouble() / goldNonCapital)
                .isCloseTo(expectedRatio, within(0.01))
        }

        @Test
        @DisplayName("Capital bonus scales inversely with nation level")
        fun `capital bonus inversely scales with level`() {
            val c = city(population = 20000, commerce = 800, commerceMax = 1000, approval = 100f)

            val dummy = general(id = 99, dedication = 0, npcState = 0) // keep nationGenerals non-empty

            // Level 1: 1 + 1/3/1 = 1.333
            seed(listOf(c), listOf(nation(funds = 100000, bill = 0, level = 1, capitalPlanetId = 1)), listOf(general(npcState = 5), dummy))
            service.preUpdateMonthly(world())
            val goldLevel1 = nations[1L]!!.funds - 100000

            // Level 7: 1 + 1/3/7 = 1.047
            seed(listOf(c), listOf(nation(funds = 100000, bill = 0, level = 7, capitalPlanetId = 1)), listOf(general(npcState = 5), dummy))
            service.preUpdateMonthly(world())
            val goldLevel7 = nations[1L]!!.funds - 100000

            assertThat(goldLevel1).isGreaterThan(goldLevel7)
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // 12. PHP-Verified Golden Values (exact hand-traced from legacy formulas)
    // Legacy: func_time_event.php calcCityGoldIncome/calcCityRiceIncome/calcCityWallRiceIncome
    // ────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("PHP-Verified Golden Values — hand-traced from legacy formulas")
    inner class PhpGoldenValues {

        @ParameterizedTest
        @CsvSource(
            // population, commerce, commerceMax, approval, security, securityMax, officers, isCapital, nationLevel, taxRate, expectedGold
            // Case 1: population=10000, commerce=500, commerceMax=1000, approval=80, security=500, securityMax=1000, off=0, nonCap, lv=1, tax=20
            //   trustRatio=80/200+0.5=0.9, income=10000*500/1000*0.9/30=150, secuMul=1+500/1000/10=1.05
            //   income=150*1.05=157.5, 1.05^0=1, noCap, taxed=157.5*20/20=157 (intval)
            "10000, 500, 1000, 80.0,  500, 1000, 0, false, 1, 20, 157",
            // Case 2: population=50000, commerce=800, commerceMax=1000, approval=100, security=900, securityMax=1000, off=3, cap, lv=3, tax=20
            //   trustRatio=100/200+0.5=1.0, income=50000*800/1000*1.0/30=1333.33, secuMul=1+900/1000/10=1.09
            //   income=1333.33*1.09=1453.33, 1.05^3=1.157625, income*=1.157625=1682.43
            //   capBonus=1+1/3/3=1.1111, income*=1.1111=1869.36, taxed=1869.36*20/20=1869
            "50000, 800, 1000, 100.0, 900, 1000, 3, true,  3, 20, 1869",
            // Case 3: population=5000, commerce=200, commerceMax=1000, approval=50, security=100, securityMax=1000, off=0, nonCap, lv=5, tax=10
            //   trustRatio=50/200+0.5=0.75, income=5000*200/1000*0.75/30=25, secuMul=1+100/1000/10=1.01
            //   income=25*1.01=25.25, 1.05^0=1, noCap, taxed=25.25*10/20=12 (intval)
            "5000,  200, 1000, 50.0,  100, 1000, 0, false, 5, 10, 12",
            // Case 4: population=1000, commerce=100, commerceMax=500, approval=30, security=50, securityMax=200, off=1, nonCap, lv=2, tax=15
            //   trustRatio=30/200+0.5=0.65, income=1000*100/500*0.65/30=4.333, secuMul=1+50/200/10=1.025
            //   income=4.333*1.025=4.4416, 1.05^1=1.05, income*=1.05=4.6637, noCap
            //   taxed=4.6637*15/20=3.4978 -> 3
            "1000,  100, 500,  30.0,  50,  200,  1, false, 2, 15, 3",
        )
        @DisplayName("Gold income PHP-traced golden values")
        fun `gold income exact php trace`(
            population: Int, commerce: Int, commerceMax: Int, approval: Float, security: Int, securityMax: Int,
            officers: Int, isCapital: Boolean, nationLevel: Int, taxRate: Int, expectedGold: Int,
        ) {
            val capitalPlanetId = if (isCapital) 1L else 99L
            val c = city(population = population, commerce = commerce, commerceMax = commerceMax, approval = approval, security = security, securityMax = securityMax)
            val n = nation(funds = 100000, supplies = 100000, rateTmp = taxRate.toShort(), capitalPlanetId = capitalPlanetId,
                level = nationLevel.toShort(), bill = 0)
            val officerGenerals = (1..officers).map { idx ->
                general(id = (10 + idx).toLong(), officerLevel = 3, officerPlanet = 1)
            }
            val allGenerals = listOf(general(npcState = 5)) + officerGenerals +
                listOf(general(id = 99, dedication = 0, npcState = 0))
            seed(listOf(c), listOf(n), allGenerals)
            service.preUpdateMonthly(world())
            val actual = nations[1L]!!.funds - 100000
            assertThat(actual).describedAs("Gold: population=$population commerce=$commerce/$commerceMax approval=$approval").isCloseTo(expectedGold, within(1))
        }

        @ParameterizedTest
        @CsvSource(
            // population, production, productionMax, approval, security, securityMax, officers, isCapital, nationLevel, taxRate, expectedRice
            // Case 1: same formula as gold but uses production instead of commerce
            //   population=10000, production=500, productionMax=1000, approval=80, security=500, securityMax=1000, off=0, nonCap, lv=1, tax=20
            //   -> 157 (same as gold case 1)
            "10000, 500, 1000, 80.0,  500, 1000, 0, false, 1, 20, 157",
            // Case 2: population=30000, production=1000, productionMax=1000, approval=60, security=800, securityMax=1000, off=1, nonCap, lv=5, tax=15
            //   trustRatio=60/200+0.5=0.8, income=30000*1000/1000*0.8/30=800, secuMul=1+800/1000/10=1.08
            //   income=800*1.08=864, 1.05^1=1.05, income*=1.05=907.2, noCap
            //   taxed=907.2*15/20=680.4 -> 680
            "30000, 1000,1000, 60.0,  800, 1000, 1, false, 5, 15, 680",
        )
        @DisplayName("Rice income PHP-traced golden values")
        fun `rice income exact php trace`(
            population: Int, production: Int, productionMax: Int, approval: Float, security: Int, securityMax: Int,
            officers: Int, isCapital: Boolean, nationLevel: Int, taxRate: Int, expectedRice: Int,
        ) {
            val capitalPlanetId = if (isCapital) 1L else 99L
            val c = city(population = population, production = production, productionMax = productionMax, approval = approval, security = security, securityMax = securityMax,
                fortress = 0, fortressMax = 0)
            val n = nation(funds = 100000, supplies = 100000, rateTmp = taxRate.toShort(), capitalPlanetId = capitalPlanetId,
                level = nationLevel.toShort(), bill = 0)
            val officerGenerals = (1..officers).map { idx ->
                general(id = (10 + idx).toLong(), officerLevel = 3, officerPlanet = 1)
            }
            seed(listOf(c), listOf(n), listOf(general(npcState = 5)) + officerGenerals +
                listOf(general(id = 99, dedication = 0, npcState = 0)))
            service.preUpdateMonthly(world())
            val actual = nations[1L]!!.supplies - 100000
            assertThat(actual).describedAs("Rice: population=$population production=$production/$productionMax").isCloseTo(expectedRice, within(1))
        }

        @ParameterizedTest
        @CsvSource(
            // orbitalDefense, fortress, fortressMax, security, securityMax, officers, isCapital, nationLevel, taxRate, expectedWall
            // Case 1: orbitalDefense=500, fortress=500, fortressMax=1000, security=500, securityMax=1000, off=0, nonCap, lv=1, tax=20
            //   income=500*500/1000/3=83.333, secuMul=1+500/1000/10=1.05, income=83.333*1.05=87.5
            //   1.05^0=1, noCap, taxed=87.5*20/20=87
            "500,  500, 1000, 500, 1000, 0, false, 1, 20, 87",
            // Case 2: orbitalDefense=1000, fortress=1000, fortressMax=1000, security=1000, securityMax=1000, off=3, cap, lv=7, tax=15
            //   income=1000*1000/1000/3=333.333, secuMul=1+1000/1000/10=1.1, income=333.333*1.1=366.666
            //   1.05^3=1.157625, income*=1.157625=424.462, capBonus=1+1/3/7=1.04762
            //   income*=1.04762=444.657, taxed=444.657*15/20=333.493 -> 333
            "1000, 1000,1000, 1000,1000, 3, true,  7, 15, 333",
        )
        @DisplayName("Wall income PHP-traced golden values")
        fun `fortress income exact php trace`(
            orbitalDefense: Int, fortress: Int, fortressMax: Int, security: Int, securityMax: Int,
            officers: Int, isCapital: Boolean, nationLevel: Int, taxRate: Int, expectedWall: Int,
        ) {
            val capitalPlanetId = if (isCapital) 1L else 99L
            val c = city(population = 1000, production = 0, productionMax = 0, commerce = 0, commerceMax = 0,
                orbitalDefense = orbitalDefense, fortress = fortress, fortressMax = fortressMax, security = security, securityMax = securityMax)
            val n = nation(funds = 100000, supplies = 100000, rateTmp = taxRate.toShort(), capitalPlanetId = capitalPlanetId,
                level = nationLevel.toShort(), bill = 0)
            val officerGenerals = (1..officers).map { idx ->
                general(id = (10 + idx).toLong(), officerLevel = 3, officerPlanet = 1)
            }
            seed(listOf(c), listOf(n), listOf(general(npcState = 5)) + officerGenerals +
                listOf(general(id = 99, dedication = 0, npcState = 0)))
            service.preUpdateMonthly(world())
            val actual = nations[1L]!!.supplies - 100000
            assertThat(actual).describedAs("Wall: orbitalDefense=$orbitalDefense fortress=$fortress/$fortressMax").isCloseTo(expectedWall, within(1))
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // 13. Nation Type Modifier Income Variants
    // Legacy: nationType->onCalcNationalIncome('gold'/'rice'/'population', value) modifies income
    //   상인: goldMultiplier=1.2
    //   농업국: riceMultiplier=1.2, popGrowthMultiplier=1.05
    //   도적: goldMultiplier=0.9
    // ────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Nation Type Modifier Income — NationTypeModifiers.kt")
    inner class NationTypeModifierIncome {

        @Test
        @DisplayName("상인 nation type gives 1.2x gold income")
        fun `merchant nation gold bonus`() {
            val c = city(population = 20000, commerce = 800, commerceMax = 1000, approval = 100f)
            val dummy = general(id = 99, dedication = 0, npcState = 0)

            // Default (중립)
            seed(listOf(c), listOf(nation(funds = 100000, bill = 0, factionType = "che_중립")),
                listOf(general(npcState = 5), dummy))
            service.preUpdateMonthly(world())
            val goldDefault = nations[1L]!!.funds - 100000

            // 상인 (goldMultiplier=1.2)
            seed(listOf(c), listOf(nation(funds = 100000, bill = 0, factionType = "che_상인")),
                listOf(general(npcState = 5), dummy))
            service.preUpdateMonthly(world())
            val goldMerchant = nations[1L]!!.funds - 100000

            assertThat(goldMerchant.toDouble() / goldDefault)
                .describedAs("상인 gold multiplier should be ~1.2")
                .isCloseTo(1.2, within(0.01))
        }

        @Test
        @DisplayName("농업국 nation type gives 1.2x rice income")
        fun `agricultural nation rice bonus`() {
            val c = city(population = 20000, production = 800, productionMax = 1000, approval = 100f, commerce = 0, commerceMax = 0, fortress = 0, fortressMax = 0)
            val dummy = general(id = 99, dedication = 0, npcState = 0)

            // Default (중립)
            seed(listOf(c), listOf(nation(funds = 100000, supplies = 100000, bill = 0, factionType = "che_중립")),
                listOf(general(npcState = 5), dummy))
            service.preUpdateMonthly(world())
            val riceDefault = nations[1L]!!.supplies - 100000

            // 농업국 (riceMultiplier=1.2)
            seed(listOf(c), listOf(nation(funds = 100000, supplies = 100000, bill = 0, factionType = "che_농업국")),
                listOf(general(npcState = 5), dummy))
            service.preUpdateMonthly(world())
            val riceAgri = nations[1L]!!.supplies - 100000

            assertThat(riceAgri.toDouble() / riceDefault)
                .describedAs("농업국 rice multiplier should be ~1.2")
                .isCloseTo(1.2, within(0.01))
        }

        @Test
        @DisplayName("도적 nation type gives 0.9x gold income")
        fun `bandit nation gold penalty`() {
            val c = city(population = 20000, commerce = 800, commerceMax = 1000, approval = 100f)
            val dummy = general(id = 99, dedication = 0, npcState = 0)

            // Default (중립)
            seed(listOf(c), listOf(nation(funds = 100000, bill = 0, factionType = "che_중립")),
                listOf(general(npcState = 5), dummy))
            service.preUpdateMonthly(world())
            val goldDefault = nations[1L]!!.funds - 100000

            // 도적 (goldMultiplier=0.9)
            seed(listOf(c), listOf(nation(funds = 100000, bill = 0, factionType = "che_도적")),
                listOf(general(npcState = 5), dummy))
            service.preUpdateMonthly(world())
            val goldBandit = nations[1L]!!.funds - 100000

            assertThat(goldBandit.toDouble() / goldDefault)
                .describedAs("도적 gold multiplier should be ~0.9")
                .isCloseTo(0.9, within(0.01))
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

    // ────────────────────────────────────────────────────────────────────────
    // 15. processIncome salary distribution (gold and rice)
    // Legacy ProcessIncome.php:
    //   goldRatio = realOutcome / totalBill (where totalBill = sum(getBill(ded)) at 100% billRate)
    //   Each general receives: round(getBill(ded) * ratio)
    //   Treasury: gold += income, then gold -= realOutcome, clamped >= BASE_GOLD(0)
    //   Partial: if gold < totalBill, realOutcome = funds - BASE_GOLD
    //   Zero: if gold < BASE_GOLD, realOutcome = 0, ratio = 0
    // ────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("processIncome Salary Distribution — ProcessIncome.php")
    inner class ProcessIncomeSalaryDistribution {

        @Test
        @DisplayName("Full salary: nation gold sufficient -> ratio = billRate/100")
        fun `full salary distribution`() {
            // Nation: funds =50000, supplies =50000, bill=100
            // General: ded=1000, getBill=1200
            // City with decent income to ensure treasury covers salary
            val c = city(population = 30000, commerce = 900, commerceMax = 1000, production = 900, productionMax = 1000, approval = 100f,
                security = 500, securityMax = 1000, fortress = 0, fortressMax = 0)
            val n = nation(funds = 50000, supplies = 50000, bill = 100)
            val g = general(funds = 0, supplies = 0, dedication = 1000)  // bill=1200
            seed(listOf(c), listOf(n), listOf(g))

            service.preUpdateMonthly(world())

            // General should receive full salary = getBill(1000) = 1200 at ratio ~1.0
            val updatedGeneral = generals[1L]!!
            assertThat(updatedGeneral.funds)
                .describedAs("General gold after full salary")
                .isCloseTo(1200, within(10))
            assertThat(updatedGeneral.supplies)
                .describedAs("General rice after full salary")
                .isCloseTo(1200, within(10))
        }

        @Test
        @DisplayName("Partial salary: nation gold < total bill -> reduced ratio")
        fun `partial salary with insufficient treasury`() {
            // Nation: funds =200, supplies =200, bill=100
            // No city income (all maxes = 0) -> gold stays at 200
            // General: ded=1000, getBill=1200
            // outcome = 1200 * 100/100 = 1200
            // gold(200) + income(0) = 200 > BASE_GOLD(0), but 200 < 1200
            // realOutcome = 200, ratio = 200/1200 = 0.1667
            // generalGold = round(1200 * 0.1667) = 200
            val c = city(commerceMax = 0, productionMax = 0, fortressMax = 0)
            val n = nation(funds = 200, supplies = 200, bill = 100)
            val g = general(funds = 0, supplies = 0, dedication = 1000)
            seed(listOf(c), listOf(n), listOf(g))

            service.preUpdateMonthly(world())

            val updatedGeneral = generals[1L]!!
            // Partial payment: general gets ~200 gold (all available treasury)
            assertThat(updatedGeneral.funds)
                .describedAs("Partial salary gold")
                .isCloseTo(200, within(5))
            // Nation treasury should be at BASE_GOLD(0)
            assertThat(nations[1L]!!.funds).isEqualTo(0)
        }

        @Test
        @DisplayName("Multiple generals: salary distributed proportionally by dedication")
        fun `salary proportional to dedication among generals`() {
            val c = city(commerceMax = 0, productionMax = 0, fortressMax = 0)
            val n = nation(funds = 5000, supplies = 5000, bill = 100)
            // General 1: ded=1000, bill=1200; General 2: ded=100, bill=600
            val g1 = general(id = 1, funds = 0, supplies = 0, dedication = 1000)
            val g2 = general(id = 2, funds = 0, supplies = 0, dedication = 100)
            seed(listOf(c), listOf(n), listOf(g1, g2))

            service.preUpdateMonthly(world())

            val updated1 = generals[1L]!!
            val updated2 = generals[2L]!!
            // Bill ratio: 1200 vs 600 = 2:1
            // Each gets proportional to their own bill, not proportional to each other
            // Both get bill * ratio where ratio = min(1.0, available/totalBill)
            // totalBill = 1200 + 600 = 1800
            // gold(5000) + 0 income = 5000 >= 1800 -> ratio = 1.0
            // g1 funds = 1200, g2 funds = 600
            assertThat(updated1.funds)
                .describedAs("Higher dedication general gets more")
                .isGreaterThan(updated2.funds)
            assertThat(updated1.funds.toDouble() / updated2.funds)
                .describedAs("Salary ratio matches bill ratio 1200/600=2.0")
                .isCloseTo(2.0, within(0.1))
        }

        @Test
        @DisplayName("Tax rate affects income and thus salary capacity")
        fun `tax rate 10 vs 20 income difference`() {
            val c = city(population = 20000, commerce = 800, commerceMax = 1000, approval = 100f, production = 0, productionMax = 0, fortress = 0, fortressMax = 0)

            // Tax 20 (full)
            seed(listOf(c), listOf(nation(funds = 100000, supplies = 100000, rateTmp = 20, bill = 0)),
                listOf(general(npcState = 5)))
            service.preUpdateMonthly(world())
            val incTax20 = nations[1L]!!.funds - 100000

            // Tax 10 (half)
            seed(listOf(c), listOf(nation(funds = 100000, supplies = 100000, rateTmp = 10, bill = 0)),
                listOf(general(npcState = 5)))
            service.preUpdateMonthly(world())
            val incTax10 = nations[1L]!!.funds - 100000

            // Legacy: income *= taxRate/20, so tax10 should be exactly half of tax20
            assertThat(incTax10).isCloseTo(incTax20 / 2, within(1))
        }
    }
}
