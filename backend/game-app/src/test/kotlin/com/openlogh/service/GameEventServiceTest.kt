package com.openlogh.service

import com.openlogh.repository.WorldHistoryRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.context.ApplicationEventPublisher
import org.springframework.messaging.simp.SimpMessagingTemplate
import com.openlogh.entity.WorldHistory

class GameEventServiceTest {

    private lateinit var messagingTemplate: SimpMessagingTemplate
    private lateinit var worldHistoryRepository: WorldHistoryRepository
    private lateinit var applicationEventPublisher: ApplicationEventPublisher
    private lateinit var service: GameEventService

    @BeforeEach
    fun setUp() {
        messagingTemplate = mock(SimpMessagingTemplate::class.java)
        worldHistoryRepository = mock(WorldHistoryRepository::class.java)
        applicationEventPublisher = mock(ApplicationEventPublisher::class.java)

        service = GameEventService(
            messagingTemplate = messagingTemplate,
            worldHistoryRepository = worldHistoryRepository,
            applicationEventPublisher = applicationEventPublisher,
        )
    }

    @Test
    fun `CommandEvent toPayload includes officerId and commandEventType`() {
        val event = CommandEvent(
            source = service,
            sessionId = 1L,
            year = 200,
            month = 6,
            officerId = 42L,
            commandEventType = "reserved",
        )

        val payload = event.toPayload()

        assertEquals(42L, payload["officerId"])
        assertEquals("reserved", payload["commandEventType"])
        assertEquals(1L, payload["sessionId"])
        assertEquals("command", payload["eventType"])
    }

    @Test
    fun `fireCommand publishes a CommandEvent via ApplicationEventPublisher`() {
        val captor = ArgumentCaptor.forClass(CommandEvent::class.java)

        service.fireCommand(
            worldId = 1L,
            year = 200,
            month = 6,
            generalId = 42L,
            commandEventType = "reserved",
        )

        verify(applicationEventPublisher).publishEvent(captor.capture())
        val published = captor.value
        assertEquals(42L, published.officerId)
        assertEquals("reserved", published.commandEventType)
        assertEquals(1L, published.sessionId)
    }

    @Test
    fun `onGameEvent with CommandEvent calls broadcastCommand`() {
        val history = WorldHistory(
            sessionId = 1L,
            year = 200,
            month = 6,
            eventType = "command",
            payload = mutableMapOf(),
        )
        `when`(worldHistoryRepository.save(org.mockito.ArgumentMatchers.any(WorldHistory::class.java)))
            .thenReturn(history)

        val event = CommandEvent(
            source = service,
            sessionId = 1L,
            year = 200,
            month = 6,
            officerId = 42L,
            commandEventType = "consumed",
        )

        service.onGameEvent(event)

        // broadcastCommand sends to /topic/world/{sessionId}/command and /topic/general/{officerId}
        verify(messagingTemplate).convertAndSend(
            org.mockito.ArgumentMatchers.eq("/topic/world/1/command"),
            org.mockito.ArgumentMatchers.any<Any>(),
        )
        verify(messagingTemplate).convertAndSend(
            org.mockito.ArgumentMatchers.eq("/topic/general/42"),
            org.mockito.ArgumentMatchers.any<Any>(),
        )
    }
}
