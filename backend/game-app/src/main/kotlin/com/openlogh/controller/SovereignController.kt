package com.openlogh.controller

import com.openlogh.repository.SovereignRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/worlds/{sessionId}")
class SovereignController(
    private val sovereignRepository: SovereignRepository,
) {
    @GetMapping("/sovereign")
    fun getSovereign(@PathVariable sessionId: Long): ResponseEntity<*> {
        val all = sovereignRepository.findAll()
        val sovereign = all.firstOrNull { it.sessionId == sessionId.toString() }
            ?: return ResponseEntity.notFound().build<Any>()
        return ResponseEntity.ok(sovereign)
    }

    @PostMapping("/sovereign/action")
    fun sovereignAction(
        @PathVariable sessionId: Long,
        @RequestBody body: Map<String, Any>,
    ): ResponseEntity<Map<String, Boolean>> {
        // TODO: implement sovereign action logic
        return ResponseEntity.ok(mapOf("success" to true))
    }
}
