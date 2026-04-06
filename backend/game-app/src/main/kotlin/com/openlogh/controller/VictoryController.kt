package com.openlogh.controller

import com.openlogh.service.VictoryResult
import com.openlogh.service.VictoryService
import com.openlogh.repository.SessionStateRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

data class VictoryResultDto(
    val condition: String,
    val conditionKorean: String,
    val tier: String,
    val tierKorean: String,
    val winnerFactionId: Long,
    val loserFactionId: Long,
    val winnerName: String,
    val loserName: String,
    val stats: Map<String, Any>,
) {
    companion object {
        fun from(result: VictoryResult) = VictoryResultDto(
            condition = result.condition.name,
            conditionKorean = result.condition.korean,
            tier = result.tier.name,
            tierKorean = result.tier.korean,
            winnerFactionId = result.winnerFactionId,
            loserFactionId = result.loserFactionId,
            winnerName = result.winnerName,
            loserName = result.loserName,
            stats = result.stats,
        )
    }
}

@RestController
@RequestMapping("/api/v1/world/{sessionId}/victory")
class VictoryController(
    private val victoryService: VictoryService,
    private val sessionStateRepository: SessionStateRepository,
) {
    /**
     * Get victory result for a session (if ended).
     */
    @GetMapping
    fun getVictoryResult(@PathVariable sessionId: Long): ResponseEntity<Any> {
        val world = sessionStateRepository.findById(sessionId.toShort()).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val victoryData = world.meta["victoryResult"] as? Map<*, *>
            ?: return ResponseEntity.ok(mapOf("ended" to false))

        return ResponseEntity.ok(mapOf(
            "ended" to true,
            "result" to victoryData,
        ))
    }

    /**
     * Manually trigger victory check (admin/debug).
     */
    @PostMapping("/check")
    fun checkVictory(@PathVariable sessionId: Long): ResponseEntity<Any> {
        val result = victoryService.checkVictoryConditions(sessionId)
            ?: return ResponseEntity.ok(mapOf("victoryDetected" to false))

        return ResponseEntity.ok(mapOf(
            "victoryDetected" to true,
            "result" to VictoryResultDto.from(result),
        ))
    }
}
