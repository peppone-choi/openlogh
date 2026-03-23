package com.openlogh.controller

import com.openlogh.dto.DiplomacyDto
import com.openlogh.dto.DiplomacyRespondWithActionRequest
import com.openlogh.repository.DiplomacyRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/worlds/{worldId}")
class DiplomacyController(
    private val diplomacyRepository: DiplomacyRepository,
) {
    @GetMapping("/diplomacy")
    fun list(@PathVariable worldId: Long): ResponseEntity<List<DiplomacyDto>> {
        val items = diplomacyRepository.findBySessionId(worldId)
        return ResponseEntity.ok(items.map { DiplomacyDto.from(it) })
    }

    @GetMapping("/diplomacy/nation/{nationId}")
    fun listByNation(
        @PathVariable worldId: Long,
        @PathVariable nationId: Long,
    ): ResponseEntity<List<DiplomacyDto>> {
        val items = diplomacyRepository.findBySessionIdAndSrcNationIdOrDestFactionId(worldId, nationId, nationId)
        return ResponseEntity.ok(items.map { DiplomacyDto.from(it) })
    }

    @PostMapping("/diplomacy/respond")
    fun respond(
        @PathVariable worldId: Long,
        @RequestBody req: DiplomacyRespondWithActionRequest,
    ): ResponseEntity<Map<String, Boolean>> {
        // TODO: implement diplomacy response logic
        return ResponseEntity.ok(mapOf("success" to true))
    }
}
