package com.openlogh.controller

import com.openlogh.dto.FactionMutationResponse
import com.openlogh.dto.FactionPolicyInfo
import com.openlogh.dto.SetFactionNoticeRequest
import com.openlogh.dto.SetFactionScoutMsgRequest
import com.openlogh.dto.SetToggleRequest
import com.openlogh.dto.UpdatePolicyRequest
import com.openlogh.repository.FactionRepository
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.AppUserRepository
import com.openlogh.service.FactionService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/factions/{nationId}")
class NationPolicyController(
    private val factionService: FactionService,
    private val factionRepository: FactionRepository,
    private val officerRepository: OfficerRepository,
    private val appUserRepository: AppUserRepository,
) {
    @GetMapping("/policy")
    fun getPolicy(@PathVariable nationId: Long): ResponseEntity<FactionPolicyInfo> {
        val policy = factionService.getPolicy(nationId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(policy)
    }

    @PatchMapping("/policy")
    fun updatePolicy(
        @PathVariable nationId: Long,
        @Valid @RequestBody req: UpdatePolicyRequest,
    ): ResponseEntity<Void> {
        val loginId = currentLoginId()
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!factionService.verifyPolicyAccess(nationId, loginId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val faction = factionRepository.findById(nationId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        req.conscriptionRate?.let { faction.conscriptionRate = it.toShort() }
        req.taxRate?.let { faction.taxRate = it.toShort() }
        req.secretLimit?.let { faction.secretLimit = it.toShort() }
        req.strategicCmdLimit?.let { faction.strategicCmdLimit = it.toShort() }
        factionRepository.save(faction)

        return ResponseEntity.ok().build()
    }

    @PatchMapping("/notice")
    fun updateNotice(
        @PathVariable nationId: Long,
        @RequestBody body: Map<String, String>,
    ): ResponseEntity<Void> {
        val loginId = currentLoginId()
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!factionService.verifyPolicyAccess(nationId, loginId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val notice = body["notice"] ?: return ResponseEntity.badRequest().build()
        val user = appUserRepository.findByLoginId(loginId) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val faction = factionRepository.findById(nationId).orElse(null) ?: return ResponseEntity.notFound().build()
        val officers = officerRepository.findBySessionIdAndUserId(faction.sessionId, user.id)
        val officer = officers.firstOrNull { it.factionId == nationId }
            ?: return ResponseEntity.status(HttpStatus.FORBIDDEN).build()

        factionService.updateNotice(nationId, notice, officer)
        return ResponseEntity.ok().build()
    }

    @PatchMapping("/scout-msg")
    fun updateScoutMsg(
        @PathVariable nationId: Long,
        @Valid @RequestBody req: SetFactionScoutMsgRequest,
    ): ResponseEntity<Void> {
        val loginId = currentLoginId()
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!factionService.verifyPolicyAccess(nationId, loginId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val faction = factionRepository.findById(nationId).orElse(null)
            ?: return ResponseEntity.notFound().build()
        faction.meta["scout_msg"] = req.msg
        faction.meta["scoutMsg"] = req.msg
        factionRepository.save(faction)

        return ResponseEntity.ok().build()
    }

    @PostMapping("/block-war")
    fun setBlockWar(
        @PathVariable nationId: Long,
        @RequestBody req: SetToggleRequest,
    ): ResponseEntity<FactionMutationResponse> {
        val loginId = currentLoginId()
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!factionService.verifyPolicyAccess(nationId, loginId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
        return ResponseEntity.ok(factionService.updateBlockWar(nationId, req.value))
    }

    @PostMapping("/block-scout")
    fun setBlockScout(
        @PathVariable nationId: Long,
        @RequestBody req: SetToggleRequest,
    ): ResponseEntity<FactionMutationResponse> {
        val loginId = currentLoginId()
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!factionService.verifyPolicyAccess(nationId, loginId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
        return ResponseEntity.ok(factionService.updateBlockScout(nationId, req.value))
    }

    private fun currentLoginId(): String? {
        return SecurityContextHolder.getContext().authentication?.name
    }
}
