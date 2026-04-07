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
import com.openlogh.model.CommandGroup
import com.openlogh.repository.OfficerRepository
import com.openlogh.service.GameEventService
import com.openlogh.service.OfflinePlayerAIService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.time.OffsetDateTime

/**
 * AI system integration test covering AI-01 through AI-04.
 * Uses mocked / hand-rolled collaborators — no Spring context needed.
 *
 * AI-01: PersonalityTrait weights influence command group scoring
 * AI-02: Offline player AI processes via AiCommandBridge
 * AI-03: FactionAIScheduler processes exactly one faction per processTick call
 * AI-04: ScenarioEventAIService triggers civil war on ACTIVE coup + threshold
 */
class AiIntegrationTest {

    // ── Helpers ─────────────────────────────────────────────────────────────

    private fun createWorld(id: Short = 1, tickCount: Long = 100L): SessionState =
        SessionState(id = id, scenarioCode = "test", currentYear = 800, currentMonth = 1, tickSeconds = 300).also {
            it.tickCount = tickCount
        }

    private fun createOfficer(
        id: Long = 1L,
        factionId: Long = 1L,
        ships: Int = 0,
        leadership: Short = 50,
        command: Short = 50,
        intelligence: Short = 50,
        politics: Short = 50,
        administration: Short = 50,
        mobility: Short = 50,
        attack: Short = 50,
        defense: Short = 50,
        npcState: Short = 0,
        userId: Long? = null,
        personality: String = "BALANCED",
        lastAccessAt: OffsetDateTime? = null,
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
            leadership = leadership,
            command = command,
            intelligence = intelligence,
            politics = politics,
            administration = administration,
            mobility = mobility,
            attack = attack,
            defense = defense,
            npcState = npcState,
            userId = userId,
            personality = personality,
            lastAccessAt = lastAccessAt,
            meta = meta,
            turnTime = OffsetDateTime.now(),
        )
    }

    private fun createEmpireFaction(
        id: Long = 1L,
        coupPhase: String? = null,
        coupLeaderId: Long? = null,
        coupActiveSinceTick: Long? = null,
    ): Faction {
        val meta: MutableMap<String, Any> = mutableMapOf()
        if (coupPhase != null) meta["coupPhase"] = coupPhase
        if (coupLeaderId != null) meta["coupLeaderId"] = coupLeaderId
        if (coupActiveSinceTick != null) meta["coupActiveSinceTick"] = coupActiveSinceTick
        return Faction(
            id = id,
            sessionId = 1,
            name = "Galactic Empire",
            color = "#FFD700",
            factionType = "empire",
            meta = meta,
        )
    }

    // ── AI-01: UtilityScorer personality weighting ──────────────────────────

    /**
     * AI-01: AGGRESSIVE trait scores OPERATIONS and COMMAND groups highest.
     * Tests that PersonalityTrait weights correctly bias the utility scores.
     */
    @Test
    fun `AI-01 - AGGRESSIVE trait scores OPERATIONS and COMMAND groups highest`() {
        // Officer with high attack/command/mobility — the AGGRESSIVE stat drivers
        val officer = createOfficer(
            attack = 90,
            command = 85,
            mobility = 80,
            defense = 30,
            administration = 30,
            intelligence = 40,
            politics = 35,
            leadership = 50,
        )

        val scores = UtilityScorer.scoreGroups(officer, PersonalityTrait.AGGRESSIVE)

        // OPERATIONS drives: attack(1.5x), command(1.3x), mobility(1.2x)
        val opsScore = scores[CommandGroup.OPERATIONS.name] ?: 0.0
        // COMMAND drives: command(1.3x), leadership(1.0x)
        val cmdScore = scores[CommandGroup.COMMAND.name] ?: 0.0
        // LOGISTICS drives: administration(0.8x), defense(0.7x) — should be lower
        val logScore = scores[CommandGroup.LOGISTICS.name] ?: 0.0

        assertTrue(opsScore > logScore) {
            "AGGRESSIVE OPERATIONS score ($opsScore) should exceed LOGISTICS ($logScore)"
        }
        assertTrue(cmdScore > logScore) {
            "AGGRESSIVE COMMAND score ($cmdScore) should exceed LOGISTICS ($logScore)"
        }
        // OPERATIONS should be the highest group for an AGGRESSIVE officer with attack/command/mobility focus
        val maxEntry = scores.maxByOrNull { it.value }!!
        assertTrue(maxEntry.key == CommandGroup.OPERATIONS.name || maxEntry.key == CommandGroup.COMMAND.name) {
            "AGGRESSIVE officer should score OPERATIONS or COMMAND highest, but top was: ${maxEntry.key} (${maxEntry.value})"
        }
    }

    // ── AI-02: Offline player AI via OfflinePlayerAIService ──────────────────

    /**
     * AI-02: Officer with userId + lastAccessAt > 30 min ago triggers AI processing.
     * Verifies AiCommandBridge.executeAiCommand is called via a hand-rolled spy.
     */
    @Test
    fun `AI-02 - offline player with 30+ min inactivity is processed by AI`() {
        val world = createWorld()

        // Officer who is a player (npcState=0, userId set) and went offline 2 hours ago
        val offlineOfficer = createOfficer(
            id = 1L,
            factionId = 1L,
            npcState = 0,
            userId = 999L,
            personality = "AGGRESSIVE",
            lastAccessAt = OffsetDateTime.now().minusHours(2),
        )

        val officerRepository = mock(OfficerRepository::class.java)
        val officerAI = mock(OfficerAI::class.java)
        val bridgeSpy = SpyAiCommandBridge()

        `when`(officerRepository.findBySessionId(1L)).thenReturn(listOf(offlineOfficer))

        val service = OfflinePlayerAIService(officerRepository, officerAI, bridgeSpy)
        service.processOfflinePlayers(world)

        // Verify executeAiCommand was called exactly once for officer 1
        assertEquals(1, bridgeSpy.calls.size) {
            "Expected 1 executeAiCommand call but got ${bridgeSpy.calls.size}"
        }
        assertEquals(1L, bridgeSpy.calls[0].first.id) {
            "Expected executeAiCommand called for officer id=1"
        }
        assertEquals(PersonalityTrait.AGGRESSIVE, bridgeSpy.calls[0].third) {
            "Expected AGGRESSIVE personality trait"
        }
    }

    // ── AI-03: FactionAIScheduler round-robin ─────────────────────────────────

    /**
     * AI-03: FactionAIScheduler processes exactly one faction per processTick() call.
     * With 4 active factions and 4 processTick calls, each faction processed once.
     */
    @Test
    fun `AI-03 - FactionAIScheduler processes exactly one faction per processTick call`() {
        val world = createWorld()
        val worldPortFactory = mock(JpaWorldPortFactory::class.java)
        val worldPorts = SimpleWorldPorts()

        // 4 factions (empire x2, alliance x2) — all active
        worldPorts.factions = listOf(
            makeFactionSnapshot(1L, "empire"),
            makeFactionSnapshot(2L, "empire"),
            makeFactionSnapshot(3L, "alliance"),
            makeFactionSnapshot(4L, "alliance"),
        )
        `when`(worldPortFactory.create(1L)).thenReturn(worldPorts)

        val spyPort = SpyFactionAIPort()
        val scheduler = FactionAIScheduler(spyPort, worldPortFactory)

        // Call processTick 4 times — each should invoke decideNationAction exactly once
        repeat(4) { scheduler.processTick(world) }

        assertEquals(4, spyPort.callCount) {
            "Expected decideNationAction called 4 times (1 per tick), got ${spyPort.callCount}"
        }
    }

    // ── AI-04: ScenarioEventAIService civil war trigger ───────────────────────

    /**
     * AI-04: ScenarioEventAIService triggers civil war when coup is ACTIVE and
     * military support threshold is met.
     */
    @Test
    fun `AI-04 - civil war triggers when coup is ACTIVE and military threshold met`() {
        val world = createWorld(tickCount = 200L)
        val faction = createEmpireFaction(
            coupPhase = "ACTIVE",
            coupLeaderId = 10L,
            coupActiveSinceTick = 0L,
        )
        // 60% support → above 40% threshold
        val supporter1 = createOfficer(id = 10L, ships = 300, coupSupport = true)
        val supporter2 = createOfficer(id = 11L, ships = 300, coupSupport = true)
        val loyalist   = createOfficer(id = 12L, ships = 400, coupSupport = false)

        val worldPorts = SimpleWorldPorts()
        worldPorts.factions = listOf(faction.toSnapshot())
        worldPorts.officersByFactionData[1L] = listOf(supporter1, supporter2, loyalist).map { it.toSnapshot() }

        val worldPortFactory = mock(JpaWorldPortFactory::class.java)
        `when`(worldPortFactory.create(1L)).thenReturn(worldPorts)

        val gameEventService = SpyGameEventService()
        val service = ScenarioEventAIService(worldPortFactory, gameEventService)

        service.processTick(world)

        assertEquals(1, gameEventService.broadcastCalls.size) {
            "Expected 1 broadcastWorldUpdate call but got ${gameEventService.broadcastCalls.size}"
        }
        assertEquals(1L, gameEventService.broadcastCalls[0].first) {
            "Expected sessionId=1"
        }
        assertEquals(1, worldPorts.putFactionCalls.size) {
            "Expected putFaction to be called once"
        }
        assertTrue(worldPorts.putFactionCalls[0].meta["civilWarTriggered"] == true) {
            "Expected civilWarTriggered=true in faction meta"
        }
    }

    // ── Internal helpers ───────────────────────────────────────────────────────

    private fun makeFactionSnapshot(id: Long, factionType: String): FactionSnapshot =
        Faction(
            id = id,
            sessionId = 1,
            name = "Faction$id",
            color = "#FF0000",
            factionType = factionType,
            factionRank = 1,
        ).toSnapshot()

    /** Minimal WorldPorts for AI-03 and AI-04 tests. */
    private inner class SimpleWorldPorts : WorldPorts {
        var factions: List<FactionSnapshot> = emptyList()
        val officersByFactionData: MutableMap<Long, List<OfficerSnapshot>> = mutableMapOf()
        val putFactionCalls: MutableList<FactionSnapshot> = mutableListOf()

        override fun allFactions(): Collection<FactionSnapshot> = factions
        override fun officersByFaction(factionId: Long): List<OfficerSnapshot> =
            officersByFactionData[factionId] ?: emptyList()
        override fun putFaction(snapshot: FactionSnapshot) { putFactionCalls.add(snapshot) }

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
     * Spy FactionAIPort that counts decideNationAction calls.
     * Avoids Mockito any() NPE with Kotlin non-null params.
     */
    private inner class SpyFactionAIPort : FactionAIPort {
        var callCount = 0
        val calledFactionIds = mutableListOf<Long>()
        override fun decideNationAction(nation: Faction, world: SessionState, rng: kotlin.random.Random): String {
            callCount++
            calledFactionIds.add(nation.id)
            return "Nation휴식"
        }
    }

    /**
     * Spy AiCommandBridge that records executeAiCommand calls.
     * Avoids Mockito any() NPE with Kotlin non-null params.
     */
    private inner class SpyAiCommandBridge : AiCommandBridge(
        mock(com.openlogh.command.CommandExecutor::class.java),
        mock(com.openlogh.command.CommandRegistry::class.java),
    ) {
        // Triple<Officer, SessionState, PersonalityTrait>
        val calls: MutableList<Triple<Officer, SessionState, PersonalityTrait>> = mutableListOf()
        override fun executeAiCommand(officer: Officer, world: SessionState, trait: PersonalityTrait): String {
            calls.add(Triple(officer, world, trait))
            return "대기"
        }
    }

    /** Spy GameEventService that records broadcastWorldUpdate calls. */
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
