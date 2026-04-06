package com.openlogh.engine

import com.openlogh.engine.event.EventActionRegistry
import com.openlogh.engine.event.actions.control.CompoundAction
import com.openlogh.engine.event.actions.control.DeleteEventAction
import com.openlogh.engine.event.actions.control.DeleteSelfAction
import com.openlogh.engine.event.actions.economy.ProcessIncomeAction
import com.openlogh.engine.event.actions.economy.ProcessSemiAnnualAction
import com.openlogh.engine.event.actions.economy.RandomizeCityTradeRateAction
import com.openlogh.engine.event.actions.economy.UpdateCitySupplyAction
import com.openlogh.engine.event.actions.economy.UpdateNationLevelAction
import com.openlogh.engine.event.actions.misc.LogAction
import com.openlogh.engine.event.actions.misc.NoticeAction
import com.openlogh.engine.event.actions.npc.ProvideNpcTroopLeaderAction
import com.openlogh.engine.event.actions.npc.RaiseInvaderAction
import com.openlogh.engine.event.actions.npc.RaiseNpcNationAction
import com.openlogh.entity.Event
import com.openlogh.entity.Message
import com.openlogh.entity.SessionState
import com.openlogh.repository.EventRepository
import com.openlogh.repository.MessageRepository
import com.openlogh.repository.FactionRepository
import com.openlogh.service.HistoryService
import com.openlogh.service.ScenarioService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*

class EventServiceTest {

    private lateinit var service: EventService
    private lateinit var eventRepository: EventRepository
    private lateinit var factionRepository: FactionRepository
    private lateinit var messageRepository: MessageRepository
    private lateinit var historyService: HistoryService
    private lateinit var economyService: EconomyService
    private lateinit var npcSpawnService: NpcSpawnService

    /** Mockito `any()` returns null which breaks Kotlin non-null params. This helper casts it. */
    @Suppress("UNCHECKED_CAST")
    private fun <T> anyNonNull(): T = org.mockito.Mockito.any<T>() as T

    @BeforeEach
    fun setUp() {
        eventRepository = mock(EventRepository::class.java)
        factionRepository = mock(FactionRepository::class.java)
        messageRepository = mock(MessageRepository::class.java)
        historyService = mock(HistoryService::class.java)
        economyService = mock(EconomyService::class.java)
        npcSpawnService = mock(NpcSpawnService::class.java)

        val scenarioService = mock(ScenarioService::class.java)

        // Build concrete action instances backed by mocks, then wrap in registry.
        // CompoundAction has a circular dependency on registry — build registry first with
        // a placeholder, then construct CompoundAction with the real registry.
        val nonCompoundActions = listOf(
            LogAction(historyService),
            NoticeAction(messageRepository),
            DeleteEventAction(eventRepository),
            DeleteSelfAction(eventRepository),
            ProcessIncomeAction(economyService),
            ProcessSemiAnnualAction(economyService),
            UpdateCitySupplyAction(economyService),
            UpdateNationLevelAction(economyService),
            RandomizeCityTradeRateAction(economyService),
            RaiseInvaderAction(npcSpawnService),
            RaiseNpcNationAction(npcSpawnService),
            ProvideNpcTroopLeaderAction(npcSpawnService),
        )
        // EventActionRegistry is constructed with all actions; CompoundAction gets the registry injected.
        // Since CompoundAction uses @Lazy in Spring, here we construct it with the full registry.
        val registry = EventActionRegistry(nonCompoundActions + listOf(
            // Temporary placeholder for compound — will be replaced below
            object : com.openlogh.engine.event.EventAction {
                override val actionType = "compound"
                override fun execute(context: com.openlogh.engine.event.EventActionContext) =
                    com.openlogh.engine.event.EventActionResult.Success
            }
        ))
        // Build the real compound action with the registry, then rebuild registry including it.
        val compoundAction = CompoundAction(registry)
        val fullRegistry = EventActionRegistry(nonCompoundActions + listOf(compoundAction))

        service = EventService(eventRepository, factionRepository, scenarioService, fullRegistry)

        // Default: messageRepository.save returns the argument
        `when`(messageRepository.save(anyNonNull<Message>())).thenAnswer { it.arguments[0] }
    }

    private fun createWorld(year: Short = 200, month: Short = 3): SessionState {
        return SessionState(
            id = 1,
            scenarioCode = "test",
            currentYear = year,
            currentMonth = month,
            tickSeconds = 300,
        )
    }

    private fun createEvent(
        id: Long,
        targetCode: String,
        condition: MutableMap<String, Any>,
        action: MutableMap<String, Any>,
        priority: Short = 100,
    ): Event {
        return Event(
            id = id,
            sessionId = 1,
            targetCode = targetCode,
            condition = condition,
            action = action,
            priority = priority,
        )
    }

    // ========== dispatchEvents: condition evaluation ==========

    @Test
    fun `dispatchEvents processes event when condition is always_true`() {
        val world = createWorld(year = 200, month = 3)
        val event = createEvent(
            id = 1,
            targetCode = "turn_start",
            condition = mutableMapOf("type" to "always_true"),
            action = mutableMapOf("type" to "log", "message" to "Test event"),
        )

        `when`(eventRepository.findBySessionIdAndTargetCodeOrderByPriorityDescIdAsc(1L, "turn_start"))
            .thenReturn(listOf(event))

        service.dispatchEvents(world, "turn_start")

        verify(historyService).logWorldHistory(anyLong(), anyString(), anyInt(), anyInt(), eq(false))
    }

    @Test
    fun `dispatchEvents skips event when condition is always_false`() {
        val world = createWorld(year = 200, month = 3)
        val event = createEvent(
            id = 1,
            targetCode = "turn_start",
            condition = mutableMapOf("type" to "always_false"),
            action = mutableMapOf("type" to "log", "message" to "Should not fire"),
        )

        `when`(eventRepository.findBySessionIdAndTargetCodeOrderByPriorityDescIdAsc(1L, "turn_start"))
            .thenReturn(listOf(event))

        service.dispatchEvents(world, "turn_start")

        verify(historyService, never()).logWorldHistory(anyLong(), anyString(), anyInt(), anyInt(), anyBoolean())
    }

    @Test
    fun `dispatchEvents matches date condition when year and month match`() {
        val world = createWorld(year = 200, month = 3)
        val event = createEvent(
            id = 1,
            targetCode = "turn_start",
            condition = mutableMapOf("type" to "date", "year" to 200, "month" to 3),
            action = mutableMapOf("type" to "log", "message" to "Date matched"),
        )

        `when`(eventRepository.findBySessionIdAndTargetCodeOrderByPriorityDescIdAsc(1L, "turn_start"))
            .thenReturn(listOf(event))

        service.dispatchEvents(world, "turn_start")

        verify(historyService).logWorldHistory(anyLong(), anyString(), anyInt(), anyInt(), eq(false))
    }

    @Test
    fun `dispatchEvents does not match date condition when year differs`() {
        val world = createWorld(year = 201, month = 3)
        val event = createEvent(
            id = 1,
            targetCode = "turn_start",
            condition = mutableMapOf("type" to "date", "year" to 200, "month" to 3),
            action = mutableMapOf("type" to "log", "message" to "Should not fire"),
        )

        `when`(eventRepository.findBySessionIdAndTargetCodeOrderByPriorityDescIdAsc(1L, "turn_start"))
            .thenReturn(listOf(event))

        service.dispatchEvents(world, "turn_start")

        verify(historyService, never()).logWorldHistory(anyLong(), anyString(), anyInt(), anyInt(), anyBoolean())
    }

    @Test
    fun `dispatchEvents matches date_after condition when date is after threshold`() {
        val world = createWorld(year = 201, month = 5)
        val event = createEvent(
            id = 1,
            targetCode = "turn_start",
            condition = mutableMapOf("type" to "date_after", "year" to 200, "month" to 12),
            action = mutableMapOf("type" to "log", "message" to "Date after matched"),
        )

        `when`(eventRepository.findBySessionIdAndTargetCodeOrderByPriorityDescIdAsc(1L, "turn_start"))
            .thenReturn(listOf(event))

        service.dispatchEvents(world, "turn_start")

        verify(historyService).logWorldHistory(anyLong(), anyString(), anyInt(), anyInt(), eq(false))
    }

    // ========== dispatchEvents: action execution ==========

    @Test
    fun `dispatchEvents executes log action and saves message`() {
        val world = createWorld(year = 200, month = 3)
        val event = createEvent(
            id = 1,
            targetCode = "turn_start",
            condition = mutableMapOf("type" to "always_true"),
            action = mutableMapOf("type" to "log", "message" to "History log test"),
        )

        `when`(eventRepository.findBySessionIdAndTargetCodeOrderByPriorityDescIdAsc(1L, "turn_start"))
            .thenReturn(listOf(event))

        service.dispatchEvents(world, "turn_start")

        verify(historyService).logWorldHistory(anyLong(), anyString(), anyInt(), anyInt(), eq(false))
    }

    @Test
    fun `dispatchEvents executes notice action and saves notice message`() {
        val world = createWorld(year = 200, month = 3)
        val event = createEvent(
            id = 1,
            targetCode = "turn_start",
            condition = mutableMapOf("type" to "always_true"),
            action = mutableMapOf("type" to "notice", "message" to "Notice test"),
        )

        `when`(eventRepository.findBySessionIdAndTargetCodeOrderByPriorityDescIdAsc(1L, "turn_start"))
            .thenReturn(listOf(event))

        service.dispatchEvents(world, "turn_start")

        val captor = ArgumentCaptor.forClass(Message::class.java)
        verify(messageRepository).save(captor.capture())
        assertEquals("notice", captor.value.mailboxCode)
        assertEquals("notice", captor.value.messageType)
    }

    @Test
    fun `dispatchEvents executes delete_event action`() {
        val world = createWorld(year = 200, month = 3)
        val event = createEvent(
            id = 1,
            targetCode = "turn_start",
            condition = mutableMapOf("type" to "always_true"),
            action = mutableMapOf("type" to "delete_event", "eventId" to 99),
        )

        `when`(eventRepository.findBySessionIdAndTargetCodeOrderByPriorityDescIdAsc(1L, "turn_start"))
            .thenReturn(listOf(event))

        service.dispatchEvents(world, "turn_start")

        verify(eventRepository).deleteById(99L)
    }

    // ========== dispatchEvents: multiple events and priority ==========

    @Test
    fun `dispatchEvents processes multiple events in priority order`() {
        val world = createWorld(year = 200, month = 3)
        val event1 = createEvent(
            id = 1,
            targetCode = "turn_start",
            condition = mutableMapOf("type" to "always_true"),
            action = mutableMapOf("type" to "log", "message" to "Event 1"),
            priority = 200.toShort(),
        )
        val event2 = createEvent(
            id = 2,
            targetCode = "turn_start",
            condition = mutableMapOf("type" to "always_true"),
            action = mutableMapOf("type" to "log", "message" to "Event 2"),
            priority = 100,
        )

        `when`(eventRepository.findBySessionIdAndTargetCodeOrderByPriorityDescIdAsc(1L, "turn_start"))
            .thenReturn(listOf(event1, event2))

        service.dispatchEvents(world, "turn_start")

        verify(historyService, times(2)).logWorldHistory(anyLong(), anyString(), anyInt(), anyInt(), eq(false))
    }

    // ========== dispatchEvents: empty events ==========

    @Test
    fun `dispatchEvents handles no events gracefully`() {
        val world = createWorld()

        `when`(eventRepository.findBySessionIdAndTargetCodeOrderByPriorityDescIdAsc(1L, "turn_start"))
            .thenReturn(emptyList())

        assertDoesNotThrow {
            service.dispatchEvents(world, "turn_start")
        }

        verify(messageRepository, never()).save(any())
    }

    // ========== dispatchEvents: remain_nation condition ==========

    @Test
    fun `dispatchEvents matches remain_nation when nation count is below threshold`() {
        val world = createWorld(year = 200, month = 3)
        val event = createEvent(
            id = 1,
            targetCode = "turn_start",
            condition = mutableMapOf("type" to "remain_nation", "count" to 5),
            action = mutableMapOf("type" to "log", "message" to "Few nations remain"),
        )

        `when`(eventRepository.findBySessionIdAndTargetCodeOrderByPriorityDescIdAsc(1L, "turn_start"))
            .thenReturn(listOf(event))
        `when`(factionRepository.findBySessionId(1L)).thenReturn(emptyList())

        service.dispatchEvents(world, "turn_start")

        verify(historyService).logWorldHistory(anyLong(), anyString(), anyInt(), anyInt(), eq(false))
    }

    // ========== New action types: economy delegations ==========

    @Test
    fun `dispatchEvents executes process_income action`() {
        val world = createWorld()
        val event = createEvent(
            id = 1,
            targetCode = "MONTH",
            condition = mutableMapOf("type" to "always_true"),
            action = mutableMapOf("type" to "process_income"),
        )

        `when`(eventRepository.findBySessionIdAndTargetCodeOrderByPriorityDescIdAsc(1L, "MONTH"))
            .thenReturn(listOf(event))

        service.dispatchEvents(world, "MONTH")

        verify(economyService).processIncomeEvent(world)
    }

    @Test
    fun `dispatchEvents executes process_semi_annual action`() {
        val world = createWorld()
        val event = createEvent(
            id = 1,
            targetCode = "MONTH",
            condition = mutableMapOf("type" to "always_true"),
            action = mutableMapOf("type" to "process_semi_annual"),
        )

        `when`(eventRepository.findBySessionIdAndTargetCodeOrderByPriorityDescIdAsc(1L, "MONTH"))
            .thenReturn(listOf(event))

        service.dispatchEvents(world, "MONTH")

        verify(economyService).processSemiAnnualEvent(world)
    }

    @Test
    fun `dispatchEvents executes update_city_supply action`() {
        val world = createWorld()
        val event = createEvent(
            id = 1,
            targetCode = "MONTH",
            condition = mutableMapOf("type" to "always_true"),
            action = mutableMapOf("type" to "update_city_supply"),
        )

        `when`(eventRepository.findBySessionIdAndTargetCodeOrderByPriorityDescIdAsc(1L, "MONTH"))
            .thenReturn(listOf(event))

        service.dispatchEvents(world, "MONTH")

        verify(economyService).updateCitySupplyState(world)
    }

    @Test
    fun `dispatchEvents executes update_nation_level action`() {
        val world = createWorld()
        val event = createEvent(
            id = 1,
            targetCode = "MONTH",
            condition = mutableMapOf("type" to "always_true"),
            action = mutableMapOf("type" to "update_nation_level"),
        )

        `when`(eventRepository.findBySessionIdAndTargetCodeOrderByPriorityDescIdAsc(1L, "MONTH"))
            .thenReturn(listOf(event))

        service.dispatchEvents(world, "MONTH")

        verify(economyService).updateNationLevelEvent(world)
    }

    @Test
    fun `dispatchEvents executes randomize_trade_rate action`() {
        val world = createWorld()
        val event = createEvent(
            id = 1,
            targetCode = "MONTH",
            condition = mutableMapOf("type" to "always_true"),
            action = mutableMapOf("type" to "randomize_trade_rate"),
        )

        `when`(eventRepository.findBySessionIdAndTargetCodeOrderByPriorityDescIdAsc(1L, "MONTH"))
            .thenReturn(listOf(event))

        service.dispatchEvents(world, "MONTH")

        verify(economyService).randomizeCityTradeRate(world)
    }

    // ========== delete_self action ==========

    @Test
    fun `dispatchEvents executes delete_self action`() {
        val world = createWorld()
        val event = createEvent(
            id = 42,
            targetCode = "MONTH",
            condition = mutableMapOf("type" to "always_true"),
            action = mutableMapOf("type" to "delete_self"),
        )

        `when`(eventRepository.findBySessionIdAndTargetCodeOrderByPriorityDescIdAsc(1L, "MONTH"))
            .thenReturn(listOf(event))

        service.dispatchEvents(world, "MONTH")

        verify(eventRepository).deleteById(42L)
    }

    // ========== compound action ==========

    @Test
    fun `dispatchEvents executes compound action with multiple sub-actions`() {
        val world = createWorld()
        val subActions: List<Map<String, Any>> = listOf(
            mapOf("type" to "process_income"),
            mapOf("type" to "update_nation_level"),
        )
        val event = createEvent(
            id = 1,
            targetCode = "MONTH",
            condition = mutableMapOf("type" to "always_true"),
            action = mutableMapOf("type" to "compound", "actions" to subActions),
        )

        `when`(eventRepository.findBySessionIdAndTargetCodeOrderByPriorityDescIdAsc(1L, "MONTH"))
            .thenReturn(listOf(event))

        service.dispatchEvents(world, "MONTH")

        verify(economyService).processIncomeEvent(world)
        verify(economyService).updateNationLevelEvent(world)
    }

    // ========== stub actions don't throw ==========

    @Test
    fun `dispatchEvents handles raise_invader stub gracefully`() {
        val world = createWorld()
        val event = createEvent(
            id = 1,
            targetCode = "MONTH",
            condition = mutableMapOf("type" to "always_true"),
            action = mutableMapOf("type" to "raise_invader"),
        )

        `when`(eventRepository.findBySessionIdAndTargetCodeOrderByPriorityDescIdAsc(1L, "MONTH"))
            .thenReturn(listOf(event))

        assertDoesNotThrow { service.dispatchEvents(world, "MONTH") }
    }

    @Test
    fun `dispatchEvents handles raise_npc_nation stub gracefully`() {
        val world = createWorld()
        val event = createEvent(
            id = 1,
            targetCode = "MONTH",
            condition = mutableMapOf("type" to "always_true"),
            action = mutableMapOf("type" to "raise_npc_nation"),
        )

        `when`(eventRepository.findBySessionIdAndTargetCodeOrderByPriorityDescIdAsc(1L, "MONTH"))
            .thenReturn(listOf(event))

        assertDoesNotThrow { service.dispatchEvents(world, "MONTH") }
    }

    @Test
    fun `dispatchEvents handles provide_npc_troop_leader stub gracefully`() {
        val world = createWorld()
        val event = createEvent(
            id = 1,
            targetCode = "MONTH",
            condition = mutableMapOf("type" to "always_true"),
            action = mutableMapOf("type" to "provide_npc_troop_leader"),
        )

        `when`(eventRepository.findBySessionIdAndTargetCodeOrderByPriorityDescIdAsc(1L, "MONTH"))
            .thenReturn(listOf(event))

        assertDoesNotThrow { service.dispatchEvents(world, "MONTH") }
    }
}
