package com.openlogh.controller

import com.openlogh.dto.MessageResponse
import com.openlogh.dto.RespondLetterRequest
import com.openlogh.dto.SendLetterRequest
import com.openlogh.service.DiplomacyLetterService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api")
class DiplomacyLetterController(
    private val diplomacyLetterService: DiplomacyLetterService,
) {
    @GetMapping("/nations/{nationId}/diplomacy-letters")
    fun listLetters(@PathVariable nationId: Long): ResponseEntity<List<MessageResponse>> {
        return ResponseEntity.ok(diplomacyLetterService.listLetters(nationId).map { MessageResponse.from(it) })
    }

    @PostMapping("/nations/{nationId}/diplomacy-letters")
    fun sendLetter(
        @PathVariable nationId: Long,
        @RequestBody request: SendLetterRequest,
    ): ResponseEntity<MessageResponse> {
        val letter = diplomacyLetterService.sendLetter(request.worldId, nationId, request.destNationId, request.type, request.content)
        return ResponseEntity.status(HttpStatus.CREATED).body(MessageResponse.from(letter))
    }

    @PostMapping("/diplomacy-letters/{id}/respond")
    fun respondLetter(
        @PathVariable id: Long,
        @RequestBody request: RespondLetterRequest,
    ): ResponseEntity<Void> {
        if (!diplomacyLetterService.respondLetter(id, request.accept)) return ResponseEntity.notFound().build()
        return ResponseEntity.ok().build()
    }

    @PostMapping("/diplomacy-letters/{id}/execute")
    fun executeLetter(@PathVariable id: Long): ResponseEntity<Void> {
        if (!diplomacyLetterService.executeLetter(id)) return ResponseEntity.notFound().build()
        return ResponseEntity.ok().build()
    }

    @PostMapping("/diplomacy-letters/{id}/rollback")
    fun rollbackLetter(@PathVariable id: Long): ResponseEntity<Void> {
        if (!diplomacyLetterService.rollbackLetter(id)) return ResponseEntity.notFound().build()
        return ResponseEntity.ok().build()
    }

    @PostMapping("/diplomacy-letters/{id}/destroy")
    fun destroyLetter(@PathVariable id: Long): ResponseEntity<Void> {
        if (!diplomacyLetterService.destroyLetter(id)) return ResponseEntity.notFound().build()
        return ResponseEntity.noContent().build()
    }
}
