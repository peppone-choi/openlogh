package com.openlogh.controller

import com.openlogh.dto.NationResponse
import com.openlogh.service.FactionService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api")
class NationController(
    private val factionService: FactionService,
) {
    @GetMapping("/worlds/{worldId}/nations")
    fun listByWorld(@PathVariable worldId: Long): ResponseEntity<List<NationResponse>> {
        return ResponseEntity.ok(factionService.listByWorld(worldId).map { NationResponse.from(it) })
    }

    @GetMapping("/nations/{id}")
    fun getById(@PathVariable id: Long): ResponseEntity<NationResponse> {
        val nation = factionService.getById(id)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(NationResponse.from(nation))
    }

    data class UpdateAbbreviationRequest(val abbreviation: String)

    @PatchMapping("/nations/{id}/abbreviation")
    fun updateAbbreviation(
        @PathVariable id: Long,
        @RequestBody request: UpdateAbbreviationRequest,
    ): ResponseEntity<NationResponse> {
        val abbr = request.abbreviation.take(2)
        if (abbr.isBlank()) return ResponseEntity.badRequest().build()
        val nation = factionService.updateAbbreviation(id, abbr)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(NationResponse.from(nation))
    }
}
