package com.openlogh.engine

import com.openlogh.entity.Message
import com.openlogh.entity.SessionState
import com.openlogh.repository.EventRepository
import com.openlogh.repository.FactionRepository
import com.openlogh.repository.MessageRepository
import com.openlogh.service.HistoryService
import com.openlogh.service.ScenarioService
import org.springframework.stereotype.Service

@Service
class EventService(
    private val eventRepository: EventRepository,
    private val factionRepository: FactionRepository,
    private val messageRepository: MessageRepository,
    private val historyService: HistoryService,
    private val economyService: EconomyService,
    private val npcSpawnService: NpcSpawnService,
    private val scenarioService: ScenarioService,
    private val eventActionService: EventActionService,
) {
    fun dispatchEvents(world: SessionState, phase: String) {
        val sessionId = world.id.toLong()
        val events = eventRepository.findBySessionIdAndTargetCodeOrderByPriorityDescIdAsc(sessionId, phase)
        for (event in events) {
            if (evaluateCondition(world, event.condition)) {
                executeAction(world, event.id, event.action)
            }
        }
    }

    private fun evaluateCondition(world: SessionState, condition: MutableMap<String, Any>): Boolean {
        return when (condition["type"] as? String) {
            "always_true" -> true
            "always_false" -> false
            "date" -> {
                val year = (condition["year"] as Number).toInt()
                val month = (condition["month"] as Number).toInt()
                world.currentYear.toInt() == year && world.currentMonth.toInt() == month
            }
            "date_after" -> {
                val year = (condition["year"] as Number).toInt()
                val month = (condition["month"] as Number).toInt()
                val worldYM = world.currentYear.toInt() * 12 + world.currentMonth.toInt()
                val condYM = year * 12 + month
                worldYM > condYM
            }
            "remain_nation" -> {
                val count = (condition["count"] as Number).toInt()
                val factions = factionRepository.findBySessionId(world.id.toLong())
                factions.size <= count
            }
            else -> false
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun executeAction(world: SessionState, eventId: Long, action: Map<String, Any>) {
        when (action["type"] as? String) {
            "log" -> {
                val message = action["message"] as? String ?: ""
                historyService.logWorldHistory(world.id.toLong(), message, world.currentYear.toInt(), world.currentMonth.toInt())
            }
            "notice" -> {
                val message = action["message"] as? String ?: ""
                messageRepository.save(Message(
                    sessionId = world.id.toLong(),
                    mailboxCode = "notice",
                    messageType = "notice",
                    payload = mutableMapOf("message" to message),
                ))
            }
            "delete_event" -> {
                val targetEventId = (action["eventId"] as Number).toLong()
                eventRepository.deleteById(targetEventId)
            }
            "delete_self" -> eventRepository.deleteById(eventId)
            "process_income" -> economyService.processIncomeEvent(world)
            "process_semi_annual" -> economyService.processSemiAnnualEvent(world)
            "update_city_supply" -> economyService.updateCitySupplyState(world)
            "update_nation_level" -> economyService.updateNationLevelEvent(world)
            "randomize_trade_rate" -> economyService.randomizeCityTradeRate(world)
            "compound" -> {
                val subActions = action["actions"] as? List<Map<String, Any>> ?: return
                for (sub in subActions) {
                    executeAction(world, eventId, sub)
                }
            }
            "raise_invader" -> { /* stub - future implementation */ }
            "raise_npc_nation" -> { /* stub - future implementation */ }
            "provide_npc_troop_leader" -> { /* stub - future implementation */ }
        }
    }
}
