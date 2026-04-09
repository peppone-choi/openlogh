package com.openlogh.engine.ai

import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandExecutor
import com.openlogh.command.CommandResult
import com.openlogh.engine.turn.cqrs.persist.JpaWorldPortFactory
import com.openlogh.entity.Diplomacy
import com.openlogh.entity.Faction
import com.openlogh.entity.Fleet
import com.openlogh.entity.Officer
import com.openlogh.entity.OperationPlan
import com.openlogh.entity.Planet
import com.openlogh.entity.SessionState
import com.openlogh.model.OperationStatus
import com.openlogh.repository.DiplomacyRepository
import com.openlogh.repository.FactionRepository
import com.openlogh.repository.FleetRepository
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.OperationPlanRepository
import com.openlogh.repository.PlanetRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.time.OffsetDateTime
import java.util.Optional
import kotlin.random.Random

/**
 * Phase 13 Plan 02: test-double CommandExecutor that records every invocation
 * and returns a configurable success/fail result. Mockito cannot stub Kotlin
 * `suspend` functions without mockito-kotlin (not on the :game-app classpath,
 * per Phase 12 decision), so we subclass the real CommandExecutor and override
 * the suspend method directly.
 */
private class StubCommandExecutor(
    var result: CommandResult = CommandResult(success = true, logs = listOf("ok")),
) : CommandExecutor(
    commandRegistry = mock(com.openlogh.command.CommandRegistry::class.java),
    officerRepository = mock(OfficerRepository::class.java),
    planetRepository = mock(PlanetRepository::class.java),
    factionRepository = mock(FactionRepository::class.java),
    diplomacyRepository = mock(DiplomacyRepository::class.java),
    diplomacyService = mock(com.openlogh.engine.DiplomacyService::class.java),
    mapService = mock(com.openlogh.service.MapService::class.java),
    statChangeService = mock(com.openlogh.engine.StatChangeService::class.java),
    modifierService = mock(com.openlogh.engine.modifier.ModifierService::class.java),
    messageService = mock(com.openlogh.service.MessageService::class.java),
) {
    data class Invocation(
        val actionCode: String,
        val generalId: Long,
        val arg: Map<String, Any>?,
    )

    val invocations = mutableListOf<Invocation>()

    override suspend fun executeOfficerCommand(
        actionCode: String,
        general: Officer,
        env: CommandEnv,
        arg: Map<String, Any>?,
        city: Planet?,
        nation: Faction?,
        rng: Random,
    ): CommandResult {
        invocations += Invocation(actionCode, general.id, arg)
        return result
    }
}

class NationAITest {

    private lateinit var ai: FactionAI
    private lateinit var planetRepository: PlanetRepository
    private lateinit var officerRepository: OfficerRepository
    private lateinit var factionRepository: FactionRepository
    private lateinit var diplomacyRepository: DiplomacyRepository
    private lateinit var fleetRepository: FleetRepository
    private lateinit var operationPlanRepository: OperationPlanRepository
    private lateinit var commandExecutor: StubCommandExecutor

    @BeforeEach
    fun setUp() {
        planetRepository = mock(PlanetRepository::class.java)
        officerRepository = mock(OfficerRepository::class.java)
        factionRepository = mock(FactionRepository::class.java)
        diplomacyRepository = mock(DiplomacyRepository::class.java)
        fleetRepository = mock(FleetRepository::class.java)
        operationPlanRepository = mock(OperationPlanRepository::class.java)
        commandExecutor = StubCommandExecutor()
        ai = FactionAI(
            worldPortFactory = JpaWorldPortFactory(
                officerRepository = officerRepository,
                planetRepository = planetRepository,
                factionRepository = factionRepository,
                diplomacyRepository = diplomacyRepository,
            ),
            commandExecutor = commandExecutor,
            fleetRepository = fleetRepository,
            operationPlanRepository = operationPlanRepository,
        )
    }

    private fun createWorld(year: Short = 200, month: Short = 3): SessionState {
        return SessionState(id = 1, scenarioCode = "test", currentYear = year, currentMonth = month, tickSeconds = 300)
    }

    private fun createNation(
        id: Long = 1,
        funds: Int = 10000,
        supplies: Int = 10000,
        militaryPower: Int = 100,
        warState: Short = 0,
        chiefOfficerId: Long = 0,
    ): Faction {
        return Faction(
            id = id,
            sessionId = 1,
            name = "국가$id",
            color = "#FF0000",
            funds = funds,
            supplies = supplies,
            militaryPower = militaryPower,
            warState = warState,
            capitalPlanetId = 1,
            chiefOfficerId = chiefOfficerId,
        )
    }

    private fun createGeneral(
        id: Long = 1,
        factionId: Long = 1,
        planetId: Long = 1,
        npcState: Short = 2,
        officerLevel: Short = 1,
        dedication: Int = 100,
    ): Officer {
        return Officer(
            id = id,
            sessionId = 1,
            name = "장수$id",
            factionId = factionId,
            planetId = planetId,
            npcState = npcState,
            officerLevel = officerLevel,
            dedication = dedication,
            leadership = 50,
            command = 50,
            intelligence = 50,
            politics = 50,
            administration = 50,
            funds = 1000,
            supplies = 1000,
            turnTime = OffsetDateTime.now(),
        )
    }

    private fun createCity(
        id: Long = 1,
        factionId: Long = 1,
        level: Short = 5,
        frontState: Short = 0,
    ): Planet {
        return Planet(
            id = id,
            sessionId = 1,
            name = "도시$id",
            factionId = factionId,
            level = level,
            frontState = frontState,
            population = 10000,
            populationMax = 50000,
            production = 500,
            productionMax = 1000,
            commerce = 500,
            commerceMax = 1000,
            security = 500,
            securityMax = 1000,
        )
    }

    private fun setupRepos(
        nation: Faction,
        cities: List<Planet>,
        generals: List<Officer>,
        allNations: List<Faction> = listOf(nation),
        diplomacies: List<Diplomacy> = emptyList(),
    ) {
        `when`(planetRepository.findByFactionId(nation.id)).thenReturn(cities)
        `when`(officerRepository.findBySessionIdAndFactionId(1L, nation.id)).thenReturn(generals)
        `when`(factionRepository.findBySessionId(1L)).thenReturn(allNations)
        `when`(diplomacyRepository.findBySessionIdAndIsDeadFalse(1L)).thenReturn(diplomacies)
    }

    @Test
    fun `should declare war when stronger and sufficiently prepared`() {
        val nation = createNation(funds = 20000, supplies = 20000, militaryPower = 200)
        val target = createNation(id = 2, militaryPower = 50)
        `when`(planetRepository.findByFactionId(1)).thenReturn(listOf(createCity(id = 1), createCity(id = 2)))
        `when`(planetRepository.findByFactionId(2)).thenReturn(listOf(createCity(id = 3, factionId = 2)))
        `when`(officerRepository.findBySessionIdAndFactionId(1L, 1L)).thenReturn(listOf(createGeneral(id = 1), createGeneral(id = 2)))
        `when`(officerRepository.findBySessionIdAndFactionId(1L, 2L)).thenReturn(listOf(createGeneral(id = 3, factionId = 2)))

        assertTrue(ai.shouldDeclareWar(nation, target, createWorld()))
    }

    @Test
    fun `should not declare war when weaker than target`() {
        val nation = createNation(funds = 20000, supplies = 20000, militaryPower = 50)
        val target = createNation(id = 2, militaryPower = 200)
        `when`(planetRepository.findByFactionId(1)).thenReturn(listOf(createCity()))
        `when`(planetRepository.findByFactionId(2)).thenReturn(listOf(createCity(id = 2, factionId = 2)))
        `when`(officerRepository.findBySessionIdAndFactionId(1L, 1L)).thenReturn(listOf(createGeneral()))
        `when`(officerRepository.findBySessionIdAndFactionId(1L, 2L)).thenReturn(listOf(createGeneral(id = 2, factionId = 2)))

        assertFalse(ai.shouldDeclareWar(nation, target, createWorld()))
    }

    @Test
    fun `decideNationAction returns Nation휴식 when nation gold is very low`() {
        val nation = createNation(funds = 500)
        setupRepos(nation, listOf(createCity()), listOf(createGeneral()))

        val action = ai.decideNationAction(nation, createWorld(), Random(42))
        assertEquals("Nation휴식", action)
    }

    @Test
    fun `decideNationAction creates operation plan when at war with available fleets`() {
        val sovereign = createGeneral(id = 1, officerLevel = 10, dedication = 100)
        sovereign.personality = "AGGRESSIVE"
        sovereign.intelligence = 80  // spy — ensures fog-free evaluation

        val nation = createNation(
            funds = 20000, supplies = 20000, warState = 1, chiefOfficerId = 1L,
        )

        // Own front-line planet with our sovereign stationed on it
        val ownCity = createCity(id = 1, factionId = 1, frontState = 1)

        // Enemy planet — front-line + strategically valuable so CONQUEST wins
        val enemyCity = createCity(id = 10, factionId = 2, frontState = 1).apply {
            production = 1000
            commerce = 1000
            tradeRoute = 200
            population = 50000
        }

        // Enemy officer with a tiny ship count so the selector picks this target
        val enemyOfficer = createGeneral(id = 50, factionId = 2, planetId = 10)
        enemyOfficer.ships = 100

        val enemyNation = createNation(id = 2, militaryPower = 50)

        // setupRepos only wires faction-scoped queries; the strategic AI also
        // calls the session-wide queries via the port layer, so set those too.
        setupRepos(
            nation,
            listOf(ownCity),
            listOf(sovereign),
            allNations = listOf(nation, enemyNation),
        )
        `when`(officerRepository.findBySessionId(1L)).thenReturn(listOf(sovereign, enemyOfficer))
        `when`(planetRepository.findBySessionId(1L)).thenReturn(listOf(ownCity, enemyCity))
        `when`(officerRepository.findById(1L)).thenReturn(Optional.of(sovereign))

        // One own fleet strong enough to exceed the 1.3x margin vs the tiny enemy
        val fleet = Fleet(
            id = 100L,
            sessionId = 1L,
            factionId = 1L,
            leaderOfficerId = 1L,
            planetId = 1L,
            currentUnits = 30,
        )
        `when`(fleetRepository.findBySessionIdAndFactionId(1L, 1L)).thenReturn(listOf(fleet))

        // No existing operations — all fleets free to commit
        `when`(
            operationPlanRepository.findBySessionIdAndFactionIdAndStatusIn(
                1L, 1L, listOf(OperationStatus.PENDING, OperationStatus.ACTIVE),
            ),
        ).thenReturn(emptyList())

        val action = ai.decideNationAction(nation, createWorld(), Random(42))

        assertEquals("작전계획", action)
        assertTrue(commandExecutor.invocations.isNotEmpty(), "expected at least one 작전계획 invocation")
        val first = commandExecutor.invocations.first()
        assertEquals("작전계획", first.actionCode)
        assertEquals(1L, first.generalId)
        val arg = first.arg
        assertTrue(arg != null)
        assertTrue(arg!!["objective"] in listOf("CONQUEST", "SWEEP", "DEFENSE"))
        @Suppress("UNCHECKED_CAST")
        val fleetIds = arg["participantFleetIds"] as List<Long>
        assertTrue(100L in fleetIds)
    }

    @Test
    fun `decideNationAction returns Nation휴식 when at war but no sovereign`() {
        val nation = createNation(funds = 20000, supplies = 20000, warState = 1, chiefOfficerId = 0L)
        setupRepos(nation, listOf(createCity()), listOf(createGeneral()))

        val action = ai.decideNationAction(nation, createWorld(), Random(42))
        assertEquals("Nation휴식", action)
        assertTrue(commandExecutor.invocations.isEmpty())
    }

    @Test
    fun `decideNationAction returns Nation휴식 when at war but all fleets committed`() {
        val sovereign = createGeneral(id = 1, officerLevel = 10)
        sovereign.personality = "BALANCED"

        val nation = createNation(
            funds = 20000, supplies = 20000, warState = 1, chiefOfficerId = 1L,
        )
        val ownCity = createCity(id = 1, factionId = 1, frontState = 1)

        setupRepos(nation, listOf(ownCity), listOf(sovereign))
        `when`(officerRepository.findBySessionId(1L)).thenReturn(listOf(sovereign))
        `when`(planetRepository.findBySessionId(1L)).thenReturn(listOf(ownCity))
        `when`(officerRepository.findById(1L)).thenReturn(Optional.of(sovereign))

        // All fleets already committed to an existing operation
        val committedFleet = Fleet(
            id = 100L, sessionId = 1L, factionId = 1L,
            leaderOfficerId = 1L, planetId = 1L, currentUnits = 30,
        )
        `when`(fleetRepository.findBySessionIdAndFactionId(1L, 1L)).thenReturn(listOf(committedFleet))

        val existingOp = OperationPlan(
            id = 1L,
            sessionId = 1L,
            factionId = 1L,
            participantFleetIds = mutableListOf(100L),
            status = OperationStatus.ACTIVE,
        )
        `when`(
            operationPlanRepository.findBySessionIdAndFactionIdAndStatusIn(
                1L, 1L, listOf(OperationStatus.PENDING, OperationStatus.ACTIVE),
            ),
        ).thenReturn(listOf(existingOp))

        val action = ai.decideNationAction(nation, createWorld(), Random(42))
        assertEquals("Nation휴식", action)
        assertTrue(commandExecutor.invocations.isEmpty())
    }

    @Test
    fun `decideNationAction returns 발령 when unassigned generals exist`() {
        val nation = createNation(funds = 20000, supplies = 20000)
        val unassigned = createGeneral(id = 1, officerLevel = 0, npcState = 2)
        setupRepos(nation, listOf(createCity()), listOf(unassigned))

        val action = ai.decideNationAction(nation, createWorld(), Random(42))
        assertEquals("발령", action)
    }

    @Test
    fun `decideNationAction returns 증축 when nation can expand low-level city`() {
        val nation = createNation(funds = 20000, supplies = 20000)
        val cities = listOf(createCity(id = 1, level = 4), createCity(id = 2, level = 5))
        val generals = listOf(createGeneral(id = 1, officerLevel = 1, dedication = 120))
        setupRepos(nation, cities, generals)

        val action = ai.decideNationAction(nation, createWorld(), Random(42))
        assertEquals("증축", action)
    }

    @Test
    fun `decideNationAction returns 포상 when generals have low dedication and nation has enough gold`() {
        val nation = createNation(funds = 15000, supplies = 15000)
        val cities = listOf(createCity(level = 5))
        val lowDedGeneral = createGeneral(id = 1, officerLevel = 1, dedication = 50)
        setupRepos(nation, cities, listOf(lowDedGeneral))

        val action = ai.decideNationAction(nation, createWorld(), Random(42))
        assertEquals("포상", action)
    }

    @Test
    fun `decideNationAction falls back to Nation휴식 when no trigger condition matches`() {
        val nation = createNation(funds = 20000, supplies = 20000, militaryPower = 100)
        val cities = listOf(createCity(level = 5))
        val generals = listOf(createGeneral(id = 1, officerLevel = 3, dedication = 120))
        setupRepos(
            nation = nation,
            cities = cities,
            generals = generals,
            allNations = listOf(nation),
            diplomacies = emptyList(),
        )

        val action = ai.decideNationAction(nation, createWorld(), Random(42))
        assertEquals("Nation휴식", action)
    }

    // ========== Policy threshold integration ==========

    @Test
    fun `decideNationAction returns Nation휴식 when gold below reqNationGold threshold`() {
        // Default reqNationGold = 10000, so funds = 1500 should trigger rest
        val nation = createNation(funds = 1500, supplies = 10000)
        setupRepos(nation, listOf(createCity()), listOf(createGeneral()))

        val action = ai.decideNationAction(nation, createWorld(), Random(42))
        assertEquals("Nation휴식", action)
    }

    @Test
    fun `decideNationAction returns Nation휴식 when rice below reqNationRice threshold`() {
        // Default reqNationRice = 12000, so supplies = 1500 should trigger rest
        val nation = createNation(funds = 10000, supplies = 1500)
        setupRepos(nation, listOf(createCity()), listOf(createGeneral()))

        val action = ai.decideNationAction(nation, createWorld(), Random(42))
        assertEquals("Nation휴식", action)
    }

    // ========== Capital relocation ==========

    @Test
    fun `decideNationAction does not consider 천도 with only one city`() {
        val nation = createNation(funds = 10000, supplies = 10000)
        nation.capitalPlanetId = 1
        val city = createCity(id = 1, level = 5)
        val generals = listOf(createGeneral(id = 1, officerLevel = 3, dedication = 120))
        setupRepos(nation, listOf(city), generals)

        // With a single city, 천도 should not appear as candidate
        val action = ai.decideNationAction(nation, createWorld(), Random(42))
        assertNotEquals("천도", action)
    }
}
