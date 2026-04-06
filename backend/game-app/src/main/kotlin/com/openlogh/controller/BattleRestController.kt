package com.openlogh.controller

import com.openlogh.dto.TacticalBattleDto
import com.openlogh.service.TacticalBattleService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * REST controller for tactical battle management.
 *
 * Endpoints:
 *   POST /api/{sessionId}/battles/start   → 전투 수동 시작 (테스트/관리 용도)
 *   GET  /api/{sessionId}/battles/active  → 활성 전투 목록
 *   GET  /api/{sessionId}/battles/{battleId} → 전투 상태 조회
 */
@RestController
@RequestMapping("/api/{sessionId}/battles")
class BattleRestController(
    private val tacticalBattleService: TacticalBattleService,
) {

    /**
     * 전투 수동 시작 (테스트/관리 용도).
     * POST /api/{sessionId}/battles/start
     */
    @PostMapping("/start")
    fun startBattle(
        @PathVariable sessionId: Long,
        @RequestBody body: StartBattleRequest,
    ): ResponseEntity<TacticalBattleDto> {
        val battle = tacticalBattleService.startBattle(
            sessionId = sessionId,
            starSystemId = body.starSystemId,
            attackerFleetIds = body.attackerFleetIds,
            defenderFleetIds = body.defenderFleetIds,
        )
        @Suppress("UNCHECKED_CAST")
        val attackerIds = (battle.participants["attackers"] as? List<*>)
            ?.mapNotNull { (it as? Number)?.toLong() } ?: body.attackerFleetIds
        @Suppress("UNCHECKED_CAST")
        val defenderIds = (battle.participants["defenders"] as? List<*>)
            ?.mapNotNull { (it as? Number)?.toLong() } ?: body.defenderFleetIds

        return ResponseEntity.ok(
            TacticalBattleDto(
                id = battle.id,
                sessionId = battle.sessionId,
                starSystemId = battle.starSystemId,
                attackerFactionId = battle.attackerFactionId,
                defenderFactionId = battle.defenderFactionId,
                phase = battle.phase,
                startedAt = battle.startedAt.toString(),
                endedAt = null,
                result = null,
                tickCount = 0,
                attackerFleetIds = attackerIds,
                defenderFleetIds = defenderIds,
                units = emptyList(),
            )
        )
    }

    /**
     * 세션의 활성 전투 목록 조회.
     * GET /api/{sessionId}/battles/active
     */
    @GetMapping("/active")
    fun getActiveBattles(@PathVariable sessionId: Long): List<TacticalBattleDto> =
        tacticalBattleService.getActiveBattles(sessionId)

    /**
     * 특정 전투 상태 조회.
     * GET /api/{sessionId}/battles/{battleId}
     */
    @GetMapping("/{battleId}")
    fun getBattleState(
        @PathVariable sessionId: Long,
        @PathVariable battleId: Long,
    ): ResponseEntity<TacticalBattleDto> {
        val dto = tacticalBattleService.getBattleState(sessionId, battleId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(dto)
    }
}

/** 전투 시작 요청 DTO */
data class StartBattleRequest(
    val starSystemId: Long,
    val attackerFleetIds: List<Long>,
    val defenderFleetIds: List<Long>,
)
