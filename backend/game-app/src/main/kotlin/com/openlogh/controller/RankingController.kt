package com.openlogh.controller

import com.openlogh.dto.BestOfficerResponse
import com.openlogh.dto.MessageResponse
import com.openlogh.service.RankingService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api")
class RankingController(
    private val rankingService: RankingService,
) {
    @GetMapping("/worlds/{sessionId}/best-officers")
    fun bestOfficers(
        @PathVariable sessionId: Long,
        @RequestParam(defaultValue = "experience") sortBy: String,
        @RequestParam(defaultValue = "20") limit: Int,
    ): ResponseEntity<List<BestOfficerResponse>> {
        return ResponseEntity.ok(rankingService.bestGenerals(sessionId, sortBy, limit))
    }

    @GetMapping("/worlds/{sessionId}/hall-of-fame")
    fun hallOfFame(
        @PathVariable sessionId: Long,
        @RequestParam(required = false) season: Int?,
        @RequestParam(required = false) scenario: String?,
    ): ResponseEntity<List<MessageResponse>> {
        return ResponseEntity.ok(rankingService.hallOfFame(sessionId, season, scenario))
    }

    @GetMapping("/worlds/{sessionId}/hall-of-fame/options")
    fun hallOfFameOptions(@PathVariable sessionId: Long): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.ok(rankingService.hallOfFameOptions(sessionId))
    }

    @GetMapping("/worlds/{sessionId}/unique-item-owners")
    fun uniqueItemOwners(@PathVariable sessionId: Long): ResponseEntity<List<Map<String, Any?>>> {
        return ResponseEntity.ok(rankingService.uniqueItemOwners(sessionId))
    }
}
