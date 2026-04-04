package com.openlogh.controller

import com.openlogh.dto.DiplomacyDto
import com.openlogh.dto.DiplomacyRespondWithActionRequest
import com.openlogh.engine.DiplomacyService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/worlds/{worldId}/diplomacy")
class DiplomacyController(
    private val diplomacyService: DiplomacyService,
) {
    @GetMapping
    fun getRelations(@PathVariable worldId: Long): ResponseEntity<List<DiplomacyDto>> {
        return ResponseEntity.ok(diplomacyService.getRelations(worldId).map(DiplomacyDto::from))
    }

    @GetMapping("/nation/{nationId}")
    fun getRelationsForNation(
        @PathVariable worldId: Long,
        @PathVariable nationId: Long,
    ): ResponseEntity<List<DiplomacyDto>> {
        return ResponseEntity.ok(diplomacyService.getRelationsForNation(worldId, nationId).map(DiplomacyDto::from))
    }

    @PostMapping("/respond")
    fun respond(
        @PathVariable worldId: Long,
        @RequestBody request: DiplomacyRespondWithActionRequest,
    ): ResponseEntity<Void> {
        return try {
            if (request.accept) {
                diplomacyService.acceptDiplomaticMessage(worldId, request.messageId)
            } else {
                diplomacyService.rejectDiplomaticMessage(worldId, request.messageId)
            }
            ResponseEntity.ok().build()
        } catch (_: Exception) {
            ResponseEntity.badRequest().build()
        }
    }
}
