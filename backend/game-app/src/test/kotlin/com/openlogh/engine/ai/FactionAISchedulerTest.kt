package com.openlogh.engine.ai

import com.openlogh.entity.Faction
import com.openlogh.entity.SessionState
import com.openlogh.engine.turn.cqrs.persist.JpaWorldPortFactory
import com.openlogh.engine.turn.cqrs.persist.WorldPorts
import com.openlogh.engine.turn.cqrs.persist.toSnapshot
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.Mockito.times
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.ArgumentCaptor

/**
 * Tests for FactionAIScheduler — slot-based round-robin faction AI processing.
 * Verifies: round-robin ordering, empty guard, eliminated faction skip, fezzan skip,
 * and exactly-once-per-tick call contract.
 *
 * Uses ArgumentCaptor and doReturn to work with Kotlin non-null types and plain Mockito.
 */
class FactionAISchedulerTest {

    private lateinit var factionAIPort: FactionAIPort
    private lateinit var worldPortFactory: JpaWorldPortFactory
    private lateinit var worldPorts: WorldPorts
    private lateinit var scheduler: FactionAIScheduler

    @BeforeEach
    fun setUp() {
        factionAIPort = mock(FactionAIPort::class.java)
        worldPortFactory = mock(JpaWorldPortFactory::class.java)
        worldPorts = mock(WorldPorts::class.java)
        scheduler = FactionAIScheduler(factionAIPort, worldPortFactory)
    }

    private fun createWorld(id: Short = 1): SessionState =
        SessionState(id = id, scenarioCode = "test", currentYear = 800, currentMonth = 1, tickSeconds = 300)

    private fun faction(
        id: Long,
        factionType: String = "empire",
        factionRank: Short = 1,
        sessionId: Long = 1L,
    ) = Faction(
        id = id,
        sessionId = sessionId,
        name = "Faction$id",
        color = "#FF0000",
        factionType = factionType,
        factionRank = factionRank,
    ).toSnapshot()

    /**
     * Stub factionAIPort to return a fixed value for any call.
     * Uses doReturn to avoid Kotlin NPE when setting up stubs for non-null params.
     */
    private fun stubAiDecide(result: String = "Nation휴식") {
        // doAnswer lets us intercept without caring about argument types
        doReturn(result).`when`(factionAIPort)
            .decideNationAction(
                org.mockito.ArgumentMatchers.isNull<Faction>() ?: org.mockito.ArgumentMatchers.any(Faction::class.java),
                org.mockito.ArgumentMatchers.isNull<SessionState>() ?: org.mockito.ArgumentMatchers.any(SessionState::class.java),
                org.mockito.ArgumentMatchers.isNull() ?: org.mockito.ArgumentMatchers.any()
            )
    }

    /**
     * Capture all Faction IDs passed to decideNationAction across N processTick calls.
     */
    private fun captureCalledFactionIds(world: SessionState, ticks: Int): List<Long> {
        val captor = ArgumentCaptor.forClass(Faction::class.java)
        repeat(ticks) { scheduler.processTick(world) }
        verify(factionAIPort, times(ticks)).decideNationAction(
            captor.capture(),
            org.mockito.ArgumentMatchers.any(SessionState::class.java),
            org.mockito.ArgumentMatchers.any()
        )
        return captor.allValues.map { it.id }
    }

    /**
     * Test 1: Round-robin ordering — 3 factions [A, B, C] processed in order A→B→C→A.
     */
    @Test
    fun `processTick cycles through factions in round-robin order`() {
        val world = createWorld()
        `when`(worldPortFactory.create(1L)).thenReturn(worldPorts)
        `when`(worldPorts.allFactions()).thenReturn(listOf(
            faction(1L, "empire"),
            faction(2L, "alliance"),
            faction(3L, "rebel"),
        ))

        // Capture faction IDs for 4 calls — should be [1, 2, 3, 1]
        val ids = captureCalledFactionIds(world, 4)
        assertEquals(listOf(1L, 2L, 3L, 1L), ids)
    }

    /**
     * Test 2: With 0 active factions, processTick() does nothing (no exception).
     */
    @Test
    fun `processTick does nothing when no active factions exist`() {
        val world = createWorld()
        `when`(worldPortFactory.create(1L)).thenReturn(worldPorts)
        `when`(worldPorts.allFactions()).thenReturn(emptyList())

        scheduler.processTick(world)

        verifyNoInteractions(factionAIPort)
    }

    /**
     * Test 3: Eliminated factions (factionRank == 0) are skipped.
     */
    @Test
    fun `processTick skips eliminated factions with factionRank zero`() {
        val world = createWorld()
        `when`(worldPortFactory.create(1L)).thenReturn(worldPorts)
        `when`(worldPorts.allFactions()).thenReturn(listOf(
            faction(1L, "empire", factionRank = 1),
            faction(2L, "alliance", factionRank = 0),  // eliminated
        ))

        // 2 processTick calls → both should target faction 1 (only active one)
        val ids = captureCalledFactionIds(world, 2)
        assertEquals(listOf(1L, 1L), ids)
    }

    /**
     * Test 4: "fezzan" factionType is skipped — handled by dedicated FezzanAiService.
     */
    @Test
    fun `processTick skips fezzan faction type`() {
        val world = createWorld()
        `when`(worldPortFactory.create(1L)).thenReturn(worldPorts)
        `when`(worldPorts.allFactions()).thenReturn(listOf(
            faction(1L, "empire", factionRank = 1),
            faction(2L, "fezzan", factionRank = 1),    // must be skipped
            faction(3L, "alliance", factionRank = 1),
        ))

        // 2 processTick calls → factions 1 and 3 (fezzan skipped)
        val ids = captureCalledFactionIds(world, 2)
        assertEquals(listOf(1L, 3L), ids)
    }

    /**
     * Test 5: decideNationAction is called exactly once per processTick() invocation.
     */
    @Test
    fun `processTick calls decideNationAction exactly once per invocation`() {
        val world = createWorld()
        `when`(worldPortFactory.create(1L)).thenReturn(worldPorts)
        `when`(worldPorts.allFactions()).thenReturn(
            (1L..5L).map { faction(it, if (it % 2 == 0L) "alliance" else "empire") }
        )

        // 5 factions, 5 calls → exactly 5 decideNationAction calls (1 per tick)
        val ids = captureCalledFactionIds(world, 5)
        assertEquals(5, ids.size)
    }
}
