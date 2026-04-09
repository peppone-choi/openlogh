package com.openlogh.controller

import com.openlogh.dto.ActiveBattlesResponse
import com.openlogh.dto.BattleSummaryDto
import com.openlogh.dto.TacticalBattleDto
import com.openlogh.dto.TacticalBattleHistoryDto
import com.openlogh.service.TacticalBattleService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * REST API for tactical battle queries.
 *
 * Endpoints:
 *   GET /api/v1/battle/{sessionId}/active             — list active battles
 *   GET /api/v1/battle/{sessionId}/{battleId}         — get battle state
 *   GET /api/v1/battle/{sessionId}/{battleId}/history — get battle tick history
 *   GET /api/v1/battle/{sessionId}/{battleId}/summary — Phase 14 end-of-battle merit breakdown
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

    /**
     * Phase 14 Plan 14-02 (D-32..D-34): per-unit merit breakdown for the
     * end-of-battle modal. Returns `BattleSummaryDto` containing the
     * base + operation-multiplier decomposition that was actually credited
     * during [TacticalBattleService.endBattle] — this is how Phase 12 OPS-02
     * ("×1.5 for operation participants") is visually verified.
     *
     * Responses:
     *   200 — battle found and ended → BattleSummaryDto
     *   404 — battle does not exist (NoSuchElementException) or session mismatch
     *         (IllegalArgumentException — mirrors [getBattleState] not-found semantics)
     *   409 — battle exists but has not ended yet (IllegalStateException)
     */
    @GetMapping("/{sessionId}/{battleId}/summary")
    fun getBattleSummary(
        @PathVariable sessionId: Long,
        @PathVariable battleId: Long,
    ): ResponseEntity<BattleSummaryDto> = try {
        ResponseEntity.ok(tacticalBattleService.buildBattleSummary(sessionId, battleId))
    } catch (e: NoSuchElementException) {
        ResponseEntity.notFound().build()
    } catch (e: IllegalArgumentException) {
        // Session mismatch — treat as not-found to avoid leaking cross-session existence.
        ResponseEntity.notFound().build()
    } catch (e: IllegalStateException) {
        ResponseEntity.status(HttpStatus.CONFLICT).build()
    }
}
