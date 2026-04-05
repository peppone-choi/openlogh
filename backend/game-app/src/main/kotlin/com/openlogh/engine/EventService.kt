package com.openlogh.engine

import com.openlogh.entity.SessionState
import com.openlogh.engine.event.EventActionContext
import com.openlogh.engine.event.EventActionRegistry
import com.openlogh.engine.turn.cqrs.persist.JpaWorldPortFactory
import com.openlogh.repository.EventRepository
import com.openlogh.repository.FactionRepository
import com.openlogh.service.ScenarioService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class EventService @Autowired constructor(
    private val eventRepository: EventRepository,
    private val worldPortFactory: JpaWorldPortFactory,
    private val scenarioService: ScenarioService,
    private val eventActionRegistry: EventActionRegistry,
) {
    constructor(
        eventRepository: EventRepository,
        factionRepository: FactionRepository,
        scenarioService: ScenarioService,
        eventActionRegistry: EventActionRegistry,
    ) : this(
        eventRepository = eventRepository,
        worldPortFactory = JpaWorldPortFactory(factionRepository = factionRepository),
        scenarioService = scenarioService,
        eventActionRegistry = eventActionRegistry,
    )

    private val log = LoggerFactory.getLogger(EventService::class.java)

    @Transactional
    fun dispatchEvents(world: SessionState, targetCode: String) {
        val events = eventRepository.findByWorldIdAndTargetCodeOrderByPriorityDescIdAsc(
            world.id.toLong(), targetCode
        )

        for (event in events) {
            if (evaluateCondition(event.condition, world)) {
                log.info("Event #{} triggered (target={}, priority={})", event.id, targetCode, event.priority)
                executeAction(event.action, world, event.id)
            }
        }
    }

    private fun evaluateCondition(condition: Map<String, Any>, world: SessionState): Boolean {
        return when (val type = condition["type"] as? String) {
            "always_true" -> true
            "always_false" -> false

            "date" -> {
                val year = (condition["year"] as? Number)?.toShort() ?: return false
                val month = (condition["month"] as? Number)?.toShort() ?: return false
                world.currentYear == year && world.currentMonth == month
            }

            "date_after" -> {
                val year = (condition["year"] as? Number)?.toShort() ?: return false
                val month = (condition["month"] as? Number)?.toShort() ?: return false
                world.currentYear > year || (world.currentYear == year && world.currentMonth >= month)
            }

            // 월만 비교 (연도 무관) — legacy Date condition with null year
            "date_month" -> {
                val month = (condition["month"] as? Number)?.toShort() ?: return false
                world.currentMonth == month
            }

            // 시나리오 시작년도 기준 상대 날짜 조건
            "date_relative" -> {
                val yearOffset = (condition["yearOffset"] as? Number)?.toInt() ?: return false
                val month = (condition["month"] as? Number)?.toShort() ?: return false
                val startYear = try {
                    scenarioService.getScenario(world.scenarioCode).startYear
                } catch (e: Exception) {
                    log.warn("Failed to resolve startYear for scenario {}: {}", world.scenarioCode, e.message)
                    return false
                }
                val targetYear = (startYear + yearOffset).coerceIn(0, 32767).toShort()
                world.currentYear == targetYear && world.currentMonth == month
            }

            // 반복 조건: startYear/startMonth부터 매 N개월마다 트리거
            "interval" -> {
                val months = (condition["months"] as? Number)?.toInt() ?: return false
                val startYear = (condition["startYear"] as? Number)?.toInt() ?: return false
                val startMonth = (condition["startMonth"] as? Number)?.toInt() ?: 1
                if (months <= 0) return false
                val startTotal = startYear * 12 + (startMonth - 1)
                val currentTotal = world.currentYear.toInt() * 12 + (world.currentMonth.toInt() - 1)
                val elapsed = currentTotal - startTotal
                elapsed >= 0 && elapsed % months == 0
            }

            "remain_nation" -> {
                val count = (condition["count"] as? Number)?.toInt() ?: return false
                val op = (condition["operator"] as? String) ?: "<="
                val nationCount = worldPortFactory.create(world.id.toLong()).allFactions().size
                when (op) {
                    "==" -> nationCount == count
                    "<=" -> nationCount <= count
                    ">=" -> nationCount >= count
                    "<" -> nationCount < count
                    ">" -> nationCount > count
                    "!=" -> nationCount != count
                    else -> nationCount <= count
                }
            }

            "and" -> {
                val conditions = readStringAnyMapList(condition["conditions"]) ?: return false
                conditions.all { evaluateCondition(it, world) }
            }

            "or" -> {
                val conditions = readStringAnyMapList(condition["conditions"]) ?: return false
                conditions.any { evaluateCondition(it, world) }
            }

            "not" -> {
                val sub = readStringAnyMap(condition["condition"]) ?: return false
                !evaluateCondition(sub, world)
            }

            "xor" -> {
                val conditions = readStringAnyMapList(condition["conditions"]) ?: return false
                conditions.count { evaluateCondition(it, world) } == 1
            }

            else -> {
                log.warn("Unknown condition type: {}", type)
                false
            }
        }
    }

    private fun executeAction(action: Map<String, Any>, world: SessionState, currentEventId: Long = 0) {
        val type = action["type"] as? String ?: run {
            log.warn("[World {}] Event action missing 'type' field", world.id)
            return
        }
        val context = EventActionContext(world = world, params = action, currentEventId = currentEventId)
        eventActionRegistry.resolve(type).execute(context)
    }

    private fun readStringAnyMap(raw: Any?): Map<String, Any>? {
        if (raw !is Map<*, *>) return null
        val result = mutableMapOf<String, Any>()
        raw.forEach { (key, value) ->
            if (key is String && value != null) {
                result[key] = value
            }
        }
        return result
    }

    private fun readStringAnyMapList(raw: Any?): List<Map<String, Any>>? {
        if (raw !is Iterable<*>) return null
        val result = mutableListOf<Map<String, Any>>()
        raw.forEach { entry ->
            val parsed = readStringAnyMap(entry) ?: return@forEach
            result.add(parsed)
        }
        return result
    }
}
