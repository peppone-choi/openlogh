package com.openlogh.controller

import com.openlogh.dto.NpcPolicyInfo
import com.openlogh.repository.FactionRepository
import com.openlogh.service.FactionService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/nations/{nationId}")
class NpcPolicyController(
    private val factionRepository: FactionRepository,
    private val factionService: FactionService,
) {
    @GetMapping("/npc-policy")
    fun getPolicy(@PathVariable nationId: Long): ResponseEntity<NpcPolicyInfo> {
        val faction = factionRepository.findById(nationId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        @Suppress("UNCHECKED_CAST")
        val policies = (faction.meta["npcPolicy"] as? Map<String, Any>) ?: emptyMap()
        @Suppress("UNCHECKED_CAST")
        val priorities = (faction.meta["npcPriority"] as? Map<String, Any>) ?: emptyMap()

        return ResponseEntity.ok(NpcPolicyInfo(policies = policies, priorities = priorities))
    }

    @PutMapping("/npc-policy")
    fun updatePolicy(
        @PathVariable nationId: Long,
        @RequestBody policy: Map<String, Any>,
    ): ResponseEntity<Void> {
        val loginId = currentLoginId()
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!factionService.verifyPolicyAccess(nationId, loginId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val faction = factionRepository.findById(nationId).orElse(null)
            ?: return ResponseEntity.notFound().build()
        faction.meta["npcPolicy"] = policy
        factionRepository.save(faction)

        return ResponseEntity.ok().build()
    }

    @PutMapping("/npc-priority")
    fun updatePriority(
        @PathVariable nationId: Long,
        @RequestBody priority: Map<String, Any>,
    ): ResponseEntity<Void> {
        val loginId = currentLoginId()
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!factionService.verifyPolicyAccess(nationId, loginId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val faction = factionRepository.findById(nationId).orElse(null)
            ?: return ResponseEntity.notFound().build()
        faction.meta["npcPriority"] = priority
        factionRepository.save(faction)

        return ResponseEntity.ok().build()
    }

    private fun currentLoginId(): String? {
        return SecurityContextHolder.getContext().authentication?.name
    }
}
