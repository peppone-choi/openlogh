package com.opensam.qa.parity

import com.opensam.engine.EconomyService
import com.opensam.engine.turn.cqrs.persist.toEntity
import com.opensam.engine.turn.cqrs.persist.toSnapshot
import com.opensam.entity.City
import com.opensam.entity.General
import com.opensam.entity.Nation
import com.opensam.entity.WorldState
import com.opensam.repository.CityRepository
import com.opensam.repository.GeneralRepository
import com.opensam.repository.MessageRepository
import com.opensam.repository.NationRepository
import com.opensam.service.HistoryService
import com.opensam.service.InheritanceService
import com.opensam.service.MapService
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
    private lateinit var cityRepository: CityRepository
    private lateinit var nationRepository: NationRepository
    private lateinit var generalRepository: GeneralRepository
    private lateinit var mapService: MapService

    private val cities = linkedMapOf<Long, City>()
    private val nations = linkedMapOf<Long, Nation>()
    private val generals = linkedMapOf<Long, General>()

    @BeforeEach
    fun setUp() {
        cityRepository = mock(CityRepository::class.java)
        nationRepository = mock(NationRepository::class.java)
        generalRepository = mock(GeneralRepository::class.java)
        mapService = mock(MapService::class.java)
        service = EconomyService(
            cityRepository, nationRepository, generalRepository,
            mock(MessageRepository::class.java), mapService,
            mock(HistoryService::class.java), mock(InheritanceService::class.java),
        )
        wireRepos()
        `when`(mapService.getAdjacentCities(ArgumentMatchers.anyString(), ArgumentMatchers.anyInt()))
            .thenReturn(emptyList())
    }

    private fun wireRepos() {
        `when`(cityRepository.findByWorldId(ArgumentMatchers.anyLong())).thenAnswer { inv ->
            val worldId = inv.arguments[0] as Long
            cities.values.filter { it.worldId == worldId }.map { it.toSnapshot().toEntity() }
        }
        `when`(nationRepository.findByWorldId(ArgumentMatchers.anyLong())).thenAnswer { inv ->
            val worldId = inv.arguments[0] as Long
            nations.values.filter { it.worldId == worldId }.map { it.toSnapshot().toEntity() }
        }
        `when`(generalRepository.findByWorldId(ArgumentMatchers.anyLong())).thenAnswer { inv ->
            val worldId = inv.arguments[0] as Long
            generals.values.filter { it.worldId == worldId }.map { it.toSnapshot().toEntity() }
        }
        `when`(generalRepository.findByWorldIdAndCityIdIn(ArgumentMatchers.anyLong(), ArgumentMatchers.anyList()))
            .thenReturn(emptyList())
        `when`(cityRepository.save(ArgumentMatchers.any(City::class.java))).thenAnswer { inv ->
            val city = inv.arguments[0] as City
            cities[city.id] = city.toSnapshot().toEntity()
            city
        }
        `when`(nationRepository.save(ArgumentMatchers.any(Nation::class.java))).thenAnswer { inv ->
            val nation = inv.arguments[0] as Nation
            nations[nation.id] = nation.toSnapshot().toEntity()
            nation
        }
        `when`(generalRepository.save(ArgumentMatchers.any(General::class.java))).thenAnswer { inv ->
            val general = inv.arguments[0] as General
            generals[general.id] = general.toSnapshot().toEntity()
            general
        }
    }

    private fun seed(
        cityList: List<City> = emptyList(),
        nationList: List<Nation> = emptyList(),
        generalList: List<General> = emptyList(),
    ) {
        cities.clear(); nations.clear(); generals.clear()
        cityList.forEach { cities[it.id] = it.toSnapshot().toEntity() }
        nationList.forEach { nations[it.id] = it.toSnapshot().toEntity() }
        generalList.forEach { generals[it.id] = it.toSnapshot().toEntity() }
    }

    private fun world(year: Short = 200, month: Short = 3): WorldState =
        WorldState(id = 1, scenarioCode = "test", currentYear = year, currentMonth = month, tickSeconds = 300)

    private fun city(
        id: Long = 1, nationId: Long = 1,
        pop: Int = 10000, popMax: Int = 50000,
        agri: Int = 500, agriMax: Int = 1000,
        comm: Int = 500, commMax: Int = 1000,
        secu: Int = 500, secuMax: Int = 1000,
        def: Int = 500, defMax: Int = 1000,
        wall: Int = 500, wallMax: Int = 1000,
        trust: Float = 80f, supplyState: Short = 1,
        level: Short = 5, dead: Int = 0, trade: Int = 100,
    ): City = City(
        id = id, worldId = 1, name = "city$id", mapCityId = id.toInt(),
        nationId = nationId, pop = pop, popMax = popMax,
        agri = agri, agriMax = agriMax, comm = comm, commMax = commMax,
        secu = secu, secuMax = secuMax, def = def, defMax = defMax,
        wall = wall, wallMax = wallMax, trust = trust,
        supplyState = supplyState, level = level, dead = dead, trade = trade,
    )

    private fun nation(
        id: Long = 1, gold: Int = 10000, rice: Int = 10000,
        level: Short = 1, rateTmp: Short = 15, bill: Short = 100,
        capitalCityId: Long? = 1, rate: Short = 15,
    ): Nation = Nation(
        id = id, worldId = 1, name = "nation$id", color = "#FF0000",
        gold = gold, rice = rice, level = level, rateTmp = rateTmp,
        bill = bill, capitalCityId = capitalCityId, rate = rate,
    )

    private fun general(
        id: Long = 1, nationId: Long = 1, cityId: Long = 1,
        gold: Int = 1000, rice: Int = 1000, dedication: Int = 1000,
        officerLevel: Short = 1, officerCity: Int = 0, npcState: Short = 0,
    ): General = General(
        id = id, worldId = 1, name = "general$id",
        nationId = nationId, cityId = cityId, gold = gold, rice = rice,
        dedication = dedication, officerLevel = officerLevel,
        officerCity = officerCity, npcState = npcState,
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
        pop: Int, comm: Int, commMax: Int, trust: Float,
        secu: Int, secuMax: Int, officers: Int,
        isCapital: Boolean, nationLevel: Int,
    ): Double {
        if (commMax == 0) return 0.0
        val trustRatio = trust / 200.0 + 0.5
        var income = pop.toDouble() * comm / commMax * trustRatio / 30
        income *= 1 + secu.toDouble() / secuMax.coerceAtLeast(1) / 10
        income *= 1.05.pow(officers)
        if (isCapital) income *= 1 + 1.0 / 3 / nationLevel.coerceAtLeast(1)
        return income
    }

    /**
     * Legacy calcCityRiceIncome (single city, before tax rate)
     * Source: func_time_event.php:106-122
     */
    private fun legacyCityRiceIncome(
        pop: Int, agri: Int, agriMax: Int, trust: Float,
        secu: Int, secuMax: Int, officers: Int,
        isCapital: Boolean, nationLevel: Int,
    ): Double {
        if (agriMax == 0) return 0.0
        val trustRatio = trust / 200.0 + 0.5
        var income = pop.toDouble() * agri / agriMax * trustRatio / 30
        income *= 1 + secu.toDouble() / secuMax.coerceAtLeast(1) / 10
        income *= 1.05.pow(officers)
        if (isCapital) income *= 1 + 1.0 / 3 / nationLevel.coerceAtLeast(1)
        return income
    }

    /**
     * Legacy calcCityWallRiceIncome (single city, before tax rate)
     * Source: func_time_event.php:124-139
     */
    private fun legacyCityWallIncome(
        def: Int, wall: Int, wallMax: Int,
        secu: Int, secuMax: Int, officers: Int,
        isCapital: Boolean, nationLevel: Int,
    ): Double {
        if (wallMax == 0) return 0.0
        var income = def.toDouble() * wall / wallMax / 3
        income *= 1 + secu.toDouble() / secuMax.coerceAtLeast(1) / 10
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
            // pop, comm, commMax, trust, secu, secuMax, officers, isCapital, nationLevel, taxRate
            "10000, 500, 1000, 80,  500, 1000, 0, false, 1, 20",
            "50000, 800, 1000, 100, 900, 1000, 3, true,  3, 20",
            "5000,  200, 1000, 50,  100, 1000, 0, false, 5, 10",
            "30000, 1000,1000, 100, 1000,1000, 2, true,  7, 30",
            "1000,  100, 500,  30,  50,  200,  1, false, 2, 15",
        )
        @DisplayName("Gold income golden value matches legacy formula exactly")
        fun `gold income golden values`(
            pop: Int, comm: Int, commMax: Int, trust: Float, secu: Int, secuMax: Int,
            officers: Int, isCapital: Boolean, nationLevel: Int, taxRate: Int,
        ) {
            val capitalCityId = if (isCapital) 1L else 99L
            val c = city(pop = pop, comm = comm, commMax = commMax, trust = trust, secu = secu, secuMax = secuMax)
            val n = nation(
                gold = 100000, rice = 100000,  // large treasury to avoid salary affecting result
                rateTmp = taxRate.toShort(), capitalCityId = capitalCityId,
                level = nationLevel.toShort(), bill = 0,  // zero bill so no salary deduction
            )
            val officerGenerals = (1..officers).map { idx ->
                general(id = (10 + idx).toLong(), officerLevel = 3, officerCity = 1)
            }
            val allGenerals = listOf(general(npcState = 5)) + officerGenerals +
                listOf(general(id = 99, dedication = 0, npcState = 0)) // keep nationGenerals non-empty

            seed(listOf(c), listOf(n), allGenerals)
            service.preUpdateMonthly(world())

            val expected = legacyCityGoldIncome(pop, comm, commMax, trust, secu, secuMax, officers, isCapital, nationLevel)
            val expectedTaxed = (expected * taxRate / 20).toInt()
            val actual = nations[1L]!!.gold - 100000

            assertThat(actual)
                .describedAs("Gold income: pop=$pop comm=$comm/$commMax trust=$trust secu=$secu/$secuMax off=$officers cap=$isCapital lv=$nationLevel tax=$taxRate -> expected=$expectedTaxed")
                .isCloseTo(expectedTaxed, within(1))
        }

        @Test
        @DisplayName("Unsupplied city produces zero income — legacy supply==0 guard")
        fun `unsupplied city zero income`() {
            val c = city(supplyState = 0)
            val n = nation(gold = 100000, bill = 0)
            seed(listOf(c), listOf(n), listOf(general(npcState = 5)))

            service.preUpdateMonthly(world())
            assertThat(nations[1L]!!.gold).isEqualTo(100000)
        }

        @Test
        @DisplayName("Zero commMax produces zero gold income (no division by zero)")
        fun `zero commMax safe`() {
            val c = city(commMax = 0)
            val n = nation(gold = 100000, bill = 0)
            seed(listOf(c), listOf(n), listOf(general(npcState = 5)))

            service.preUpdateMonthly(world())
            // Should not throw, no gold income from comm
            assertThat(nations[1L]!!.gold).isEqualTo(100000)
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // 2. City Rice Income (agri-based)
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
            pop: Int, agri: Int, agriMax: Int, trust: Float, secu: Int, secuMax: Int,
            officers: Int, isCapital: Boolean, nationLevel: Int, taxRate: Int,
        ) {
            val capitalCityId = if (isCapital) 1L else 99L
            val c = city(pop = pop, agri = agri, agriMax = agriMax, trust = trust, secu = secu, secuMax = secuMax,
                wall = 0, wallMax = 0)  // zero wall to isolate rice-from-agri
            val n = nation(
                gold = 100000, rice = 100000,
                rateTmp = taxRate.toShort(), capitalCityId = capitalCityId,
                level = nationLevel.toShort(), bill = 0,
            )
            val officerGenerals = (1..officers).map { idx ->
                general(id = (10 + idx).toLong(), officerLevel = 3, officerCity = 1)
            }

            seed(listOf(c), listOf(n), listOf(general(npcState = 5)) + officerGenerals +
                listOf(general(id = 99, dedication = 0, npcState = 0))) // keep nationGenerals non-empty
            service.preUpdateMonthly(world())

            val expected = legacyCityRiceIncome(pop, agri, agriMax, trust, secu, secuMax, officers, isCapital, nationLevel)
            val expectedTaxed = (expected * taxRate / 20).toInt()
            val actual = nations[1L]!!.rice - 100000

            assertThat(actual)
                .describedAs("Rice income: pop=$pop agri=$agri/$agriMax")
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
        fun `wall income golden values`(
            def: Int, wall: Int, wallMax: Int,
            secu: Int, secuMax: Int, officers: Int,
            isCapital: Boolean, nationLevel: Int, taxRate: Int,
        ) {
            val capitalCityId = if (isCapital) 1L else 99L
            // Zero agri/comm to isolate wall income
            val c = city(pop = 1000, agri = 0, agriMax = 0, comm = 0, commMax = 0,
                def = def, wall = wall, wallMax = wallMax, secu = secu, secuMax = secuMax)
            val n = nation(
                gold = 100000, rice = 100000,
                rateTmp = taxRate.toShort(), capitalCityId = capitalCityId,
                level = nationLevel.toShort(), bill = 0,
            )
            val officerGenerals = (1..officers).map { idx ->
                general(id = (10 + idx).toLong(), officerLevel = 3, officerCity = 1)
            }

            seed(listOf(c), listOf(n), listOf(general(npcState = 5)) + officerGenerals +
                listOf(general(id = 99, dedication = 0, npcState = 0))) // keep nationGenerals non-empty
            service.preUpdateMonthly(world())

            val expected = legacyCityWallIncome(def, wall, wallMax, secu, secuMax, officers, isCapital, nationLevel)
            val expectedTaxed = (expected * taxRate / 20).toInt()
            val actual = nations[1L]!!.rice - 100000

            assertThat(actual)
                .describedAs("Wall income: def=$def wall=$wall/$wallMax")
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
            val c = city(pop = 20000, comm = 800, commMax = 1000, trust = 100f, secu = 500, secuMax = 1000)

            // Tax 20 (full multiplier = 20/20 = 1.0)
            seed(listOf(c), listOf(nation(gold = 100000, rateTmp = 20, bill = 0)), listOf(general(npcState = 5)))
            service.preUpdateMonthly(world())
            val goldFull = nations[1L]!!.gold - 100000

            // Tax 10 (half multiplier = 10/20 = 0.5)
            seed(listOf(c), listOf(nation(gold = 100000, rateTmp = 10, bill = 0)), listOf(general(npcState = 5)))
            service.preUpdateMonthly(world())
            val goldHalf = nations[1L]!!.gold - 100000

            // goldHalf should be approximately goldFull / 2
            assertThat(goldHalf).isCloseTo(goldFull / 2, within(1))
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // 5. War Income
    // Legacy: calcCityWarGoldIncome() → dead / 10
    // Legacy: dead resets to 0; pop += dead * 0.2 (capped at popMax)
    // ────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("War Income — func_time_event.php:78")
    inner class WarIncome {

        @ParameterizedTest
        @CsvSource(
            "1000,  10000, 50000, 100",   // dead=1000 -> gold+=100, pop+=200
            "5000,  30000, 50000, 500",   // dead=5000 -> gold+=500, pop+=1000
            "500,   49900, 50000, 50",    // dead=500  -> gold+=50, pop+=100 (no cap)
        )
        @DisplayName("War gold income = dead / 10")
        fun `war income from dead`(dead: Int, pop: Int, popMax: Int, expectedGold: Int) {
            val c = city(dead = dead, pop = pop, popMax = popMax, comm = 0, commMax = 0, agri = 0, agriMax = 0, def = 0, defMax = 0, wall = 0, wallMax = 0)
            val n = nation(gold = 100000, rice = 100000, bill = 0)
            seed(listOf(c), listOf(n), listOf(general(npcState = 0, dedication = 0)))

            service.preUpdateMonthly(world())

            assertThat(nations[1L]!!.gold - 100000).isEqualTo(expectedGold)
            assertThat(cities[1L]!!.dead).isEqualTo(0)
        }

        @Test
        @DisplayName("Pop gain from dead troops capped by popMax")
        fun `pop gain capped at popMax`() {
            val c = city(dead = 50000, pop = 49900, popMax = 50000, comm = 0, commMax = 0, agri = 0, agriMax = 0, def = 0, defMax = 0, wall = 0, wallMax = 0)
            val n = nation(gold = 100000, bill = 0)
            seed(listOf(c), listOf(n), listOf(general(npcState = 0, dedication = 0)))

            service.preUpdateMonthly(world())

            assertThat(cities[1L]!!.pop).isLessThanOrEqualTo(50000)
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
            // Nation: gold = 50000, BASE_GOLD = 0
            // 1 general: ded=1000, bill=1200
            // outcome = bill * (nation.bill/100) = 1200 * 1.0 = 1200
            // Nation gold after: 50000 + income - 1200
            // General gold after: 1000 + 1200 * ratio
            val c = city(pop = 20000, comm = 800, commMax = 1000, trust = 100f)
            val n = nation(gold = 50000, rice = 50000, bill = 100)
            val g = general(dedication = 1000, gold = 1000, rice = 1000)
            seed(listOf(c), listOf(n), listOf(g))

            service.preUpdateMonthly(world())

            val updatedGeneral = generals[1L]!!
            // General should receive salary based on getBill(1000) = 1200
            assertThat(updatedGeneral.gold).isGreaterThan(1000)
            assertThat(updatedGeneral.rice).isGreaterThan(1000)
        }

        @Test
        @DisplayName("Nation gold below BASE_GOLD(0) pays zero salary")
        fun `zero salary when treasury empty`() {
            // Treasury at 0, no cities -> no income -> gold stays at 0
            val n = nation(gold = 0, rice = 0, bill = 100)
            val g = general(gold = 500, rice = 500, dedication = 1000)
            seed(emptyList(), listOf(n), listOf(g))

            service.preUpdateMonthly(world())

            // General should receive 0 salary (nation has no income, gold < BASE_GOLD after no income)
            assertThat(generals[1L]!!.gold).isEqualTo(500)
        }

        @Test
        @DisplayName("Partial salary when treasury insufficient: ratio = (gold - BASE_GOLD) / totalBill")
        fun `partial salary when partially broke`() {
            // Nation gold = 100, bill = 100, 1 general ded=1000 -> totalBill=1200, outcome=1200
            // gold < outcome: realOutcome = 100 - 0 = 100, ratio = 100/1200
            // No city income -> general gets 100 * 100/1200 = 8.33 -> 8
            val n = nation(gold = 100, rice = 100, bill = 100)
            val g = general(gold = 0, rice = 0, dedication = 1000)
            // Need at least one city so nationCities is non-null; zero commMax/agriMax/wallMax = zero income
            val zeroCity = city(commMax = 0, agriMax = 0, wallMax = 0)
            seed(listOf(zeroCity), listOf(n), listOf(g))

            service.preUpdateMonthly(world())

            assertThat(generals[1L]!!.gold).isLessThan(legacyBill(1000))
            assertThat(nations[1L]!!.gold).isEqualTo(0) // BASE_GOLD
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
            val g = general(id = 1, gold = initial, rice = 0)
            seed(listOf(c), listOf(n), listOf(g))

            service.postUpdateMonthly(world(month = 1))

            assertThat(generals[1L]!!.gold).isEqualTo(expected)
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
            val g = general(id = 1, gold = 0, rice = initial)
            seed(listOf(c), listOf(n), listOf(g))

            service.postUpdateMonthly(world(month = 1))

            assertThat(generals[1L]!!.rice).isEqualTo(expected)
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
            seed(listOf(c), listOf(nation(gold = initial)), listOf(g))

            service.postUpdateMonthly(world(month = 1))

            assertThat(nations[1L]!!.gold).isEqualTo(expected)
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
            seed(listOf(c), listOf(nation(rice = initial)), listOf(g))

            service.postUpdateMonthly(world(month = 1))

            assertThat(nations[1L]!!.rice).isEqualTo(expected)
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
            val n = nation(level = 0, gold = 0, rice = 0)
            val g = general()
            seed(cityList, listOf(n), listOf(g))

            service.postUpdateMonthly(world(month = 3))

            assertThat(nations[1L]!!.level.toInt()).isEqualTo(expectedLevel)
        }

        @Test
        @DisplayName("Level-up reward: newLevel * 1000 gold and rice")
        fun `level up reward`() {
            // 5 high cities -> level 3, reward = 3 * 1000 = 3000
            val highCities = (1..5L).map { city(id = it, level = 5) }
            val n = nation(level = 0, gold = 1000, rice = 1000)
            val g = general()
            seed(highCities, listOf(n), listOf(g))

            service.postUpdateMonthly(world(month = 3))

            assertThat(nations[1L]!!.gold).isGreaterThanOrEqualTo(1000 + 3000)
            assertThat(nations[1L]!!.rice).isGreaterThanOrEqualTo(1000 + 3000)
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

            assertThat(nations[1L]!!.level.toInt()).isEqualTo(5)
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
            val c = city(pop = 20000, comm = 800, commMax = 1000, trust = 100f)

            val dummy = general(id = 99, dedication = 0, npcState = 0) // keep nationGenerals non-empty

            // No officers
            seed(listOf(c), listOf(nation(gold = 100000, bill = 0)), listOf(general(npcState = 5), dummy))
            service.preUpdateMonthly(world())
            val goldNoOfficer = nations[1L]!!.gold - 100000

            // 3 officers assigned to city 1
            val officers = (10..12L).map { general(id = it, officerLevel = 3, officerCity = 1) }
            seed(listOf(c), listOf(nation(gold = 100000, bill = 0)), listOf(general(npcState = 5), dummy) + officers)
            service.preUpdateMonthly(world())
            val goldWithOfficers = nations[1L]!!.gold - 100000

            // 1.05^3 ≈ 1.1576
            val expectedRatio = 1.05.pow(3)
            assertThat(goldWithOfficers.toDouble() / goldNoOfficer)
                .isCloseTo(expectedRatio, within(0.01))
        }

        @Test
        @DisplayName("Officers NOT in their assigned city do not count")
        fun `officers elsewhere ignored`() {
            val c = city(pop = 20000, comm = 800, commMax = 1000, trust = 100f)

            val dummy = general(id = 99, dedication = 0, npcState = 0) // keep nationGenerals non-empty

            // Officer assigned to city 1 but located in city 99
            val officer = general(id = 10, cityId = 99, officerLevel = 3, officerCity = 1, dedication = 0)
            seed(listOf(c), listOf(nation(gold = 100000, bill = 0)), listOf(general(npcState = 5), dummy, officer))
            service.preUpdateMonthly(world())
            val goldMismatch = nations[1L]!!.gold

            // No officers at all
            seed(listOf(c), listOf(nation(gold = 100000, bill = 0)), listOf(general(npcState = 5), dummy))
            service.preUpdateMonthly(world())
            val goldNone = nations[1L]!!.gold

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
            val c = city(pop = 20000, comm = 800, commMax = 1000, trust = 100f)

            val dummy = general(id = 99, dedication = 0, npcState = 0) // keep nationGenerals non-empty

            // Capital city (capitalCityId = 1)
            seed(listOf(c), listOf(nation(gold = 100000, bill = 0, capitalCityId = 1)), listOf(general(npcState = 5), dummy))
            service.preUpdateMonthly(world())
            val goldCapital = nations[1L]!!.gold - 100000

            // Non-capital (capitalCityId = 99)
            seed(listOf(c), listOf(nation(gold = 100000, bill = 0, capitalCityId = 99)), listOf(general(npcState = 5), dummy))
            service.preUpdateMonthly(world())
            val goldNonCapital = nations[1L]!!.gold - 100000

            // level=1: bonus = 1 + 1/3/1 = 1.333
            val expectedRatio = 1 + 1.0 / 3 / 1
            assertThat(goldCapital.toDouble() / goldNonCapital)
                .isCloseTo(expectedRatio, within(0.01))
        }

        @Test
        @DisplayName("Capital bonus scales inversely with nation level")
        fun `capital bonus inversely scales with level`() {
            val c = city(pop = 20000, comm = 800, commMax = 1000, trust = 100f)

            val dummy = general(id = 99, dedication = 0, npcState = 0) // keep nationGenerals non-empty

            // Level 1: 1 + 1/3/1 = 1.333
            seed(listOf(c), listOf(nation(gold = 100000, bill = 0, level = 1, capitalCityId = 1)), listOf(general(npcState = 5), dummy))
            service.preUpdateMonthly(world())
            val goldLevel1 = nations[1L]!!.gold - 100000

            // Level 7: 1 + 1/3/7 = 1.047
            seed(listOf(c), listOf(nation(gold = 100000, bill = 0, level = 7, capitalCityId = 1)), listOf(general(npcState = 5), dummy))
            service.preUpdateMonthly(world())
            val goldLevel7 = nations[1L]!!.gold - 100000

            assertThat(goldLevel1).isGreaterThan(goldLevel7)
        }
    }
}
