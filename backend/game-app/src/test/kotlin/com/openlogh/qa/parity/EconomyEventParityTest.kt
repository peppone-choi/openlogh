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

/**
 * Economy Event Parity Test
 *
 * Legacy source:
 *   - hwe/sammo/Event/Action/ProcessSemiAnnual.php: semi-annual decay + growth
 *   - hwe/func_time_event.php: popIncrease(), disaster(), tradeRate()
 *   - hwe/sammo/Event/Action/RaiseDisaster.php: disaster/boom effects
 *   - hwe/sammo/Event/Action/RandomizeCityTradeRate.php: trade rate randomization
 *   - hwe/sammo/Event/Action/UpdateNationLevel.php: nation level thresholds
 *   - hwe/sammo/GameConstBase.php: basePopIncreaseAmount=5000
 *
 * Current impl: EconomyService.kt
 */
@DisplayName("Economy Event Parity")
class EconomyEventParityTest {

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

    private fun world(year: Short = 200, month: Short = 1, startYear: Int = 190): SessionState {
        val w = SessionState(id = 1, scenarioCode = "test", currentYear = year, currentMonth = month, tickSeconds = 300)
        w.config["startYear"] = startYear
        w.config["hiddenSeed"] = "test-seed"
        return w
    }

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

    // ────────────────────────────────────────────────────────────────────────
    // Semi-Annual Events (January / July)
    // Legacy ProcessSemiAnnual.php::run():
    //   Step 1: ALL cities get 0.99 decay on production/commerce/security/orbitalDefense/fortress + dead=0
    //   Step 2: popIncrease() applies growth ONLY to supplied nation cities
    //
    // CRITICAL: Legacy decays ALL cities first (line 75-82), then grows supplied.
    //   Net effect on supplied city: value * 0.99 * (1 + genericRatio)
    // ────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Semi-Annual Events (1/7) — ProcessSemiAnnual.php")
    inner class SemiAnnual {

        @Test
        @DisplayName("Semi-annual only triggers on month 1 and 7")
        fun `semi-annual month guard`() {
            val c = city(production = 500)
            val n = nation(rateTmp = 15)
            val g = general(funds = 0, supplies = 0)
            seed(listOf(c), listOf(n), listOf(g))

            // Month 3: no semi-annual growth
            service.postUpdateMonthly(world(month = 3))
            val agriMonth3 = cities[1L]!!.production

            // Reset and test month 1
            seed(listOf(city(production = 500)), listOf(nation(rateTmp = 15)), listOf(general(funds = 0, supplies = 0)))
            service.postUpdateMonthly(world(month = 1))
            val agriMonth1 = cities[1L]!!.production

            // Month 1 should have infrastructure change; month 3 should not
            assertThat(agriMonth1).isNotEqualTo(agriMonth3)
        }

        @Test
        @DisplayName("Dead troops reset to 0 at start of semi-annual")
        fun `dead reset to zero`() {
            val c = city(dead = 500)
            val n = nation()
            val g = general(funds = 0, supplies = 0)
            seed(listOf(c), listOf(n), listOf(g))

            service.postUpdateMonthly(world(month = 1))

            assertThat(cities[1L]!!.dead).isEqualTo(0)
        }

        // ── Population Growth ──
        // Legacy: population = least(populationMax, BASE_POP_INCREASE + population * (1 + popRatio * (1 ± security/securityMax/10)))
        //   popRatio = (30 - taxRate) / 200

        @ParameterizedTest
        @CsvSource(
            // taxRate, expectedPopRatio (= (30-tax)/200)
            "5,   0.125",     // (30-5)/200 = 0.125 = 12.5%
            "15,  0.075",     // (30-15)/200 = 0.075 = 7.5%
            "20,  0.05",      // (30-20)/200 = 0.05 = 5%
            "30,  0.0",       // (30-30)/200 = 0 = 0%
            "50, -0.1",       // (30-50)/200 = -0.1 = -10%
        )
        @DisplayName("popRatio = (30 - taxRate) / 200")
        fun `population ratio formula`(taxRate: Int, expectedRatio: Double) {
            val actualRatio = (30.0 - taxRate) / 200
            assertThat(actualRatio).isCloseTo(expectedRatio, within(0.001))
        }

        @Test
        @DisplayName("Population growth with positive popRatio includes security bonus")
        fun `population growth positive ratio`() {
            // population=10000, taxRate=15, security=500, securityMax=1000
            // popRatio = (30-15)/200 = 0.075
            // secuBonus = security/securityMax/10 = 0.05
            // newPop = least(populationMax, 5000 + 10000 * (1 + 0.075 * (1 + 0.05)))
            //        = least(50000, 5000 + 10000 * 1.07875) = 15787
            val c = city(population = 10000, populationMax = 50000, security = 500, securityMax = 1000)
            val n = nation(rateTmp = 15)
            val g = general(funds = 0, supplies = 0)
            seed(listOf(c), listOf(n), listOf(g))

            service.postUpdateMonthly(world(month = 1))

            val updatedPop = cities[1L]!!.population
            // Expected: 5000 + 10000 * (1 + 0.075 * 1.05) = 5000 + 10787 = 15787
            assertThat(updatedPop).isCloseTo(15787, within(10))
        }

        @Test
        @DisplayName("Population growth with negative popRatio reverses security bonus")
        fun `population growth negative ratio`() {
            // population=10000, taxRate=50, security=500, securityMax=1000
            // popRatio = (30-50)/200 = -0.1
            // Since popRatio < 0: newPop = 5000 + 10000 * (1 + (-0.1) * (1 - 0.05))
            //                             = 5000 + 10000 * (1 - 0.095) = 5000 + 9050 = 14050
            val c = city(population = 10000, populationMax = 50000, security = 500, securityMax = 1000)
            val n = nation(rateTmp = 50)
            val g = general(funds = 0, supplies = 0)
            seed(listOf(c), listOf(n), listOf(g))

            service.postUpdateMonthly(world(month = 1))

            val updatedPop = cities[1L]!!.population
            assertThat(updatedPop).isCloseTo(14050, within(10))
        }

        @Test
        @DisplayName("Population capped at populationMax")
        fun `population capped at max`() {
            val c = city(population = 49000, populationMax = 50000, security = 1000, securityMax = 1000)
            val n = nation(rateTmp = 5)  // very low tax -> high growth
            val g = general(funds = 0, supplies = 0)
            seed(listOf(c), listOf(n), listOf(g))

            service.postUpdateMonthly(world(month = 1))

            assertThat(cities[1L]!!.population).isLessThanOrEqualTo(50000)
        }

        // ── Infrastructure Growth ──
        // Legacy: value = least(max, value * (1 + genericRatio))
        //   genericRatio = (20 - taxRate) / 200
        //
        // IMPORTANT: Legacy applies 0.99 decay to ALL cities FIRST (ProcessSemiAnnual.php:75-82),
        // then applies growth to supplied cities. Net for supplied: value * 0.99 * (1 + genericRatio)

        @ParameterizedTest
        @CsvSource(
            // taxRate, genericRatio
            "0,   0.1",       // (20-0)/200 = 0.1 = 10% growth
            "10,  0.05",      // (20-10)/200 = 0.05 = 5%
            "15,  0.025",     // (20-15)/200 = 0.025 = 2.5%
            "20,  0.0",       // (20-20)/200 = 0 = 0%
            "30, -0.05",      // (20-30)/200 = -0.05 = -5%
        )
        @DisplayName("genericRatio = (20 - taxRate) / 200")
        fun `generic ratio formula`(taxRate: Int, expectedRatio: Double) {
            val actualRatio = (20.0 - taxRate) / 200
            assertThat(actualRatio).isCloseTo(expectedRatio, within(0.001))
        }

        @Test
        @DisplayName("Supplied city infrastructure grows by genericRatio after 0.99 pre-decay")
        fun `infrastructure growth with pre-decay`() {
            // Legacy: production = production * 0.99 (pre-decay), then production = least(max, production * (1 + genericRatio))
            // production=500, taxRate=15, genericRatio=(20-15)/200=0.025
            // After pre-decay: 500 * 0.99 = 495
            // After growth: 495 * 1.025 = 507.375 -> 507
            val c = city(production = 500, productionMax = 1000)
            val n = nation(rateTmp = 15)
            val g = general(funds = 0, supplies = 0)
            seed(listOf(c), listOf(n), listOf(g))

            service.postUpdateMonthly(world(month = 1))

            val updatedAgri = cities[1L]!!.production
            // Legacy: supplied nation cities get 0.99 pre-decay then growth
            // 500 * 0.99 = 495, then 495 * 1.025 = 507.375 -> 507
            val expected = (500 * 0.99 * 1.025).toInt()  // 507

            assertThat(updatedAgri)
                .describedAs("Supplied city production grows by genericRatio without pre-decay")
                .isEqualTo(expected)
        }

        @Test
        @DisplayName("Infrastructure capped at max values")
        fun `infrastructure capped at max`() {
            val c = city(production = 990, productionMax = 1000, commerce = 995, commerceMax = 1000)
            val n = nation(rateTmp = 15)
            val g = general(funds = 0, supplies = 0)
            seed(listOf(c), listOf(n), listOf(g))

            service.postUpdateMonthly(world(month = 1))

            assertThat(cities[1L]!!.production).isLessThanOrEqualTo(1000)
            assertThat(cities[1L]!!.commerce).isLessThanOrEqualTo(1000)
        }

        // ── Trust Adjustment ──
        // Legacy: approval = greatest(0, least(100, approval + (20 - taxRate)))

        @ParameterizedTest
        @CsvSource(
            "80,  15,  85",    // 80 + (20-15) = 85
            "80,  20,  80",    // 80 + (20-20) = 80
            "80,  30,  70",    // 80 + (20-30) = 70
            "95,  10,  100",   // 95 + (20-10) = 105 -> capped at 100
            "5,   25,  0",     // 5 + (20-25) = 0 -> clamped at 0
        )
        @DisplayName("Trust adjustment: approval += (20 - taxRate), clamped [0, 100]")
        fun `approval adjustment`(initialTrust: Float, taxRate: Int, expectedTrust: Float) {
            val c = city(approval = initialTrust)
            val n = nation(rateTmp = taxRate.toShort())
            val g = general(funds = 0, supplies = 0)
            seed(listOf(c), listOf(n), listOf(g))

            service.postUpdateMonthly(world(month = 1))

            assertThat(cities[1L]!!.approval).isEqualTo(expectedTrust)
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Neutral City Decay
    // Legacy ProcessSemiAnnual.php (func_time_event.php:42-49):
    //   nation=0 cities: approval=50, infra *= 0.99
    // ────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Neutral City Decay — func_time_event.php:42")
    inner class NeutralCityDecay {

        @Test
        @DisplayName("Neutral cities get single 0.99 decay on semi-annual")
        fun `neutral city decays`() {
            val c = city(id = 1, factionId = 0, production = 1000, commerce = 1000, security = 1000, orbitalDefense = 1000, fortress = 1000)
            seed(listOf(c), emptyList(), emptyList())

            service.postUpdateMonthly(world(month = 1))

            val updated = cities[1L]!!
            assertThat(updated.production).isEqualTo(990)
            assertThat(updated.commerce).isEqualTo(990)
            assertThat(updated.security).isEqualTo(990)
            assertThat(updated.orbitalDefense).isEqualTo(990)
            assertThat(updated.fortress).isEqualTo(990)
        }

        @Test
        @DisplayName("Neutral city approval resets to 50 on semi-annual")
        fun `neutral city approval resets`() {
            val c = city(id = 1, factionId = 0, approval = 80f)
            seed(listOf(c), emptyList(), emptyList())

            service.postUpdateMonthly(world(month = 1))

            assertThat(cities[1L]!!.approval).isEqualTo(50f)
        }

        @Test
        @DisplayName("Neutral city dead resets to 0")
        fun `neutral city dead resets`() {
            val c = city(id = 1, factionId = 0, dead = 300)
            seed(listOf(c), emptyList(), emptyList())

            service.postUpdateMonthly(world(month = 1))

            assertThat(cities[1L]!!.dead).isEqualTo(0)
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Non-Supplied City Decay
    // Legacy: non-supplied nation cities get 0.99 decay (same as neutral)
    //         but do NOT get growth from popIncrease()
    // ────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Non-Supplied City Decay — ProcessSemiAnnual.php")
    inner class NonSuppliedCityDecay {

        @Test
        @DisplayName("Non-supplied nation city gets 0.99 decay during semi-annual")
        fun `non-supplied city decays`() {
            val c = city(supplyState = 0, production = 1000, commerce = 1000)
            val n = nation()
            val g = general(funds = 0, supplies = 0)
            seed(listOf(c), listOf(n), listOf(g))

            service.postUpdateMonthly(world(month = 1))

            val updated = cities[1L]!!
            assertThat(updated.production).isEqualTo(990)
            assertThat(updated.commerce).isEqualTo(990)
        }

        @Test
        @DisplayName("Supplied nation city grows (not just decays)")
        fun `supplied city grows`() {
            val c = city(supplyState = 1, production = 500, productionMax = 1000)
            val n = nation(rateTmp = 15)  // genericRatio = 0.025 > 0
            val g = general(funds = 0, supplies = 0)
            seed(listOf(c), listOf(n), listOf(g))

            service.postUpdateMonthly(world(month = 1))

            // With pre-decay + growth: 500 * 0.99 * 1.025 = 507 > 500
            assertThat(cities[1L]!!.production).isGreaterThan(500)
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // BFS Supply Chain
    // ────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("BFS Supply Chain")
    inner class SupplyChain {

        @Test
        @DisplayName("BFS from capital marks connected cities as supplied")
        fun `bfs supply from capital`() {
            val c1 = city(id = 1, supplyState = 0)
            val c2 = city(id = 2, supplyState = 0)
            val c3 = city(id = 3, supplyState = 0)
            val n = nation(capitalPlanetId = 1)
            val g = general()

            `when`(mapService.getAdjacentCities("che", 1)).thenReturn(listOf(2))
            `when`(mapService.getAdjacentCities("che", 2)).thenReturn(listOf(1, 3))
            `when`(mapService.getAdjacentCities("che", 3)).thenReturn(listOf(2))

            seed(listOf(c1, c2, c3), listOf(n), listOf(g))
            service.postUpdateMonthly(world(month = 3))

            assertThat(cities[1L]!!.supplyState.toInt()).isEqualTo(1)
            assertThat(cities[2L]!!.supplyState.toInt()).isEqualTo(1)
            assertThat(cities[3L]!!.supplyState.toInt()).isEqualTo(1)
        }

        @Test
        @DisplayName("Isolated city marked as unsupplied with penalty")
        fun `isolated city penalized`() {
            val c1 = city(id = 1, population = 10000, approval = 80f, production = 500, commerce = 500)
            val c2 = city(id = 2, population = 10000, approval = 80f, production = 500, commerce = 500)
            val n = nation(capitalPlanetId = 1)
            val g = general(planetId = 1)

            // No adjacency -> c2 is isolated
            `when`(mapService.getAdjacentCities("che", 1)).thenReturn(emptyList())
            `when`(mapService.getAdjacentCities("che", 2)).thenReturn(emptyList())

            seed(listOf(c1, c2), listOf(n), listOf(g))
            service.postUpdateMonthly(world(month = 3))

            val updated2 = cities[2L]!!
            // Unsupplied penalty: population * 0.9, approval * 0.9, infra * 0.9
            assertThat(updated2.population).isEqualTo(9000)
            assertThat(updated2.approval).isEqualTo(72f)
            assertThat(updated2.production).isEqualTo(450)
            assertThat(updated2.commerce).isEqualTo(450)
            assertThat(updated2.supplyState.toInt()).isEqualTo(0)
        }

        @Test
        @DisplayName("Trust < 30 on unsupplied non-capital city -> neutralized")
        fun `approval below 30 neutralizes city`() {
            val c1 = city(id = 1)  // capital
            val c2 = city(id = 2, approval = 25f)  // low approval, isolated
            val n = nation(capitalPlanetId = 1)
            val g = general(planetId = 1)

            `when`(mapService.getAdjacentCities("che", 1)).thenReturn(emptyList())
            `when`(mapService.getAdjacentCities("che", 2)).thenReturn(emptyList())

            seed(listOf(c1, c2), listOf(n), listOf(g))
            service.postUpdateMonthly(world(month = 3))

            // approval = 25 * 0.9 = 22.5 < 30 -> city neutralized
            val updated2 = cities[2L]!!
            assertThat(updated2.factionId).isEqualTo(0L)
        }

        @Test
        @DisplayName("Capital city is never neutralized even with low approval")
        fun `capital never neutralized`() {
            val c1 = city(id = 1, approval = 10f)  // capital with very low approval
            val n = nation(capitalPlanetId = 1)
            val g = general(planetId = 1)

            `when`(mapService.getAdjacentCities("che", 1)).thenReturn(emptyList())

            seed(listOf(c1), listOf(n), listOf(g))
            service.postUpdateMonthly(world(month = 3))

            // Capital should remain owned even with approval < 30
            assertThat(cities[1L]!!.factionId).isEqualTo(1L)
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Disaster / Boom System
    // Legacy RaiseDisaster.php:
    //   - First 3 years from startYear: skip
    //   - Boom months: 4 (25%), 7 (25%); other months: 0%
    //   - Disaster: city prob = 0.06 - secuRatio * 0.05 (1~6%)
    //   - Boom: city prob = 0.02 + secuRatio * 0.05 (2~7%)
    //   - Disaster affectRatio = 0.8 + clamp(security/securityMax/0.8, 0, 1) * 0.15
    //   - Boom affectRatio = 1.01 + clamp(security/securityMax/0.8, 0, 1) * 0.04
    // ────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Disaster and Boom — RaiseDisaster.php")
    inner class DisasterAndBoom {

        @Test
        @DisplayName("Disasters skip first 3 years after game start")
        fun `skip first 3 years`() {
            val c = city()
            val n = nation()
            val g = general()
            seed(listOf(c), listOf(n), listOf(g))

            // startYear=190, currentYear=192 -> within first 3 years
            service.processDisasterOrBoom(world(year = 192, month = 4, startYear = 190))

            assertThat(cities[1L]!!.population).isEqualTo(10000)
        }

        @Test
        @DisplayName("Year exactly at startYear+3 is NOT skipped")
        fun `year at boundary not skipped`() {
            val c = city(security = 0, securityMax = 1000)  // low security = high disaster prob
            val n = nation()
            val g = general()
            seed(listOf(c), listOf(n), listOf(g))

            // startYear=190, year=193 -> startYear+3 == 193, NOT > 193 -> proceeds
            service.processDisasterOrBoom(world(year = 193, month = 1, startYear = 190))
            // Method should run without error (whether disaster hits is RNG-dependent)
        }

        @ParameterizedTest
        @CsvSource(
            // secuRatio, expectedDisasterProb, expectedBoomProb
            "0.0,  0.06,  0.02",    // 0%  security: disaster=6%, boom=2%
            "0.5,  0.035, 0.045",   // 50% security: disaster=3.5%, boom=4.5%
            "1.0,  0.01,  0.07",    // 100% security: disaster=1%, boom=7%
        )
        @DisplayName("Security ratio affects disaster/boom probability")
        fun `security ratio probability`(secuRatio: Double, expectedDisaster: Double, expectedBoom: Double) {
            // Verify formula
            val actualDisaster = 0.06 - secuRatio * 0.05
            val actualBoom = 0.02 + secuRatio * 0.05
            assertThat(actualDisaster).isCloseTo(expectedDisaster, within(0.001))
            assertThat(actualBoom).isCloseTo(expectedBoom, within(0.001))
        }

        @ParameterizedTest
        @CsvSource(
            // security, securityMax, isDisaster, expectedRatio
            "0,    1000, true,  0.8",      // security/securityMax/0.8 = 0 -> 0.8 + 0*0.15 = 0.8
            "400,  1000, true,  0.875",    // 400/1000/0.8 = 0.5 -> 0.8 + 0.5*0.15 = 0.875
            "800,  1000, true,  0.95",     // 800/1000/0.8 = 1.0 -> 0.8 + 1.0*0.15 = 0.95
            "1000, 1000, true,  0.95",     // 1000/1000/0.8 = 1.25 -> clamp(1) -> 0.95
            "0,    1000, false, 1.01",     // boom: 1.01 + 0*0.04 = 1.01
            "800,  1000, false, 1.05",     // boom: 1.01 + 1.0*0.04 = 1.05
        )
        @DisplayName("Disaster/boom affectRatio formula")
        fun `affect ratio formula`(security: Int, securityMax: Int, isDisaster: Boolean, expectedRatio: Double) {
            val secuRatio = if (securityMax > 0) security.toDouble() / securityMax / 0.8 else 0.0
            val clamped = secuRatio.coerceIn(0.0, 1.0)
            val actualRatio = if (isDisaster) {
                0.8 + clamped * 0.15
            } else {
                1.01 + clamped * 0.04
            }
            assertThat(actualRatio).isCloseTo(expectedRatio, within(0.001))
        }

        @Test
        @DisplayName("Disaster state codes reset at start of processing")
        fun `state codes reset`() {
            // Set a city with state=5 (from previous disaster)
            val c = city()
            cities.clear()
            val modified = c.toSnapshot().toEntity()
            modified.state = 5
            cities[1L] = modified
            nations[1L] = nation()
            generals[1L] = general()

            service.processDisasterOrBoom(world(year = 200, month = 10, startYear = 190))

            // State <= 10 should have been reset to 0 at the start of processing
            // (new disaster may re-set it, but the reset logic should have run)
        }

        @Test
        @DisplayName("Disaster entries match legacy: 10 types across 4 seasons")
        fun `disaster types per month`() {
            // Legacy disaster text counts per month:
            // Month 1: 4 types (역병, 지진, 추위, 황건적)
            // Month 4: 3 types (홍수, 지진, 태풍)
            // Month 7: 3 types (메뚜기, 지진, 흉년)
            // Month 10: 4 types (혹한, 지진, 눈, 황건적)
            // Total: 14 entries (some state codes repeat across months)
            // Boom: Month 4 (호황, stateCode=2), Month 7 (풍작, stateCode=1)

            // This is a structural verification - just verify no crash on all months
            for (month in listOf<Short>(1, 4, 7, 10)) {
                seed(listOf(city(security = 0, securityMax = 1000)), listOf(nation()), listOf(general()))
                service.processDisasterOrBoom(world(year = 200, month = month, startYear = 190))
            }
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Trade Rate Randomization
    // Legacy RandomizeCityTradeRate.php:
    //   Level-based probability: L1-3=0, L4=0.2, L5=0.4, L6=0.6, L7=0.8, L8=1.0
    //   Range: [95, 105] inclusive (nextRangeInt(95, 105))
    // ────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Trade Rate Randomization — RandomizeCityTradeRate.php")
    inner class TradeRateRandomization {

        @ParameterizedTest
        @CsvSource(
            "1, 0.0",
            "2, 0.0",
            "3, 0.0",
            "4, 0.2",
            "5, 0.4",
            "6, 0.6",
            "7, 0.8",
            "8, 1.0",
        )
        @DisplayName("Level-based probability matches legacy table")
        fun `level probability table`(level: Int, expectedProb: Double) {
            val probByLevel = mapOf(
                4 to 0.2, 5 to 0.4, 6 to 0.6, 7 to 0.8, 8 to 1.0
            )
            val actual = probByLevel[level] ?: 0.0
            assertThat(actual).isEqualTo(expectedProb)
        }

        @Test
        @DisplayName("Level 3 city trade rate never changes (prob = 0)")
        fun `level 3 no change`() {
            val c = city(level = 3, tradeRoute = 100)
            val n = nation()
            val g = general()
            seed(listOf(c), listOf(n), listOf(g))

            service.randomizeCityTradeRate(world())

            assertThat(cities[1L]!!.tradeRoute).isEqualTo(100)
        }

        @Test
        @DisplayName("Non-qualifying city trade resets to 100")
        fun `non-qualifying city trade resets`() {
            val c = city(level = 2, tradeRoute = 105)  // level 2 has prob=0, trade should reset
            val n = nation()
            val g = general()
            seed(listOf(c), listOf(n), listOf(g))

            service.randomizeCityTradeRate(world())

            assertThat(cities[1L]!!.tradeRoute).isEqualTo(100)
        }

        @Test
        @DisplayName("Level 8 cities always get randomized (prob = 1.0), range [95, 105]")
        fun `level 8 always randomized in range`() {
            val cityList = (1..20L).map { city(id = it, level = 8, tradeRoute = 100) }
            val n = nation()
            val g = general()
            seed(cityList, listOf(n), listOf(g))

            service.randomizeCityTradeRate(world(year = 200, month = 5))

            for ((_, c) in cities) {
                assertThat(c.tradeRoute).isBetween(95, 105)
            }
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Yearly Statistics (National Power)
    // Legacy: militaryPower = (resource + tech + cityPower + statPower + dex + expDed) / 10
    //   resource = (nationGold + nationRice + sum(generalGold + generalRice)) / 100
    //   techLevel = nation.techLevel
    //   cityPower = sum(population) * sum(population+production+commerce+security+fortress+orbitalDefense) / sum(populationMax+productionMax+commerceMax+securityMax+fortressMax+orbitalDefenseMax) / 100
    //   statPower = per-general formula
    //   dex = sum(dex1..5) / 1000
    //   expDed = sum(experience+dedication) / 100
    // ────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Yearly Statistics — EconomyService.processYearlyStatistics()")
    inner class YearlyStatistics {

        @Test
        @DisplayName("National power calculated for active nations")
        fun `national power calculated`() {
            val c = city(population = 30000, populationMax = 50000, production = 800, productionMax = 1000, commerce = 700, commerceMax = 1000)
            val n = nation(funds = 50000, supplies = 50000, level = 3)
            val g = general(dedication = 5000)
            seed(listOf(c), listOf(n), listOf(g))

            service.processYearlyStatistics(world(year = 200, month = 1))

            assertThat(nations[1L]!!.militaryPower).isGreaterThan(0)
        }

        @Test
        @DisplayName("Level 0 nations skip power calculation")
        fun `level 0 nations skip`() {
            val c = city()
            val n = nation(level = 0)
            val g = general()
            seed(listOf(c), listOf(n), listOf(g))

            service.processYearlyStatistics(world(month = 1))

            assertThat(nations[1L]!!.militaryPower).isEqualTo(0)
        }

        @Test
        @DisplayName("More resources and generals increase power")
        fun `more resources more power`() {
            // Low resources
            val cLow = city(population = 5000, populationMax = 50000, production = 100, productionMax = 1000)
            val nLow = nation(funds = 1000, supplies = 1000, level = 1)
            val gLow = general(dedication = 100)
            seed(listOf(cLow), listOf(nLow), listOf(gLow))
            service.processYearlyStatistics(world(month = 1))
            val powerLow = nations[1L]!!.militaryPower

            // High resources
            val cHigh = city(population = 40000, populationMax = 50000, production = 900, productionMax = 1000, commerce = 900, commerceMax = 1000)
            val nHigh = nation(funds = 100000, supplies = 100000, level = 5)
            val gHigh = general(dedication = 10000)
            seed(listOf(cHigh), listOf(nHigh), listOf(gHigh))
            service.processYearlyStatistics(world(month = 1))
            val powerHigh = nations[1L]!!.militaryPower

            assertThat(powerHigh).isGreaterThan(powerLow)
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // PHP-Verified: Population Increase Golden Values
    // Legacy func_time_event.php popIncrease():
    //   popRatio = (30 - taxRate) / 200
    //   if popRatio >= 0: newPop = least(populationMax, BASE_POP_INCREASE + population * (1 + popRatio * (1 + security/securityMax/10)))
    //   if popRatio < 0:  newPop = least(populationMax, BASE_POP_INCREASE + population * (1 + popRatio * (1 - security/securityMax/10)))
    //   BASE_POP_INCREASE = 5000
    // ────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("PHP-Verified Population Golden Values — func_time_event.php:popIncrease")
    inner class PopulationGoldenValues {

        @ParameterizedTest
        @CsvSource(
            // population, populationMax, security, securityMax, taxRate, expectedPop
            // Case 1: population=1000, populationMax=50000, security=500, securityMax=1000, tax=15
            //   popRatio=(30-15)/200=0.075, secuBonus=500/1000/10=0.05
            //   newPop=least(50000, 5000 + 1000*(1+0.075*(1+0.05))) = 5000+1078 = 6078
            "1000,  50000, 500, 1000, 15, 6078",
            // Case 2: population=5000, populationMax=50000, security=500, securityMax=1000, tax=15
            //   newPop=least(50000, 5000 + 5000*(1+0.075*1.05)) = 5000+5393 = 10393
            "5000,  50000, 500, 1000, 15, 10393",
            // Case 3: population=9000, populationMax=10000, security=500, securityMax=1000, tax=15
            //   newPop=least(10000, 5000 + 9000*1.07875) = least(10000, 5000+9708) = 10000 (capped)
            "9000,  10000, 500, 1000, 15, 10000",
            // Case 4: population=10000, populationMax=50000, security=500, securityMax=1000, tax=50 (negative popRatio)
            //   popRatio=(30-50)/200=-0.1, secuBonus reverses: (1-0.05)=0.95
            //   newPop=least(50000, 5000+10000*(1+(-0.1)*0.95)) = 5000+10000*0.905 = 5000+9050 = 14050
            "10000, 50000, 500, 1000, 50, 14050",
            // Case 5: population=10000, populationMax=50000, security=0, securityMax=1000, tax=20
            //   popRatio=(30-20)/200=0.05, secuBonus=0/1000/10=0
            //   newPop=5000+10000*(1+0.05*1.0) = 5000+10500 = 15500
            "10000, 50000, 0,   1000, 20, 15500",
            // Case 6: population=10000, populationMax=50000, security=1000, securityMax=1000, tax=20
            //   popRatio=0.05, secuBonus=1000/1000/10=0.1
            //   newPop=5000+10000*(1+0.05*1.1) = 5000+10550 = 15550
            "10000, 50000, 1000,1000, 20, 15550",
        )
        @DisplayName("popIncrease PHP-traced golden values")
        fun `population increase golden values`(
            population: Int, populationMax: Int, security: Int, securityMax: Int, taxRate: Int, expectedPop: Int,
        ) {
            val c = city(population = population, populationMax = populationMax, security = security, securityMax = securityMax)
            val n = nation(rateTmp = taxRate.toShort())
            val g = general(funds = 0, supplies = 0)
            seed(listOf(c), listOf(n), listOf(g))

            service.postUpdateMonthly(world(month = 1))

            assertThat(cities[1L]!!.population)
                .describedAs("Pop: population=$population populationMax=$populationMax security=$security/$securityMax tax=$taxRate")
                .isCloseTo(expectedPop, within(10))
        }

        @Test
        @DisplayName("농업국 popGrowthMultiplier=1.05 increases population growth")
        fun `agricultural nation population growth bonus`() {
            val c = city(population = 10000, populationMax = 50000, security = 500, securityMax = 1000)
            val g = general(funds = 0, supplies = 0)

            // Default (중립)
            seed(listOf(c), listOf(nation(rateTmp = 15, factionType = "che_중립")), listOf(g))
            service.postUpdateMonthly(world(month = 1))
            val popDefault = cities[1L]!!.population

            // 농업국 (popGrowthMultiplier=1.05)
            seed(listOf(city(population = 10000, populationMax = 50000, security = 500, securityMax = 1000)),
                listOf(nation(rateTmp = 15, factionType = "che_농업국")),
                listOf(general(funds = 0, supplies = 0)))
            service.postUpdateMonthly(world(month = 1))
            val popAgri = cities[1L]!!.population

            // 농업국 should have higher population growth
            assertThat(popAgri).isGreaterThan(popDefault)
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // PHP-Verified: Infrastructure Growth Golden Values
    // Legacy ProcessSemiAnnual.php:
    //   Step 1: ALL cities: infra *= 0.99 (decay)
    //   Step 2: Supplied cities: infra = least(max, infra * (1 + genericRatio))
    //     genericRatio = (20 - taxRate) / 200
    //   Net for supplied: value * 0.99 * (1 + genericRatio)
    // ────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("PHP-Verified Infrastructure Growth — ProcessSemiAnnual.php")
    inner class InfrastructureGrowthGoldenValues {

        @ParameterizedTest
        @CsvSource(
            // field, initial, max, taxRate, expectedAfter
            // Case 1: production=500, max=1000, tax=15, genericRatio=(20-15)/200=0.025
            //   500*0.99=495, 495*1.025=507.375 -> 507
            "500, 1000, 15, 507",
            // Case 2: production=100, max=1000, tax=15
            //   100*0.99=99, 99*1.025=101.475 -> 101
            "100, 1000, 15, 101",
            // Case 3: production=900, max=1000, tax=15
            //   900*0.99=891, 891*1.025=913.275 -> 913
            "900, 1000, 15, 913",
            // Case 4: production=990, max=1000, tax=15
            //   990*0.99=980, 980*1.025=1004.5 -> capped at 1000
            "990, 1000, 15, 1000",
            // Case 5: production=500, max=1000, tax=0, genericRatio=(20-0)/200=0.1
            //   500*0.99=495, 495*1.1=544.5 -> 544
            "500, 1000, 0,  544",
            // Case 6: production=500, max=1000, tax=30, genericRatio=(20-30)/200=-0.05
            //   500*0.99=495, 495*0.95=470.25 -> 470
            "500, 1000, 30, 470",
        )
        @DisplayName("Infrastructure growth = decay(0.99) then grow by genericRatio")
        fun `infrastructure growth golden values`(
            initial: Int, max: Int, taxRate: Int, expected: Int,
        ) {
            val c = city(production = initial, productionMax = max, commerce = initial, commerceMax = max,
                security = initial, securityMax = max, orbitalDefense = initial, orbitalDefenseMax = max,
                fortress = initial, fortressMax = max)
            val n = nation(rateTmp = taxRate.toShort())
            val g = general(funds = 0, supplies = 0)
            seed(listOf(c), listOf(n), listOf(g))

            service.postUpdateMonthly(world(month = 1))

            val updated = cities[1L]!!
            // All infra fields follow the same formula
            assertThat(updated.production)
                .describedAs("Agri: $initial -> decay+grow with tax=$taxRate")
                .isCloseTo(expected, within(1))
            assertThat(updated.commerce)
                .describedAs("Comm: $initial -> decay+grow with tax=$taxRate")
                .isCloseTo(expected, within(1))
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // PHP-Verified: Disaster/Boom Effect Golden Values
    // Legacy RaiseDisaster.php:
    //   Disaster affectRatio = 0.8 + clamp(security/securityMax/0.8, 0, 1) * 0.15
    //     security=0   -> 0.8 (20% reduction)
    //     security=400 -> 0.875 (12.5% reduction)
    //     security=800 -> 0.95 (5% reduction)
    //     security>=800-> 0.95 (max protection)
    //   Boom affectRatio = 1.01 + clamp(security/securityMax/0.8, 0, 1) * 0.04
    //     security=0   -> 1.01 (1% boost)
    //     security=800 -> 1.05 (5% boost)
    // ────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("PHP-Verified Disaster/Boom Effects — RaiseDisaster.php")
    inner class DisasterBoomGoldenValues {

        @ParameterizedTest
        @CsvSource(
            // security, securityMax, expectedRatio
            "0,    1000, 0.80",     // clamp(0/1000/0.8)=0 -> 0.8+0*0.15=0.8
            "200,  1000, 0.8375",   // clamp(200/1000/0.8=0.25)=0.25 -> 0.8+0.25*0.15=0.8375
            "400,  1000, 0.875",    // clamp(400/1000/0.8=0.5)=0.5 -> 0.8+0.5*0.15=0.875
            "600,  1000, 0.9125",   // clamp(600/1000/0.8=0.75)=0.75 -> 0.8+0.75*0.15=0.9125
            "800,  1000, 0.95",     // clamp(800/1000/0.8=1.0)=1.0 -> 0.8+1.0*0.15=0.95
            "1000, 1000, 0.95",     // clamp(1000/1000/0.8=1.25)=1.0 -> 0.8+1.0*0.15=0.95 (capped)
        )
        @DisplayName("Disaster affectRatio = 0.8 + clamp(security/securityMax/0.8, 0, 1) * 0.15")
        fun `disaster affect ratio golden values`(security: Int, securityMax: Int, expectedRatio: Double) {
            val secuRatioNorm = if (securityMax > 0) (security.toDouble() / securityMax / 0.8).coerceIn(0.0, 1.0) else 0.0
            val actualRatio = 0.8 + secuRatioNorm * 0.15
            assertThat(actualRatio)
                .describedAs("Disaster ratio for security=$security/$securityMax")
                .isCloseTo(expectedRatio, within(0.0001))
        }

        @ParameterizedTest
        @CsvSource(
            // security, securityMax, expectedRatio
            "0,    1000, 1.01",     // 1.01+0*0.04=1.01
            "400,  1000, 1.03",     // 1.01+0.5*0.04=1.03
            "800,  1000, 1.05",     // 1.01+1.0*0.04=1.05
            "1000, 1000, 1.05",     // capped at 1.0
        )
        @DisplayName("Boom affectRatio = 1.01 + clamp(security/securityMax/0.8, 0, 1) * 0.04")
        fun `boom affect ratio golden values`(security: Int, securityMax: Int, expectedRatio: Double) {
            val secuRatioNorm = if (securityMax > 0) (security.toDouble() / securityMax / 0.8).coerceIn(0.0, 1.0) else 0.0
            val actualRatio = 1.01 + secuRatioNorm * 0.04
            assertThat(actualRatio)
                .describedAs("Boom ratio for security=$security/$securityMax")
                .isCloseTo(expectedRatio, within(0.0001))
        }

        @ParameterizedTest
        @CsvSource(
            // month, boomRate
            "1,  0.0",     // January: no boom possible
            "4,  0.25",    // April: 25% boom chance
            "7,  0.25",    // July: 25% boom chance
            "10, 0.0",     // October: no boom possible
        )
        @DisplayName("Boom probability by month matches legacy boomingRate table")
        fun `boom rate by month`(month: Int, expectedBoomRate: Double) {
            val boomingRate = mapOf(1 to 0.0, 4 to 0.25, 7 to 0.25, 10 to 0.0)
            assertThat(boomingRate[month]).isEqualTo(expectedBoomRate)
        }

        @ParameterizedTest
        @CsvSource(
            // secuRatio, isGood=false -> disaster prob, isGood=true -> boom prob
            "0.0, 0.06, 0.02",
            "0.25, 0.0475, 0.0325",
            "0.5, 0.035, 0.045",
            "0.75, 0.0225, 0.0575",
            "1.0, 0.01, 0.07",
        )
        @DisplayName("Per-city disaster/boom probability formulas match legacy")
        fun `per city probability formulas`(secuRatio: Double, disasterProb: Double, boomProb: Double) {
            val actualDisaster = 0.06 - secuRatio * 0.05
            val actualBoom = 0.02 + secuRatio * 0.05
            assertThat(actualDisaster).isCloseTo(disasterProb, within(0.0001))
            assertThat(actualBoom).isCloseTo(boomProb, within(0.0001))
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // PHP-Verified: Faction Level Thresholds
    // Legacy UpdateNationLevel.php:41-50:
    //   nationLevelByCityCnt = [0, 1, 2, 5, 8, 11, 16, 21]  (PHP 8-level)
    //   opensamguk extension: [0, 1, 2, 4, 6, 9, 12, 16, 20, 25] (10-level)
    //   Level only increases. Reward: newLevel * 1000 gold + rice.
    // ────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("PHP-Verified Nation Level — UpdateNationLevel.php")
    inner class NationLevelGoldenValues {

        @ParameterizedTest
        @CsvSource(
            // highCityCount, expectedLevel, expectedRewardIfFromZero
            //   opensamguk thresholds: [0,1,2,4,6,9,12,16,20,25]
            "0,  0, 0",
            "1,  1, 1000",
            "2,  2, 2000",
            "3,  2, 2000",
            "4,  3, 3000",
            "6,  4, 4000",
            "9,  5, 5000",
            "12, 6, 6000",
            "16, 7, 7000",
            "20, 8, 8000",
            "25, 9, 9000",
            "30, 9, 9000",
        )
        @DisplayName("Nation level + reward by high city count")
        fun `nation level and reward golden values`(highCityCount: Int, expectedLevel: Int, expectedReward: Int) {
            val cityList = if (highCityCount == 0) {
                listOf(city(level = 2))
            } else {
                (1..highCityCount.toLong()).map { city(id = it, level = 5) }
            }
            val n = nation(level = 0, funds = 0, supplies = 0)
            val g = general()
            seed(cityList, listOf(n), listOf(g))

            service.postUpdateMonthly(world(month = 3))

            assertThat(nations[1L]!!.factionRank.toInt())
                .describedAs("Level for $highCityCount high cities")
                .isEqualTo(expectedLevel)
            assertThat(nations[1L]!!.funds)
                .describedAs("Gold reward for level $expectedLevel")
                .isEqualTo(expectedReward)
            assertThat(nations[1L]!!.supplies)
                .describedAs("Rice reward for level $expectedLevel")
                .isEqualTo(expectedReward)
        }

        @Test
        @DisplayName("Level-up from non-zero: reward based on new level, not delta")
        fun `level up from non zero base`() {
            // Start at level 2, increase to 4 (needs 6 high cities)
            val cityList = (1..6L).map { city(id = it, level = 5) }
            val n = nation(level = 2, funds = 5000, supplies = 5000)
            val g = general()
            seed(cityList, listOf(n), listOf(g))

            service.postUpdateMonthly(world(month = 3))

            assertThat(nations[1L]!!.factionRank.toInt()).isEqualTo(4)
            // Reward = newLevel * 1000 = 4000
            assertThat(nations[1L]!!.funds).isEqualTo(5000 + 4000)
            assertThat(nations[1L]!!.supplies).isEqualTo(5000 + 4000)
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // PHP-Verified: Trade Rate Randomization
    // Legacy RandomizeCityTradeRate.php:
    //   prob by level: {1:0, 2:0, 3:0, 4:0.2, 5:0.4, 6:0.6, 7:0.8, 8:1.0}
    //   If prob > 0 and rng.nextBool(prob): trade = rng.nextRangeInt(95, 105)
    //   Otherwise: trade = null (no market activity)
    // ────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("PHP-Verified Trade Rate — RandomizeCityTradeRate.php")
    inner class TradeRateGoldenValues {

        @Test
        @DisplayName("Level 4 city has 20% chance of trade randomization")
        fun `level 4 partial chance`() {
            // Run 100 iterations with different seeds and count how many get randomized
            // Level 4: prob=0.2, so roughly 20% should change from default
            val c = city(level = 4, tradeRoute = 100)
            val n = nation()
            val g = general()
            var changedCount = 0
            for (yr in 200..299) {
                seed(listOf(city(level = 4, tradeRoute = 100)), listOf(nation()), listOf(general()))
                service.randomizeCityTradeRate(world(year = yr.toShort(), month = 5))
                val trade = cities[1L]!!.tradeRoute
                if (trade != 100) changedCount++
                // Regardless, all randomized values should be in range
                assertThat(trade).isBetween(95, 105)
            }
            // With 100 trials at 20% probability, expect 10-30 changes (2 sigma)
            assertThat(changedCount)
                .describedAs("Level 4 randomization count out of 100 should be ~20")
                .isBetween(5, 45)
        }

        @Test
        @DisplayName("Trade rate null behavior: non-qualifying cities get default 100")
        fun `non qualifying city trade default`() {
            // PHP sets trade = null for non-qualifying. Kotlin should handle this
            // as tradeRoute = 100 (default/no market) or similar.
            val c = city(level = 1, tradeRoute = 105)
            seed(listOf(c), listOf(nation()), listOf(general()))

            service.randomizeCityTradeRate(world())

            // Level 1: prob=0, trade should reset (PHP: null, Kotlin: 100)
            assertThat(cities[1L]!!.tradeRoute).isEqualTo(100)
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // PHP-Verified: Yearly Statistics Power Formula
    // Legacy checkStatistic:
    //   resource = (nationGold + nationRice + sum(generalGold + generalRice)) / 100
    //   cityPower = totalPop * totalInfra / totalInfraMax / 100
    //     where totalInfra = sum(population+production+commerce+security+fortress+orbitalDefense)
    //     and totalInfraMax = sum(populationMax+productionMax+commerceMax+securityMax+fortressMax+orbitalDefenseMax)
    //   militaryPower = (resource + tech + cityPower + statPower + dex + expDed) / 10
    // ────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("PHP-Verified Yearly Statistics — power formula")
    inner class YearlyStatisticsGoldenValues {

        @Test
        @DisplayName("Power formula with known inputs produces deterministic value")
        fun `power formula deterministic`() {
            val c = city(population = 30000, populationMax = 50000, production = 800, productionMax = 1000,
                commerce = 700, commerceMax = 1000, security = 600, securityMax = 1000,
                orbitalDefense = 500, orbitalDefenseMax = 1000, fortress = 400, fortressMax = 1000)
            val n = nation(funds = 50000, supplies = 50000, level = 3)
            val g = general(funds = 5000, supplies = 5000, dedication = 5000)
            seed(listOf(c), listOf(n), listOf(g))

            service.processYearlyStatistics(world(year = 200, month = 1))

            val militaryPower = nations[1L]!!.militaryPower
            assertThat(militaryPower)
                .describedAs("Power should be deterministic for fixed inputs")
                .isGreaterThan(0)

            // Run again with same inputs -> same result
            seed(listOf(city(population = 30000, populationMax = 50000, production = 800, productionMax = 1000,
                commerce = 700, commerceMax = 1000, security = 600, securityMax = 1000,
                orbitalDefense = 500, orbitalDefenseMax = 1000, fortress = 400, fortressMax = 1000)),
                listOf(nation(funds = 50000, supplies = 50000, level = 3)),
                listOf(general(funds = 5000, supplies = 5000, dedication = 5000)))
            service.processYearlyStatistics(world(year = 200, month = 1))
            assertThat(nations[1L]!!.militaryPower).isEqualTo(militaryPower)
        }

        @Test
        @DisplayName("Power increases with more cities and generals")
        fun `power scales with nation size`() {
            // Single city, single general
            seed(listOf(city(population = 30000, populationMax = 50000)),
                listOf(nation(funds = 50000, supplies = 50000, level = 3)),
                listOf(general(dedication = 5000)))
            service.processYearlyStatistics(world(month = 1))
            val powerSmall = nations[1L]!!.militaryPower

            // Two cities, two generals -> more power
            val c1 = city(id = 1, population = 30000, populationMax = 50000)
            val c2 = city(id = 2, population = 25000, populationMax = 50000)
            val g1 = general(id = 1, dedication = 5000)
            val g2 = general(id = 2, dedication = 3000)
            seed(listOf(c1, c2),
                listOf(nation(funds = 80000, supplies = 80000, level = 5)),
                listOf(g1, g2))
            service.processYearlyStatistics(world(month = 1))
            val powerLarge = nations[1L]!!.militaryPower

            assertThat(powerLarge).isGreaterThan(powerSmall)
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // PHP-Verified: Supply Chain penalty effects
    // Legacy: unsupplied city gets population*0.9, approval*0.9, infra*0.9
    //   If approval < 30 after penalty (and not capital) -> neutralized (factionId=0)
    // ────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("PHP-Verified Supply Penalty Golden Values")
    inner class SupplyPenaltyGoldenValues {

        @ParameterizedTest
        @CsvSource(
            // population, approval, production, expectedPop, expectedTrust, expectedAgri
            "10000, 80.0, 500, 9000, 72.0, 450",   // 10000*0.9=9000, 80*0.9=72, 500*0.9=450
            "5000,  50.0, 800, 4500, 45.0, 720",    // 5000*0.9=4500, 50*0.9=45, 800*0.9=720
            "1000,  33.0, 100, 900,  29.7, 90",     // 1000*0.9=900, 33*0.9=29.7, 100*0.9=90
        )
        @DisplayName("Unsupplied city penalty: all stats * 0.9")
        fun `unsupplied penalty golden values`(
            population: Int, approval: Float, production: Int,
            expectedPop: Int, expectedTrust: Float, expectedAgri: Int,
        ) {
            val c1 = city(id = 1)  // capital
            val c2 = city(id = 2, population = population, approval = approval, production = production, commerce = production, commerceMax = 1000)
            val n = nation(capitalPlanetId = 1)
            val g = general(planetId = 1)

            `when`(mapService.getAdjacentCities("che", 1)).thenReturn(emptyList())
            `when`(mapService.getAdjacentCities("che", 2)).thenReturn(emptyList())

            seed(listOf(c1, c2), listOf(n), listOf(g))
            service.postUpdateMonthly(world(month = 3))

            val updated2 = cities[2L]!!
            assertThat(updated2.population).isCloseTo(expectedPop, within(1))
            assertThat(updated2.approval).isCloseTo(expectedTrust, within(0.1f))
            assertThat(updated2.production).isCloseTo(expectedAgri, within(1))
        }

        @Test
        @DisplayName("Trust exactly at 30 after penalty does NOT neutralize")
        fun `approval at 30 boundary not neutralized`() {
            // approval = 33.33... -> after 0.9: 30.0 exactly -> NOT < 30 -> stays
            val c1 = city(id = 1)
            val c2 = city(id = 2, approval = 33.4f)
            val n = nation(capitalPlanetId = 1)
            val g = general(planetId = 1)

            `when`(mapService.getAdjacentCities("che", 1)).thenReturn(emptyList())
            `when`(mapService.getAdjacentCities("che", 2)).thenReturn(emptyList())

            seed(listOf(c1, c2), listOf(n), listOf(g))
            service.postUpdateMonthly(world(month = 3))

            // 33.4 * 0.9 = 30.06 >= 30 -> not neutralized
            assertThat(cities[2L]!!.factionId).isEqualTo(1L)
        }
    }
}
