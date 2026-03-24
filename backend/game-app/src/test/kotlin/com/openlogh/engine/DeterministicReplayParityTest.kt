package com.openlogh.engine

import com.openlogh.engine.turn.cqrs.memory.DirtyTracker
import com.openlogh.engine.turn.cqrs.memory.InMemoryTurnProcessor
import com.openlogh.engine.turn.cqrs.memory.InMemoryWorldState
import com.openlogh.entity.SessionState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime

class DeterministicReplayParityTest {

    private val processor = InMemoryTurnProcessor(
        org.mockito.Mockito.mock(com.openlogh.engine.EconomyService::class.java),
        org.mockito.Mockito.mock(com.openlogh.engine.EventService::class.java),
        org.mockito.Mockito.mock(com.openlogh.engine.OfficerMaintenanceService::class.java),
        org.mockito.Mockito.mock(com.openlogh.engine.CommandPointService::class.java),
        org.mockito.Mockito.mock(com.openlogh.engine.AgeGrowthService::class.java),
        org.mockito.Mockito.mock(com.openlogh.engine.UnificationService::class.java),
        org.mockito.Mockito.mock(com.openlogh.engine.modifier.OfficerLevelModifier::class.java),
    )

    @Test
    fun `processor runs without crashing on empty world state`() {
        val world = makeWorld(1)
        val state = InMemoryWorldState(worldId = 1)
        val tracker = DirtyTracker()

        val result = processor.process(world, state, tracker)

        assertEquals(0, result.advancedTurns)
    }

    @Test
    fun `same replay fixture produces same canonical output`() {
        val result1 = processor.process(makeWorld(1), InMemoryWorldState(worldId = 1), DirtyTracker())
        val result2 = processor.process(makeWorld(1), InMemoryWorldState(worldId = 1), DirtyTracker())

        assertEquals(result1.advancedTurns, result2.advancedTurns)
        assertEquals(result1.events.size, result2.events.size)
    }

    @Test
    fun `processor returns zero advanced turns and empty events on stub world`() {
        val world = makeWorld(2)
        val state = InMemoryWorldState(worldId = 2)
        val tracker = DirtyTracker()

        val result = processor.process(world, state, tracker)

        assertEquals(0, result.advancedTurns)
        assertEquals(emptyList<Any>(), result.events)
    }

    private fun makeWorld(id: Short): SessionState = SessionState(
        id = id,
        name = "replay-world-$id",
        scenarioCode = "test",
        currentYear = 200,
        currentMonth = 1,
        tickSeconds = 300,
        updatedAt = OffsetDateTime.now().minusSeconds(301),
    )
}
