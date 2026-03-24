package com.openlogh.engine

import com.openlogh.entity.SessionState
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Registry-based event action dispatch system.
 *
 * Replaces hardcoded when-blocks in EventService with a pluggable registry.
 * Each action handler is registered by type code and receives the world state
 * plus action parameters.
 *
 * gin7: 이벤트 시스템은 조건 평가 후 액션을 실행하는 구조.
 * 이 레지스트리는 새로운 액션 타입을 코드 변경 없이 등록 가능하게 한다.
 */
@Service
class EventActionRegistry(
    private val economyService: EconomyService,
    private val npcSpawnService: NpcSpawnService,
    private val eventActionService: EventActionService,
) {
    companion object {
        private val log = LoggerFactory.getLogger(EventActionRegistry::class.java)
    }

    /**
     * Action handler: receives world, event ID, and action params.
     */
    fun interface ActionHandler {
        fun execute(world: SessionState, eventId: Long, params: Map<String, Any>)
    }

    private val handlers: MutableMap<String, ActionHandler> = mutableMapOf()

    init {
        // Register built-in action handlers
        registerBuiltinHandlers()
    }

    /**
     * Register a custom action handler.
     */
    fun register(actionType: String, handler: ActionHandler) {
        handlers[actionType] = handler
        log.debug("Registered event action handler: {}", actionType)
    }

    /**
     * Execute an action by type code.
     * @return true if handler was found and executed, false if unknown type
     */
    fun execute(world: SessionState, eventId: Long, action: Map<String, Any>): Boolean {
        val type = action["type"] as? String ?: return false
        val handler = handlers[type]
        if (handler == null) {
            log.warn("Unknown event action type: {}", type)
            return false
        }
        try {
            handler.execute(world, eventId, action)
            return true
        } catch (e: Exception) {
            log.error("Event action '{}' failed for world {}: {}", type, world.id, e.message)
            return false
        }
    }

    /**
     * List all registered action types.
     */
    fun getRegisteredTypes(): Set<String> = handlers.keys.toSet()

    @Suppress("UNCHECKED_CAST")
    private fun registerBuiltinHandlers() {
        // Economy actions
        register("process_income") { world, _, _ ->
            economyService.processIncomeEvent(world)
        }
        register("process_semi_annual") { world, _, _ ->
            economyService.processSemiAnnualEvent(world)
        }
        register("update_city_supply") { world, _, _ ->
            economyService.updateCitySupplyState(world)
        }
        register("update_nation_level") { world, _, _ ->
            economyService.updateNationLevelEvent(world)
        }
        register("randomize_trade_rate") { world, _, _ ->
            economyService.randomizeCityTradeRate(world)
        }

        // EventActionService delegated actions
        register("add_global_betray") { world, _, params ->
            val cnt = (params["count"] as? Number)?.toInt() ?: 1
            val ifMax = (params["ifMax"] as? Number)?.toInt() ?: 0
            eventActionService.addGlobalBetray(world, cnt, ifMax)
        }
        register("change_city") { world, _, params ->
            val target = params["target"] as? String
            val changes = params["changes"] as? Map<String, Any> ?: emptyMap()
            eventActionService.changeCity(world, target, changes)
        }
        register("new_year") { world, _, _ ->
            eventActionService.newYear(world)
        }
        register("reset_officer_lock") { world, _, _ ->
            eventActionService.resetOfficerLock(world)
        }
        register("process_war_income") { world, _, _ ->
            eventActionService.processWarIncome(world)
        }
        register("lost_unique_item") { world, _, params ->
            val prob = (params["probability"] as? Number)?.toDouble() ?: 0.1
            eventActionService.lostUniqueItem(world, prob)
        }
        register("merge_inherit_rank") { world, _, _ ->
            eventActionService.mergeInheritPointRank(world)
        }
        register("assign_general_speciality") { world, _, _ ->
            eventActionService.assignGeneralSpeciality(world)
        }
        register("create_many_npc") { world, _, params ->
            val count = (params["npcCount"] as? Number)?.toInt() ?: 0
            val fill = (params["fillCnt"] as? Number)?.toInt() ?: 0
            eventActionService.createManyNPC(world, count, fill)
        }
        register("reg_npc") { world, _, params ->
            eventActionService.regNPC(world, params)
        }
        register("reg_neutral_npc") { world, _, params ->
            eventActionService.regNeutralNPC(world, params)
        }

        // NPC spawn
        register("raise_invader") { world, _, params ->
            npcSpawnService.raiseInvader(world, params)
        }
        register("raise_npc_nation") { world, _, params ->
            npcSpawnService.raiseNpcNation(world, params)
        }
        register("provide_npc_troop_leader") { world, _, params ->
            npcSpawnService.provideNpcTroopLeader(world, params)
        }

        // Compound action
        register("compound") { world, eventId, params ->
            val subActions = params["actions"] as? List<Map<String, Any>> ?: return@register
            for (sub in subActions) {
                execute(world, eventId, sub)
            }
        }

        // Simple actions
        register("delete_self") { _, eventId, _ ->
            // Caller handles deletion via eventRepository
        }
        register("log") { _, _, _ ->
            // Caller handles log via historyService
        }
        register("notice") { _, _, _ ->
            // Caller handles notice via messageRepository
        }
    }
}
