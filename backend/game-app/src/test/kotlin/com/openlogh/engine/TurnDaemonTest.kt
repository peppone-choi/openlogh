package com.openlogh.engine

import com.openlogh.engine.turn.cqrs.TurnCoordinator
import com.openlogh.entity.SessionState
import com.openlogh.repository.SessionStateRepository
import com.openlogh.service.GameEventService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.time.OffsetDateTime

class TurnDaemonTest {

    private lateinit var daemon: TurnDaemon
    private lateinit var turnService: TurnService
    private lateinit var turnCoordinator: TurnCoordinator
    private lateinit var realtimeService: RealtimeService
    private lateinit var sessionStateRepository: SessionStateRepository
    private lateinit var gameEventService: GameEventService

    @Suppress("UNCHECKED_CAST")
    private fun <T> anyNonNull(): T = any<T>() as T

    @BeforeEach
    fun setUp() {
        turnService = mock(TurnService::class.java)
        turnCoordinator = mock(TurnCoordinator::class.java)
        realtimeService = mock(RealtimeService::class.java)
        sessionStateRepository = mock(SessionStateRepository::class.java)
        gameEventService = mock(GameEventService::class.java)

        daemon = TurnDaemon(
            turnService,
            turnCoordinator,
            realtimeService,
            mock(TickEngine::class.java),
            "test-sha",
            false,
            sessionStateRepository,
            gameEventService,
        )
    }

    private fun createWorld(id: Short = 1, realtimeMode: Boolean = false, gatewayActive: Any? = null): SessionState {
        val meta = if (gatewayActive != null) mutableMapOf<String, Any>("gatewayActive" to gatewayActive)
        else mutableMapOf()
        return SessionState(
            id = id,
            scenarioCode = "test",
            currentYear = 200,
            currentMonth = 6,
            tickSeconds = 300,
            updatedAt = OffsetDateTime.now(),
            realtimeMode = realtimeMode,
            meta = meta,
        )
    }

    @Test
    fun `tick calls turnService for non-realtime worlds`() {
        val world = createWorld(realtimeMode = false)
        `when`(sessionStateRepository.findByCommitSha("test-sha")).thenReturn(listOf(world))

        daemon.tick()

        verify(turnService).processWorld(world)
        verify(turnCoordinator, never()).processSession(anyNonNull())
        verify(realtimeService, never()).processCompletedCommands(anyNonNull())
    }

    @Test
    fun `tick calls realtimeService for realtime worlds`() {
        val world = createWorld(realtimeMode = true)
        `when`(sessionStateRepository.findByCommitSha("test-sha")).thenReturn(listOf(world))

        daemon.tick()

        verify(realtimeService).processCompletedCommands(world)
        verify(realtimeService).regenerateCommandPoints(world)
        verify(turnService, never()).processWorld(anyNonNull())
        verify(turnCoordinator, never()).processSession(anyNonNull())
    }

    @Test
    fun `tick calls turnCoordinator for non-realtime worlds when cqrs is enabled`() {
        val world = createWorld(realtimeMode = false)
        `when`(sessionStateRepository.findByCommitSha("test-sha")).thenReturn(listOf(world))

        val cqrsDaemon = TurnDaemon(
            turnService,
            turnCoordinator,
            realtimeService,
            mock(TickEngine::class.java),
            "test-sha",
            true,
            sessionStateRepository,
            gameEventService,
        )
        cqrsDaemon.tick()

        verify(turnCoordinator).processSession(world)
        verify(turnService, never()).processWorld(anyNonNull())
    }

    @Test
    fun `tick skips worlds with gatewayActive false`() {
        val world = createWorld(gatewayActive = false)
        `when`(sessionStateRepository.findByCommitSha("test-sha")).thenReturn(listOf(world))

        daemon.tick()

        verify(turnService, never()).processWorld(anyNonNull())
    }

    @Test
    fun `pause prevents tick from running`() {
        val world = createWorld()
        `when`(sessionStateRepository.findByCommitSha("test-sha")).thenReturn(listOf(world))

        daemon.pause()
        daemon.tick()

        verify(turnService, never()).processWorld(anyNonNull())
        assertEquals(TurnDaemon.DaemonState.PAUSED, daemon.getStatus())
    }

    @Test
    fun `resume allows tick to run again after pause`() {
        val world = createWorld()
        `when`(sessionStateRepository.findByCommitSha("test-sha")).thenReturn(listOf(world))

        daemon.pause()
        daemon.resume()
        daemon.tick()

        verify(turnService).processWorld(world)
    }

    @Test
    fun `tick skips locked world`() {
        val world = createWorld()
        world.config["locked"] = true
        `when`(sessionStateRepository.findByCommitSha("test-sha")).thenReturn(listOf(world))

        daemon.tick()

        verify(turnService, never()).processWorld(world)
    }

    @Test
    fun `tick skips world in pre_open phase`() {
        val world = createWorld()
        world.config["opentime"] = OffsetDateTime.now().plusHours(1).toString()
        `when`(sessionStateRepository.findByCommitSha("test-sha")).thenReturn(listOf(world))

        daemon.tick()

        verify(turnService, never()).processWorld(world)
    }

    @Test
    fun `tick processes unlocked world`() {
        val world = createWorld()
        world.config["locked"] = false
        `when`(sessionStateRepository.findByCommitSha("test-sha")).thenReturn(listOf(world))

        daemon.tick()

        verify(turnService).processWorld(world)
    }

    @Test
    fun `tick broadcasts turn advance after legacy turnService when month changes`() {
        val world = createWorld()
        `when`(sessionStateRepository.findByCommitSha("test-sha")).thenReturn(listOf(world))
        doAnswer {
            world.currentYear = 200
            world.currentMonth = 7
            null
        }.`when`(turnService).processWorld(world)

        daemon.tick()

        verify(gameEventService).broadcastTurnAdvance(1L, 200, 7)
    }

    @Test
    fun `tick does not broadcast when month unchanged`() {
        val world = createWorld()
        `when`(sessionStateRepository.findByCommitSha("test-sha")).thenReturn(listOf(world))

        daemon.tick()

        verify(gameEventService, never()).broadcastTurnAdvance(anyLong(), anyInt(), anyInt())
    }
}
