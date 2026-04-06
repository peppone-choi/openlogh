package com.openlogh.service

import com.openlogh.entity.Event
import com.openlogh.entity.Faction
import com.openlogh.entity.SessionState
import com.openlogh.repository.EventRepository
import com.openlogh.repository.FactionRepository
import com.openlogh.repository.SessionStateRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito.*
import java.util.Optional

class FezzanEndingServiceTest {

    private lateinit var factionRepository: FactionRepository
    private lateinit var eventRepository: EventRepository
    private lateinit var fezzanService: FezzanService
    private lateinit var gameEventService: GameEventService
    private lateinit var sessionStateRepository: SessionStateRepository
    private lateinit var service: FezzanEndingService

    @BeforeEach
    fun setUp() {
        factionRepository = mock(FactionRepository::class.java)
        eventRepository = mock(EventRepository::class.java)
        fezzanService = mock(FezzanService::class.java)
        gameEventService = mock(GameEventService::class.java)
        sessionStateRepository = mock(SessionStateRepository::class.java)
        service = FezzanEndingService(
            factionRepository,
            eventRepository,
            fezzanService,
            gameEventService,
            sessionStateRepository,
        )
    }

    private fun makeWorld(sessionId: Long, alreadyTriggered: Boolean = false): SessionState {
        val world = SessionState()
        world.id = sessionId.toShort()
        if (alreadyTriggered) {
            world.meta["fezzanEndingTriggered"] = true as Any
        }
        return world
    }

    private fun makeFaction(id: Long, sessionId: Long, name: String = "테스트진영", factionType: String = "empire"): Faction {
        val f = Faction()
        f.id = id
        f.sessionId = sessionId
        f.name = name
        f.factionType = factionType
        return f
    }

    /** Test 1: 연체 차관 2건 → triggered=false, triggerFezzanEnding 미호출 */
    @Test
    fun `checkAndTrigger - 연체 2건이면 엔딩 미트리거`() {
        val sessionId = 1L
        val world = makeWorld(sessionId)
        `when`(sessionStateRepository.findById(sessionId.toShort())).thenReturn(Optional.of(world))
        `when`(fezzanService.checkFezzanEnding(sessionId))
            .thenReturn(FezzanEndingResult(triggered = false, dominatedFactionId = null))

        service.checkAndTrigger(sessionId)

        verify(fezzanService).checkFezzanEnding(sessionId)
        verify(factionRepository, never()).findById(anyLong())
        verify(eventRepository, never()).save(any(Event::class.java))
    }

    /** Test 2: 연체 차관 3건 → triggered=true, triggerFezzanEnding 호출됨 */
    @Test
    fun `checkAndTrigger - 연체 3건이면 엔딩 트리거`() {
        val sessionId = 1L
        val factionId = 10L
        val world = makeWorld(sessionId)
        val faction = makeFaction(factionId, sessionId)
        `when`(sessionStateRepository.findById(sessionId.toShort())).thenReturn(Optional.of(world))
        `when`(fezzanService.checkFezzanEnding(sessionId))
            .thenReturn(FezzanEndingResult(triggered = true, dominatedFactionId = factionId))
        `when`(factionRepository.findById(factionId)).thenReturn(Optional.of(faction))
        `when`(eventRepository.save(any(Event::class.java))).thenAnswer { it.arguments[0] }

        service.checkAndTrigger(sessionId)

        verify(eventRepository).save(any(Event::class.java))
    }

    /** Test 3: 연체 차관 3건 → fezzanEndingTriggered 플래그가 SessionState.meta에 설정됨 */
    @Test
    fun `checkAndTrigger - 트리거 후 meta에 fezzanEndingTriggered=true 설정`() {
        val sessionId = 1L
        val factionId = 10L
        val world = makeWorld(sessionId)
        val faction = makeFaction(factionId, sessionId)
        `when`(sessionStateRepository.findById(sessionId.toShort())).thenReturn(Optional.of(world))
        `when`(fezzanService.checkFezzanEnding(sessionId))
            .thenReturn(FezzanEndingResult(triggered = true, dominatedFactionId = factionId))
        `when`(factionRepository.findById(factionId)).thenReturn(Optional.of(faction))
        `when`(eventRepository.save(any(Event::class.java))).thenAnswer { it.arguments[0] }

        service.checkAndTrigger(sessionId)

        assertEquals(true, world.meta["fezzanEndingTriggered"])
        verify(sessionStateRepository).save(world)
    }

    /** Test 4: 이미 fezzanEndingTriggered=true이면 재트리거 안됨 */
    @Test
    fun `checkAndTrigger - 이미 트리거된 경우 재트리거 방지`() {
        val sessionId = 1L
        val world = makeWorld(sessionId, alreadyTriggered = true)
        `when`(sessionStateRepository.findById(sessionId.toShort())).thenReturn(Optional.of(world))

        service.checkAndTrigger(sessionId)

        verify(fezzanService, never()).checkFezzanEnding(anyLong())
        verify(eventRepository, never()).save(any(Event::class.java))
    }

    /** Test 5: factionType="fezzan"인 진영은 엔딩 검사에서 제외됨 */
    @Test
    fun `checkFezzanEnding - fezzan 진영은 엔딩 검사 제외`() {
        val sessionId = 1L
        val world = makeWorld(sessionId)
        `when`(sessionStateRepository.findById(sessionId.toShort())).thenReturn(Optional.of(world))
        // FezzanService.checkFezzanEnding already skips fezzan-type factions internally.
        // We test that if it returns triggered=false (as it would for fezzan-only session), nothing triggers.
        `when`(fezzanService.checkFezzanEnding(sessionId))
            .thenReturn(FezzanEndingResult(triggered = false, dominatedFactionId = null))

        service.checkAndTrigger(sessionId)

        verify(eventRepository, never()).save(any(Event::class.java))
    }
}
