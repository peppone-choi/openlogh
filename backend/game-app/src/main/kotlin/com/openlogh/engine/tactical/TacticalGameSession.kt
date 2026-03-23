package com.openlogh.engine.tactical

import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * WebSocket/세션 관리용 래퍼.
 * 엔진의 TacticalBattleSession을 감싸고 참가/준비/타이머 상태를 추가.
 */
class TacticalGameSession(
    val battleSession: TacticalBattleSession,
    val allOfficerIds: Set<Long>,
    val attackerFactionId: Long,
    val defenderFactionId: Long,
) {
    val sessionCode: String get() = battleSession.sessionCode

    val joinedOfficers: MutableSet<Long> = Collections.synchronizedSet(mutableSetOf())
    val readyOfficers: MutableSet<Long> = Collections.synchronizedSet(mutableSetOf())
    val previousOrders: MutableMap<Long, MutableList<TacticalOrder>> = ConcurrentHashMap()

    fun isAllJoined(): Boolean = joinedOfficers.containsAll(allOfficerIds)
    fun isAllReady(): Boolean = readyOfficers.containsAll(allOfficerIds)

    fun findFleetByOfficer(officerId: Long): TacticalFleet? =
        (battleSession.attackerFleets + battleSession.defenderFleets)
            .firstOrNull { it.officerId == officerId }

    fun getSide(factionId: Long): String =
        if (battleSession.isAttacker(factionId)) "attacker" else "defender"

    /** 턴 전환 준비: 이전 명령 저장, 준비 상태 초기화 */
    fun prepareNextTurn() {
        previousOrders.clear()
        for ((key, value) in battleSession.pendingOrders) {
            previousOrders[key] = value.toMutableList()
        }
        readyOfficers.clear()
    }

    /** 타임아웃 시 미입력 장교에 이전 명령 적용 */
    fun applyPreviousOrdersForMissing() {
        for (officerId in allOfficerIds) {
            if (officerId !in battleSession.pendingOrders) {
                previousOrders[officerId]?.let {
                    battleSession.submitOrders(officerId, it)
                }
            }
        }
    }

    /** WebSocket 브로드캐스트용 상태 DTO 변환 */
    fun toStateDto(timer: Int): TacticalStateDto {
        val session = battleSession
        return TacticalStateDto(
            sessionCode = sessionCode,
            turn = session.currentTurn,
            phase = session.phase.name.lowercase(),
            attackerFleets = session.attackerFleets.map { it.toDto("attacker") },
            defenderFleets = session.defenderFleets.map { it.toDto("defender") },
            grid = GridStateDto(
                fieldSize = session.grid.fieldSize,
                obstacles = session.grid.obstacles.map { obs ->
                    ObstacleDto(obs.center.x, obs.center.y, obs.center.z, obs.radius, obs.type.name.lowercase())
                },
            ),
            events = session.battleLog
                .filter { it.turn == session.currentTurn }
                .map { it.toDto() },
            timer = timer,
        )
    }

    /** 승리 DTO 변환 */
    fun toVictoryDto(victory: VictoryResult): TacticalVictoryDto {
        val winnerFactionId = victory.winnerFactionId
        val winnerSide = if (winnerFactionId == attackerFactionId) "attacker" else "defender"
        return TacticalVictoryDto(
            sessionCode = sessionCode,
            winnerFactionId = winnerFactionId,
            winnerSide = winnerSide,
            victoryType = victory.victoryType.name,
            description = victory.description,
            turns = battleSession.currentTurn,
        )
    }
}
