package com.openlogh.controller

import com.openlogh.dto.ActiveBattlesResponse
import com.openlogh.dto.TacticalBattleDto
import com.openlogh.dto.TacticalBattleHistoryDto
import com.openlogh.service.TacticalBattleService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * REST API for tactical battle queries.
 *
 * Endpoints:
 *   GET /api/v1/battle/{sessionId}/active           — list active battles
 *   GET /api/v1/battle/{sessionId}/{battleId}        — get battle state
 *   GET /api/v1/battle/{sessionId}/{battleId}/history — get battle tick history
 */
@RestController
@RequestMapping("/api/v1/battle")
class TacticalBattleRestController(
    private val tacticalBattleService: TacticalBattleService,
) {

    /**
     * List all active battles in a session.
     */
    @GetMapping("/{sessionId}/active")
    fun getActiveBattles(@PathVariable sessionId: Long): ResponseEntity<ActiveBattlesResponse> {
        val battles = tacticalBattleService.getActiveBattles(sessionId)
        return ResponseEntity.ok(ActiveBattlesResponse(battles))
    }

    /**
     * Get a specific battle's current state.
     */
    @GetMapping("/{sessionId}/{battleId}")
    fun getBattleState(
        @PathVariable sessionId: Long,
        @PathVariable battleId: Long,
    ): ResponseEntity<TacticalBattleDto> {
        val battle = tacticalBattleService.getBattleState(sessionId, battleId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(battle)
    }

    /**
     * Get battle tick history (completed battles).
     * Returns battle metadata with result and tick count.
     */
    @GetMapping("/{sessionId}/{battleId}/history")
    fun getBattleHistory(
        @PathVariable sessionId: Long,
        @PathVariable battleId: Long,
    ): ResponseEntity<TacticalBattleHistoryDto> {
        val history = tacticalBattleService.getBattleHistory(sessionId, battleId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(history)
    }
}
