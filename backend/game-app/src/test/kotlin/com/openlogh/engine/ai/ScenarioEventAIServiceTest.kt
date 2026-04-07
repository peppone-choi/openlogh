package com.openlogh.engine.ai

import com.openlogh.engine.turn.cqrs.memory.DiplomacySnapshot
import com.openlogh.engine.turn.cqrs.memory.FactionSnapshot
import com.openlogh.engine.turn.cqrs.memory.FactionTurnSnapshot
import com.openlogh.engine.turn.cqrs.memory.FleetSnapshot
import com.openlogh.engine.turn.cqrs.memory.OfficerSnapshot
import com.openlogh.engine.turn.cqrs.memory.OfficerTurnSnapshot
import com.openlogh.engine.turn.cqrs.memory.PlanetSnapshot
import com.openlogh.engine.turn.cqrs.memory.UnitCrewSnapshot
import com.openlogh.engine.turn.cqrs.persist.JpaWorldPortFactory
import com.openlogh.engine.turn.cqrs.persist.WorldPorts
import com.openlogh.engine.turn.cqrs.persist.toSnapshot
import com.openlogh.entity.Faction
import com.openlogh.entity.Officer
import com.openlogh.entity.SessionState
import com.openlogh.service.GameEventService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.time.OffsetDateTime

/**
 * TDD tests for ScenarioEventAIService.
 * Covers: civil war trigger, PLANNING skip, idempotency, non-empire skip, no-session no-op.
 *
 * Uses hand-rolled WorldPorts spy to avoid Kotlin non-null + Mockito matcher NPE issues.
 */
class ScenarioEventAIServiceTest {

    private lateinit var worldPortFactory: JpaWorldPortFactory
    private lateinit var worldPorts: SpyWorldPorts
    private lateinit var gameEventService: SpyGameEventService
    private lateinit var service: ScenarioEventAIService

    @BeforeEach
    fun setUp() {
        worldPorts = SpyWorldPorts()
        gameEventService = SpyGameEventService()
        worldPortFactory = mock(JpaWorldPortFactory::class.java)
        service = ScenarioEventAIService(worldPortFactory, gameEventService)
    }

    private fun createWorld(tickCount: Long = 200L): SessionState =
        SessionState(id = 1, scenarioCode = "test", currentYear = 800, currentMonth = 1, tickSeconds = 300).also {
            it.tickCount = tickCount
        }

    private fun createEmpireFaction(
        id: Long = 1L,
        coupPhase: String? = null,
        coupLeaderId: Long? = null,
        coupActiveSinceTick: Long? = null,
        civilWarTriggered: Boolean? = null,
    ): Faction {
        val meta: MutableMap<String, Any> = mutableMapOf()
        if (coupPhase != null) meta["coupPhase"] = coupPhase
        if (coupLeaderId != null) meta["coupLeaderId"] = coupLeaderId
        if (coupActiveSinceTick != null) meta["coupActiveSinceTick"] = coupActiveSinceTick
        if (civilWarTriggered != null) meta["civilWarTriggered"] = civilWarTriggered
        return Faction(
            id = id,
            sessionId = 1,
            name = "Galactic Empire",
            color = "#FFD700",
            factionType = "empire",
            meta = meta,
        )
    }

    private fun createOfficer(
        id: Long,
        factionId: Long = 1L,
        ships: Int = 1000,
        coupSupport: Boolean = false,
    ): Officer {
        val meta: MutableMap<String, Any> = mutableMapOf()
        if (coupSupport) meta["coupSupport"] = true
        return Officer(
            id = id,
            sessionId = 1,
            name = "Officer$id",
            factionId = factionId,
            ships = ships,
            meta = meta,
            turnTime = OffsetDateTime.now(),
        )
    }

    // ── Tests ──────────────────────────────────────────────────────────────

    /**
     * Test 1: Empire faction with coupPhase=ACTIVE and sufficient military support triggers civil war.
     */
    @Test
    fun `civil war triggers when coup is ACTIVE and military support threshold met`() {
        val world = createWorld(tickCount = 200L)
        val faction = createEmpireFaction(coupPhase = "ACTIVE", coupLeaderId = 10L, coupActiveSinceTick = 0L)
        // supporters: 600 ships, loyalist: 500 → ratio = 0.545 >= 0.4
        val supporter1 = createOfficer(id = 10L, ships = 300, coupSupport = true)
        val supporter2 = createOfficer(id = 11L, ships = 300, coupSupport = true)
        val loyalist   = createOfficer(id = 12L, ships = 500, coupSupport = false)

        worldPorts.factions = listOf(faction.toSnapshot())
        worldPorts.officersByFactionMap[1L] = listOf(supporter1, supporter2, loyalist).map { it.toSnapshot() }
        `when`(worldPortFactory.create(1L)).thenReturn(worldPorts)

        service.processTick(world)

        assertEquals(1, gameEventService.broadcastCalls.size) {
            "Expected 1 broadcastWorldUpdate call but got ${gameEventService.broadcastCalls.size}"
        }
        assertEquals(1L, gameEventService.broadcastCalls[0].first) {
            "Expected sessionId=1 but got ${gameEventService.broadcastCalls[0].first}"
        }
        assertEquals(1, worldPorts.putFactionCalls.size) {
            "Expected 1 putFaction call but got ${worldPorts.putFactionCalls.size}"
        }
        assertTrue(worldPorts.putFactionCalls[0].meta["civilWarTriggered"] == true) {
            "Expected meta[civilWarTriggered]=true but got: ${worldPorts.putFactionCalls[0].meta}"
        }
    }

    /**
     * Test 2: Empire faction with coupPhase=PLANNING does NOT trigger civil war.
     */
    @Test
    fun `civil war does NOT trigger when coup is in PLANNING phase`() {
        val world = createWorld()
        val faction = createEmpireFaction(coupPhase = "PLANNING", coupLeaderId = 10L, coupActiveSinceTick = 0L)
        val officer = createOfficer(id = 10L, ships = 500, coupSupport = true)

        worldPorts.factions = listOf(faction.toSnapshot())
        worldPorts.officersByFactionMap[1L] = listOf(officer.toSnapshot())
        `when`(worldPortFactory.create(1L)).thenReturn(worldPorts)

        service.processTick(world)

        assertEquals(0, gameEventService.broadcastCalls.size) { "No broadcast expected for PLANNING phase" }
        assertEquals(0, worldPorts.putFactionCalls.size) { "No putFaction expected for PLANNING phase" }
    }

    /**
     * Test 3: Idempotency — faction with civilWarTriggered=true is NOT triggered again.
     */
    @Test
    fun `civil war trigger is idempotent - already triggered faction is skipped`() {
        val world = createWorld()
        val faction = createEmpireFaction(
            coupPhase = "ACTIVE", coupLeaderId = 10L, coupActiveSinceTick = 0L,
            civilWarTriggered = true,
        )
        val supporter = createOfficer(id = 10L, ships = 500, coupSupport = true)

        worldPorts.factions = listOf(faction.toSnapshot())
        worldPorts.officersByFactionMap[1L] = listOf(supporter.toSnapshot())
        `when`(worldPortFactory.create(1L)).thenReturn(worldPorts)

        service.processTick(world)

        assertEquals(0, gameEventService.broadcastCalls.size) { "No broadcast expected (already triggered)" }
        assertEquals(0, worldPorts.putFactionCalls.size) { "No putFaction expected (already triggered)" }
    }

    /**
     * Test 4: Non-empire factions (alliance, fezzan) are not evaluated for coup conditions.
     */
    @Test
    fun `non-empire factions are not evaluated for coup conditions`() {
        val world = createWorld()
        val allianceFaction = Faction(
            id = 2L, sessionId = 1, name = "FPA", color = "#0000FF", factionType = "alliance",
            meta = mutableMapOf("coupPhase" to "ACTIVE", "coupLeaderId" to 20L, "coupActiveSinceTick" to 0L),
        )
        val fezzanFaction = Faction(
            id = 3L, sessionId = 1, name = "Fezzan", color = "#00FF00", factionType = "fezzan",
            meta = mutableMapOf("coupPhase" to "ACTIVE", "coupLeaderId" to 30L, "coupActiveSinceTick" to 0L),
        )

        worldPorts.factions = listOf(allianceFaction.toSnapshot(), fezzanFaction.toSnapshot())
        `when`(worldPortFactory.create(1L)).thenReturn(worldPorts)

        service.processTick(world)

        assertEquals(0, gameEventService.broadcastCalls.size) { "Non-empire must not trigger broadcast" }
        assertEquals(0, worldPorts.putFactionCalls.size) { "Non-empire must not trigger putFaction" }
        assertEquals(0, worldPorts.officersByFactionCalls.size) { "Non-empire must not query officers" }
    }

    /**
     * Test 5: processTick() with empty faction list is a no-op — no exception thrown.
     */
    @Test
    fun `processTick with empty faction list is a no-op`() {
        val world = createWorld()
        worldPorts.factions = emptyList()
        `when`(worldPortFactory.create(1L)).thenReturn(worldPorts)

        // Should not throw
        service.processTick(world)

        assertEquals(0, gameEventService.broadcastCalls.size) { "Empty session must produce no broadcasts" }
    }

    // ── Spy helpers ────────────────────────────────────────────────────────

    /**
     * Hand-rolled WorldPorts spy that records putFaction calls and supports
     * configurable allFactions / officersByFaction results.
     * Avoids all Mockito non-null type issues.
     */
    private inner class SpyWorldPorts : WorldPorts {
        var factions: List<FactionSnapshot> = emptyList()
        val officersByFactionMap: MutableMap<Long, List<OfficerSnapshot>> = mutableMapOf()
        val putFactionCalls: MutableList<FactionSnapshot> = mutableListOf()
        val officersByFactionCalls: MutableList<Long> = mutableListOf()

        override fun allFactions(): Collection<FactionSnapshot> = factions
        override fun officersByFaction(factionId: Long): List<OfficerSnapshot> {
            officersByFactionCalls.add(factionId)
            return officersByFactionMap[factionId] ?: emptyList()
        }
        override fun putFaction(snapshot: FactionSnapshot) { putFactionCalls.add(snapshot) }

        // Unused stubs
        override fun officer(id: Long): OfficerSnapshot? = null
        override fun planet(id: Long): PlanetSnapshot? = null
        override fun faction(id: Long): FactionSnapshot? = null
        override fun fleet(id: Long): FleetSnapshot? = null
        override fun unitCrew(id: Long): UnitCrewSnapshot? = null
        override fun diplomacy(id: Long): DiplomacySnapshot? = null
        override fun allOfficers(): Collection<OfficerSnapshot> = emptyList()
        override fun allPlanets(): Collection<PlanetSnapshot> = emptyList()
        override fun allFleets(): Collection<FleetSnapshot> = emptyList()
        override fun allUnitCrews(): Collection<UnitCrewSnapshot> = emptyList()
        override fun allDiplomacies(): Collection<DiplomacySnapshot> = emptyList()
        override fun officersByPlanet(planetId: Long): List<OfficerSnapshot> = emptyList()
        override fun planetsByFaction(factionId: Long): List<PlanetSnapshot> = emptyList()
        override fun diplomaciesByFaction(factionId: Long): List<DiplomacySnapshot> = emptyList()
        override fun activeDiplomacies(): List<DiplomacySnapshot> = emptyList()
        override fun officerTurns(officerId: Long): List<OfficerTurnSnapshot> = emptyList()
        override fun factionTurns(factionId: Long, officerLevel: Short): List<FactionTurnSnapshot> = emptyList()
        override fun putOfficer(snapshot: OfficerSnapshot) {}
        override fun putPlanet(snapshot: PlanetSnapshot) {}
        override fun putFleet(snapshot: FleetSnapshot) {}
        override fun putUnitCrew(snapshot: UnitCrewSnapshot) {}
        override fun putDiplomacy(snapshot: DiplomacySnapshot) {}
        override fun deleteOfficer(id: Long) {}
        override fun deletePlanet(id: Long) {}
        override fun deleteFaction(id: Long) {}
        override fun deleteFleet(id: Long) {}
        override fun deleteUnitCrew(id: Long) {}
        override fun deleteDiplomacy(id: Long) {}
        override fun setOfficerTurns(officerId: Long, turns: List<OfficerTurnSnapshot>) {}
        override fun setFactionTurns(factionId: Long, officerLevel: Short, turns: List<FactionTurnSnapshot>) {}
        override fun removeOfficerTurns(officerId: Long) {}
        override fun removeFactionTurns(factionId: Long, officerLevel: Short) {}
    }

    /**
     * Hand-rolled GameEventService spy that records broadcastWorldUpdate calls.
     */
    private inner class SpyGameEventService : GameEventService(
        mock(org.springframework.messaging.simp.SimpMessagingTemplate::class.java),
        mock(com.openlogh.repository.WorldHistoryRepository::class.java),
        mock(org.springframework.context.ApplicationEventPublisher::class.java),
    ) {
        val broadcastCalls: MutableList<Pair<Long, Any>> = mutableListOf()

        override fun broadcastWorldUpdate(worldId: Long, data: Any) {
            broadcastCalls.add(Pair(worldId, data))
        }
    }
}
