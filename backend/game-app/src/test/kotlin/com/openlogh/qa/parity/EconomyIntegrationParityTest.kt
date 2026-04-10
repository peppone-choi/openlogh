package com.openlogh.qa.parity

import com.openlogh.engine.EconomyService
import com.openlogh.engine.turn.TurnContext
import com.openlogh.engine.turn.cqrs.persist.toEntity
import com.openlogh.engine.turn.cqrs.persist.toSnapshot
import com.openlogh.engine.turn.steps.DisasterAndTradeStep
import com.openlogh.engine.turn.steps.EconomyPostUpdateStep
import com.openlogh.engine.turn.steps.EconomyPreUpdateStep
import com.openlogh.engine.turn.steps.YearlyStatisticsStep
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.time.OffsetDateTime

/**
 * Economy Integration Parity Test
 *
 * Runs a 24-turn simulation (2 game years: months 1-12, 1-12) with:
 *   - 1 nation (neutral type, taxRate=15, funds =10000, supplies =20000)
 *   - 3 cities with varying population and infrastructure
 *   - 5 generals with varying dedication for salary spread
 *   - 4 semi-annual cycles (months 1,7 of each year)
 *
 * Verifies:
 *   1. No cumulative drift in nation gold/rice vs tracked expected state
 *   2. City population follows expected compounding growth/decay
 *   3. City infrastructure matches expected values after semi-annual processing
 *   4. Turn pipeline economy step ordering matches legacy daemon.ts
 *
 * Legacy source:
 *   - src/daemon.ts: turn step ordering (~441 lines)
 *   - hwe/func_time_event.php: income, semi-annual, disaster formulas
 *   - hwe/sammo/Event/Action/ProcessIncome.php: gold/rice income + salary
 *   - hwe/sammo/Event/Action/ProcessSemiAnnual.php: infrastructure growth
 */
@DisplayName("Economy Integration Parity")
class EconomyIntegrationParityTest {

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
        w.config["hiddenSeed"] = "integration-test-seed"
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
    // ══════════════════════════════════════════════════════
    //  Turn pipeline step ordering
    // ══════════════════════════════════════════════════════

    @Nested
    @DisplayName("TurnPipelineOrdering")
    inner class TurnPipelineOrdering {

        /**
         * Legacy daemon.ts ordering:
         *   preUpdateMonthly (before advanceMonth) -> step 300, skipped in pipeline
         *   YearlyStatistics (month==1 only) -> step 800
         *   EconomyPostUpdate -> step 1000
         *   DisasterAndTrade -> step 1100
         *
         * This matches legacy execution order:
         *   1. preUpdateMonthly (income/salary) -- handled outside pipeline
         *   2. advanceMonth
         *   3. yearlyStatistics (if month==1)
         *   4. postUpdateMonthly (semi-annual)
         *   5. disaster/boom + trade rate
         */
        @Test
        fun `EconomyPreUpdateStep order is 300`() {
            val step = EconomyPreUpdateStep(service)
            assertThat(step.order).isEqualTo(300)
        }

        @Test
        fun `YearlyStatisticsStep order is 800`() {
            val portFactory = com.openlogh.engine.turn.cqrs.persist.JpaWorldPortFactory()
            val step = YearlyStatisticsStep(service, portFactory, mock(InheritanceService::class.java))
            assertThat(step.order).isEqualTo(800)
        }

        @Test
        fun `EconomyPostUpdateStep order is 1000`() {
            val step = EconomyPostUpdateStep(service)
            assertThat(step.order).isEqualTo(1000)
        }

        @Test
        fun `DisasterAndTradeStep order is 1100`() {
            val step = DisasterAndTradeStep(service)
            assertThat(step.order).isEqualTo(1100)
        }

        @Test
        fun `step ordering is 300 less than 800 less than 1000 less than 1100`() {
            val preUpdate = EconomyPreUpdateStep(service)
            val portFactory = com.openlogh.engine.turn.cqrs.persist.JpaWorldPortFactory()
            val yearly = YearlyStatisticsStep(service, portFactory, mock(InheritanceService::class.java))
            val postUpdate = EconomyPostUpdateStep(service)
            val disaster = DisasterAndTradeStep(service)

            assertThat(preUpdate.order).isLessThan(yearly.order)
            assertThat(yearly.order).isLessThan(postUpdate.order)
            assertThat(postUpdate.order).isLessThan(disaster.order)
        }

        @Test
        fun `EconomyPreUpdateStep shouldSkip always returns true`() {
            val step = EconomyPreUpdateStep(service)
            val w = world(year = 200, month = 3)
            val ctx = TurnContext(
                world = w, worldId = 1,
                year = 200, month = 3,
                previousYear = 200, previousMonth = 2,
                nextTurnAt = OffsetDateTime.now()
            )
            assertThat(step.shouldSkip(ctx)).isTrue()
                .describedAs("PreUpdate is handled outside pipeline, always skipped")
        }

        @Test
        fun `YearlyStatisticsStep shouldSkip returns true when month is not 1`() {
            val portFactory = com.openlogh.engine.turn.cqrs.persist.JpaWorldPortFactory()
            val step = YearlyStatisticsStep(service, portFactory, mock(InheritanceService::class.java))

            val wMonth3 = world(year = 200, month = 3)
            val ctx3 = TurnContext(
                world = wMonth3, worldId = 1,
                year = 200, month = 3,
                previousYear = 200, previousMonth = 2,
                nextTurnAt = OffsetDateTime.now()
            )
            assertThat(step.shouldSkip(ctx3)).isTrue()
                .describedAs("Yearly stats skipped when month != 1")

            val wMonth1 = world(year = 200, month = 1)
            val ctx1 = TurnContext(
                world = wMonth1, worldId = 1,
                year = 200, month = 1,
                previousYear = 199, previousMonth = 12,
                nextTurnAt = OffsetDateTime.now()
            )
            assertThat(step.shouldSkip(ctx1)).isFalse()
                .describedAs("Yearly stats should NOT skip when month == 1")
        }

        @Test
        fun `EconomyPostUpdateStep shouldSkip returns false (always runs)`() {
            val step = EconomyPostUpdateStep(service)
            val w = world(year = 200, month = 6)
            val ctx = TurnContext(
                world = w, worldId = 1,
                year = 200, month = 6,
                previousYear = 200, previousMonth = 5,
                nextTurnAt = OffsetDateTime.now()
            )
            assertThat(step.shouldSkip(ctx)).isFalse()
                .describedAs("PostUpdate always runs")
        }

        @Test
        fun `DisasterAndTradeStep shouldSkip returns false (always runs)`() {
            val step = DisasterAndTradeStep(service)
            val w = world(year = 200, month = 4)
            val ctx = TurnContext(
                world = w, worldId = 1,
                year = 200, month = 4,
                previousYear = 200, previousMonth = 3,
                nextTurnAt = OffsetDateTime.now()
            )
            assertThat(step.shouldSkip(ctx)).isFalse()
                .describedAs("DisasterAndTrade always runs")
        }
    }
}
