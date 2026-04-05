package com.openlogh.engine

import com.openlogh.entity.SessionState
import com.openlogh.repository.SessionStateRepository
import com.openlogh.service.GameEventService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

class TickEngineTest {

    private lateinit var realtimeService: RealtimeService
    private lateinit var sessionStateRepository: SessionStateRepository
    private lateinit var gameEventService: GameEventService
    private lateinit var tickEngine: TickEngine

    @BeforeEach
    fun setUp() {
        realtimeService = mock(RealtimeService::class.java)
        sessionStateRepository = mock(SessionStateRepository::class.java)
        gameEventService = mock(GameEventService::class.java)
        tickEngine = TickEngine(realtimeService, sessionStateRepository, gameEventService)

        // stub save to return the argument
        `when`(sessionStateRepository.save(any(SessionState::class.java))).thenAnswer { it.arguments[0] }
    }

    private fun createWorld(): SessionState {
        return SessionState().apply {
            id = 1
            currentYear = 801
            currentMonth = 1
            realtimeMode = true
            gameTimeSec = 0
            tickCount = 0
        }
    }

    @Test
    fun test_single_tick_advances_game_time() {
        val world = createWorld()
        tickEngine.processTick(world)
        assertEquals(1L, world.tickCount)
        assertEquals(24L, world.gameTimeSec)
    }

    @Test
    fun test_100_ticks() {
        val world = createWorld()
        repeat(100) { tickEngine.processTick(world) }
        assertEquals(100L, world.tickCount)
        assertEquals(2400L, world.gameTimeSec)
    }

    @Test
    fun test_cp_regen_fires_at_300_ticks() {
        val world = createWorld()
        // Run 300 ticks
        repeat(300) { tickEngine.processTick(world) }
        // CP regen should have fired exactly once (at tick 300)
        verify(realtimeService, times(1)).regenerateCommandPoints(world)
    }

    @Test
    fun test_cp_regen_does_not_fire_at_tick_zero() {
        val world = createWorld()
        // After 1 tick (tickCount=1), regen should NOT fire
        tickEngine.processTick(world)
        verify(realtimeService, never()).regenerateCommandPoints(world)
    }

    @Test
    fun test_cp_regen_does_not_fire_at_299_or_301() {
        val world = createWorld()
        // Run 299 ticks -- no regen
        repeat(299) { tickEngine.processTick(world) }
        verify(realtimeService, never()).regenerateCommandPoints(world)

        // Tick 300 -- regen fires
        tickEngine.processTick(world)
        verify(realtimeService, times(1)).regenerateCommandPoints(world)

        // Tick 301 -- no additional regen
        tickEngine.processTick(world)
        verify(realtimeService, times(1)).regenerateCommandPoints(world)
    }

    @Test
    fun test_month_boundary_crossing() {
        val world = createWorld().apply {
            // One tick before boundary: 2,592,000 - 24 = 2,591,976
            gameTimeSec = GameTimeConstants.GAME_SECONDS_PER_MONTH - GameTimeConstants.GAME_SECONDS_PER_TICK
        }
        val prevMonth = world.currentMonth.toInt()

        tickEngine.processTick(world)

        // gameTimeSec should reset to 0 (2,591,976 + 24 - 2,592,000 = 0)
        assertEquals(0L, world.gameTimeSec)
        // month should advance by 1
        assertEquals(prevMonth + 1, world.currentMonth.toInt())
    }

    @Test
    fun test_year_rollover() {
        val world = createWorld().apply {
            currentMonth = 12
            currentYear = 801
            gameTimeSec = GameTimeConstants.GAME_SECONDS_PER_MONTH - GameTimeConstants.GAME_SECONDS_PER_TICK
        }

        tickEngine.processTick(world)

        assertEquals(1, world.currentMonth.toInt())
        assertEquals(802, world.currentYear.toInt())
        assertEquals(0L, world.gameTimeSec)
    }

    @Test
    fun test_monthly_pipeline_fires_only_on_boundary() {
        val world = createWorld()
        // 100 normal ticks -- no month boundary crossed
        repeat(100) { tickEngine.processTick(world) }
        // broadcastTurnAdvance should NOT have been called (proxy for monthly pipeline)
        verify(gameEventService, never()).broadcastTurnAdvance(anyLong(), anyInt(), anyInt())
    }

    @Test
    fun test_completed_commands_processed_every_tick() {
        val world = createWorld()
        repeat(5) { tickEngine.processTick(world) }
        // processCompletedCommands should be called on every tick
        verify(realtimeService, times(5)).processCompletedCommands(world)
    }
}
