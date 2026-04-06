package com.openlogh.service

import com.openlogh.entity.Event
import com.openlogh.repository.EventRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Manages LOGH scenario-specific events: coups, civil wars, special battles, and timeline events.
 *
 * Event types:
 * - ScenarioMessage: Display a narrative message at a specific date
 * - EmperorDeath: Trigger emperor death and succession crisis
 * - LipstadtRebellion: Trigger noble rebellion (S6)
 * - GreenHillCoup: Alliance coup event triggered by political machining
 * - FezzanFall: Fezzan absorbed into Empire (S9+)
 * - RagnarokOperation: Empire invasion via Fezzan corridor (S8+)
 * - ReinhardRename: Reinhard Musel -> Reinhard von Lohengramm (S1 only)
 */
@Service
class ScenarioEventService(
    private val eventRepository: EventRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Load LOGH scenario events for a world based on the scenario number.
     * Called during world initialization after the basic scenario is loaded.
     */
    @Transactional
    fun loadLoghScenarioEvents(worldId: Long, scenarioId: Int): List<Event> {
        val events = mutableListOf<Event>()

        when (scenarioId) {
            1 -> {
                // UC795.9 - Reinhard rename event on promotion to Fleet Admiral
                events.add(createEvent(worldId, "month", 950,
                    dateCondition(796, 6),
                    scenarioMessageAction("라인하르트 폰 뮤젤이 로엔그람 백작가를 하사받아 라인하르트 폰 로엔그람으로 개명하였다.")))
            }
            2 -> {
                // UC796.2 - Emperor Friedrich IV death
                events.add(createEvent(worldId, "month", 950,
                    dateCondition(797, 1),
                    scenarioMessageAction("프리드리히 4세가 붕어하였다. 은하제국에 후계 분쟁의 기운이 감돈다.")))
                // Noble uprising possibility
                events.add(createEvent(worldId, "month", 940,
                    dateCondition(797, 3),
                    scenarioMessageAction("브라운슈바이크 공작과 리텐하임 후작이 불온한 움직임을 보이고 있다.")))
            }
            3 -> {
                // UC796.5 - 7th Iserlohn assault
                events.add(createEvent(worldId, "month", 950,
                    dateCondition(796, 6),
                    scenarioMessageAction("양 웬리의 제13함대가 이제르론 요새 공략 작전을 개시한다.")))
            }
            4 -> {
                // UC796.8 - Alliance invasion of Empire territory
                events.add(createEvent(worldId, "month", 950,
                    dateCondition(796, 9),
                    scenarioMessageAction("자유행성동맹이 제국령 침공 작전을 발동하였다. 8개 함대가 제국 영토로 진격한다.")))
                // Greenhill coup warning
                events.add(createEvent(worldId, "month", 900,
                    dateCondition(797, 2),
                    scenarioMessageAction("동맹 내부에서 불온한 정치적 움직임이 감지된다. 그린힐 대장의 동향에 주의가 필요하다.")))
            }
            5 -> {
                // UC796.10 - Battle of Amritsar
                events.add(createEvent(worldId, "month", 950,
                    dateCondition(796, 11),
                    scenarioMessageAction("암리처 성역에서 대규모 회전이 벌어지고 있다. 동맹군의 보급이 끊기고 있다.")))
            }
            6 -> {
                // UC797.4 - Lipstadt War
                events.add(createEvent(worldId, "month", 960,
                    dateCondition(797, 4),
                    scenarioMessageAction("립슈타트 전역 발발! 브라운슈바이크 공작을 맹주로 한 귀족연합이 반란을 일으켰다.")))
                // Alliance civil war (Greenhill)
                events.add(createEvent(worldId, "month", 940,
                    dateCondition(797, 8),
                    scenarioMessageAction("자유행성동맹에서도 구국군사회의에 의한 쿠데타가 발생하였다.")))
            }
            7 -> {
                // UC798.4 - Fortress vs Fortress
                events.add(createEvent(worldId, "month", 950,
                    dateCondition(798, 4),
                    scenarioMessageAction("가이에스부르크 요새가 워프를 통해 이제르론 요새 앞에 출현하였다! 요새 대 요새 결전이 시작된다.")))
            }
            8 -> {
                // UC798.11 - First Ragnarok
                events.add(createEvent(worldId, "month", 960,
                    dateCondition(798, 11),
                    scenarioMessageAction("제1차 라그나뢰크 작전 개시! 제국군이 페잔 회랑을 통한 동맹 침공을 시작한다.")))
                events.add(createEvent(worldId, "month", 940,
                    dateCondition(799, 1),
                    scenarioMessageAction("페잔 자치령이 함락되었다. 제국군이 동맹 영토로 쏟아져 들어온다.")))
            }
            9 -> {
                // UC799.2 - Twin-headed serpent
                events.add(createEvent(worldId, "month", 950,
                    dateCondition(799, 2),
                    scenarioMessageAction("란테마리오 성역에서 제국군과 동맹군의 결전이 시작된다. 동맹의 운명이 걸린 전투이다.")))
            }
            10 -> {
                // UC799.4 - Long live the Emperor
                events.add(createEvent(worldId, "month", 960,
                    dateCondition(799, 4),
                    scenarioMessageAction("버밀리온 성역에서 양 웬리 함대와 라인하르트 직할 함대가 격돌한다.")))
                events.add(createEvent(worldId, "month", 940,
                    dateCondition(799, 5),
                    scenarioMessageAction("하이네센에 제국군이 접근하고 있다. 동맹 정부가 항복을 검토 중이다.")))
            }
        }

        if (events.isNotEmpty()) {
            val saved = eventRepository.saveAll(events)
            log.info("[World {}] Loaded {} LOGH scenario events for scenario {}", worldId, saved.size, scenarioId)
            return saved
        }
        return emptyList()
    }

    private fun createEvent(
        worldId: Long,
        targetCode: String,
        priority: Int,
        condition: Map<String, Any>,
        action: Map<String, Any>,
    ): Event {
        return Event(
            sessionId = worldId,
            targetCode = targetCode,
            priority = priority.toShort(),
            condition = condition.toMutableMap(),
            action = action.toMutableMap(),
        )
    }

    private fun dateCondition(year: Int, month: Int): Map<String, Any> {
        return mapOf(
            "type" to "Date",
            "op" to "==",
            "year" to year,
            "month" to month,
        )
    }

    private fun scenarioMessageAction(message: String): Map<String, Any> {
        return mapOf(
            "type" to "compound",
            "actions" to listOf(
                mapOf("type" to "ScenarioMessage", "message" to message),
                mapOf("type" to "DeleteEvent"),
            ),
        )
    }
}
