package com.openlogh.engine

import com.openlogh.entity.SessionState
import com.openlogh.repository.SessionStateRepository
import com.openlogh.service.AlliancePoliticsService
import com.openlogh.service.EmpirePoliticsService
import com.openlogh.service.FezzanEndingService
import com.openlogh.service.FezzanService
import com.openlogh.service.GameEventService
import com.openlogh.service.OfflinePlayerAIService
import com.openlogh.service.ShipyardProductionService
import com.openlogh.service.TacticalBattleService
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
        tickEngine = TickEngine(
            realtimeService,
            sessionStateRepository,
            gameEventService,
            mock(EmpirePoliticsService::class.java),
            mock(AlliancePoliticsService::class.java),
            mock(FezzanService::class.java),
            mock(FezzanAiService::class.java),
            mock(FezzanEndingService::class.java),
            mock(TacticalBattleService::class.java),
            mock(Gin7EconomyService::class.java),
            mock(ShipyardProductionService::class.java),
            mock(FleetSortieCostService::class.java),
            mock(OfflinePlayerAIService::class.java),
        )

        // stub save to return the argument
        `when`(sessionStateRepository.save(any(SessionState::class.java))).thenAnswer { it.arguments[0] }
    }

    private fun createWorld(): SessionState {
        return SessionState().apply {
            id = 1.toShort()
            currentYear = 801.toShort()
            currentMonth = 1.toShort()
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

    // ── Tick broadcast tests ──

    @Test
    fun test_tick_broadcast_fires_every_10_ticks() {
        val world = createWorld()
        // After 10 ticks, broadcastTickState should fire once (at tick 10)
        repeat(10) { tickEngine.processTick(world) }
        verify(gameEventService, times(1)).broadcastTickState(world)

        // After 20 total ticks, should fire twice (at tick 10 and 20)
        repeat(10) { tickEngine.processTick(world) }
        verify(gameEventService, times(2)).broadcastTickState(world)
    }

    @Test
    fun test_tick_broadcast_not_on_first_tick() {
        val world = createWorld()
        // After 1 tick (tickCount=1), broadcast should NOT fire (1 % 10 != 0)
        tickEngine.processTick(world)
        verify(gameEventService, never()).broadcastTickState(world)
    }

    @Test
    fun test_tick_broadcast_not_at_9_ticks() {
        val world = createWorld()
        // After 9 ticks, broadcast should NOT fire
        repeat(9) { tickEngine.processTick(world) }
        verify(gameEventService, never()).broadcastTickState(world)
    }

    /**
     * Command duration integration verification:
     *
     * Command durations use fortress-clock time (OffsetDateTime), not game time.
     * A 300-second command takes 300 real seconds = 300 ticks = 7,200 game-seconds.
     *
     * Flow:
     * - RealtimeService.submitCommand sets commandEndTime = OffsetDateTime.now().plusSeconds(duration)
     * - TickEngine calls processCompletedCommands every tick, which checks commandEndTime < now
     * - This means command durations work in real fortress-clock seconds regardless of game-time speedup
     * - No changes needed to RealtimeService -- the existing fortress-clock duration mechanism is correct
     *
     * Broadcast does not interfere: broadcastTickState fires every 10 ticks (after save),
     * while processCompletedCommands fires every tick (before save). They are independent.
     */
    @Test
    fun test_command_duration_compatible_with_broadcast() {
        val world = createWorld()
        // Run 20 ticks -- verify both processCompletedCommands (every tick)
        // and broadcastTickState (every 10 ticks) fire independently
        repeat(20) { tickEngine.processTick(world) }
        verify(realtimeService, times(20)).processCompletedCommands(world)
        verify(gameEventService, times(2)).broadcastTickState(world)
    }
}
