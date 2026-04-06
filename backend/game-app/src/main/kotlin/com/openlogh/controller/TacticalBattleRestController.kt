package com.openlogh.controller

import com.openlogh.dto.ActiveBattlesResponse
import com.openlogh.dto.TacticalBattleDto
import com.openlogh.service.TacticalBattleService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * REST controller for tactical battle queries.
 */
@RestController
@RequestMapping("/api/v1/battle")
class TacticalBattleRestController(
    private val tacticalBattleService: TacticalBattleService,
) {

    /**
     * List all active tactical battles in a session.
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
}
