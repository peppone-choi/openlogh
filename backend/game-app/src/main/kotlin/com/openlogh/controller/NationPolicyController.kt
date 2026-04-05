package com.openlogh.controller

import com.openlogh.dto.NationPolicyInfo
import com.openlogh.dto.NationMutationResponse
import com.openlogh.dto.SetBillRequest
import com.openlogh.dto.SetNationNoticeRequest
import com.openlogh.dto.SetNationScoutMsgRequest
import com.openlogh.dto.SetRateRequest
import com.openlogh.dto.SetSecretLimitRequest
import com.openlogh.dto.SetToggleRequest
import com.openlogh.dto.UpdateNoticeRequest
import com.openlogh.dto.UpdatePolicyRequest
import com.openlogh.dto.UpdateScoutMsgRequest
import com.openlogh.service.FactionService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/nations/{nationId}")
class NationPolicyController(
    private val factionService: FactionService,
) {
    @GetMapping("/policy")
    fun getPolicy(@PathVariable nationId: Long): ResponseEntity<NationPolicyInfo> {
        val policy = factionService.getPolicy(nationId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(policy)
    }

    @PatchMapping("/policy")
    fun updatePolicy(
        @PathVariable nationId: Long,
        @Valid @RequestBody request: UpdatePolicyRequest,
    ): ResponseEntity<Void> {
        val loginId = currentLoginId() ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!factionService.verifyPolicyAccess(nationId, loginId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
        if (!factionService.updatePolicy(nationId, request.rate, request.bill, request.secretLimit, request.strategicCmdLimit)) {
            return ResponseEntity.notFound().build()
        }
        return ResponseEntity.ok().build()
    }

    @PatchMapping("/notice")
    fun updateNotice(
        @PathVariable nationId: Long,
        @Valid @RequestBody request: UpdateNoticeRequest,
    ): ResponseEntity<Void> {
        val loginId = currentLoginId() ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val actor = factionService.resolvePolicyActor(nationId, loginId)
            ?: return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        if (!factionService.verifyPolicyAccess(nationId, loginId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
        if (!factionService.updateNotice(nationId, request.notice, actor)) return ResponseEntity.notFound().build()
        return ResponseEntity.ok().build()
    }

    @PatchMapping("/scout-msg")
    fun updateScoutMsg(
        @PathVariable nationId: Long,
        @Valid @RequestBody request: UpdateScoutMsgRequest,
    ): ResponseEntity<Void> {
        val loginId = currentLoginId() ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!factionService.verifyPolicyAccess(nationId, loginId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
        if (!factionService.updateScoutMsg(nationId, request.scoutMsg)) return ResponseEntity.notFound().build()
        return ResponseEntity.ok().build()
    }

    @PostMapping("/bill")
    fun setBill(
        @PathVariable nationId: Long,
        @Valid @RequestBody request: SetBillRequest,
    ): ResponseEntity<NationMutationResponse> {
        val loginId = currentLoginId() ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(NationMutationResponse(false, "Unauthorized"))
        if (!factionService.verifyPolicyAccess(nationId, loginId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(NationMutationResponse(false, "권한이 부족합니다."))
        }
        if (!factionService.updateBill(nationId, request.amount)) {
            return ResponseEntity.notFound().build()
        }
        return ResponseEntity.ok(NationMutationResponse(true))
    }

    @PostMapping("/rate")
    fun setRate(
        @PathVariable nationId: Long,
        @Valid @RequestBody request: SetRateRequest,
    ): ResponseEntity<NationMutationResponse> {
        val loginId = currentLoginId() ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(NationMutationResponse(false, "Unauthorized"))
        if (!factionService.verifyPolicyAccess(nationId, loginId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(NationMutationResponse(false, "권한이 부족합니다."))
        }
        if (!factionService.updateRate(nationId, request.amount)) {
            return ResponseEntity.notFound().build()
        }
        return ResponseEntity.ok(NationMutationResponse(true))
    }

    @PostMapping("/secret-limit")
    fun setSecretLimit(
        @PathVariable nationId: Long,
        @Valid @RequestBody request: SetSecretLimitRequest,
    ): ResponseEntity<NationMutationResponse> {
        val loginId = currentLoginId() ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(NationMutationResponse(false, "Unauthorized"))
        if (!factionService.verifyPolicyAccess(nationId, loginId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(NationMutationResponse(false, "권한이 부족합니다."))
        }
        if (!factionService.updateSecretLimit(nationId, request.amount)) {
            return ResponseEntity.notFound().build()
        }
        return ResponseEntity.ok(NationMutationResponse(true))
    }

    @PostMapping("/notice")
    fun setNotice(
        @PathVariable nationId: Long,
        @Valid @RequestBody request: SetNationNoticeRequest,
    ): ResponseEntity<NationMutationResponse> {
        val loginId = currentLoginId() ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(NationMutationResponse(false, "Unauthorized"))
        val actor = factionService.resolvePolicyActor(nationId, loginId)
            ?: return ResponseEntity.status(HttpStatus.FORBIDDEN).body(NationMutationResponse(false, "권한이 부족합니다."))
        if (!factionService.verifyPolicyAccess(nationId, loginId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(NationMutationResponse(false, "권한이 부족합니다."))
        }
        if (!factionService.updateNotice(nationId, request.msg, actor)) {
            return ResponseEntity.notFound().build()
        }
        return ResponseEntity.ok(NationMutationResponse(true))
    }

    @PostMapping("/scout-msg")
    fun setScoutMsg(
        @PathVariable nationId: Long,
        @Valid @RequestBody request: SetNationScoutMsgRequest,
    ): ResponseEntity<NationMutationResponse> {
        val loginId = currentLoginId() ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(NationMutationResponse(false, "Unauthorized"))
        if (!factionService.verifyPolicyAccess(nationId, loginId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(NationMutationResponse(false, "권한이 부족합니다."))
        }
        if (!factionService.updateScoutMsg(nationId, request.msg)) {
            return ResponseEntity.notFound().build()
        }
        return ResponseEntity.ok(NationMutationResponse(true))
    }

    @PostMapping("/block-scout")
    fun setBlockScout(
        @PathVariable nationId: Long,
        @RequestBody request: SetToggleRequest,
    ): ResponseEntity<NationMutationResponse> {
        val loginId = currentLoginId() ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(NationMutationResponse(false, "Unauthorized"))
        if (!factionService.verifyPolicyAccess(nationId, loginId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(NationMutationResponse(false, "권한이 부족합니다."))
        }
        val result = factionService.updateBlockScout(nationId, request.value)
        val status = when {
            result.success -> HttpStatus.OK
            result.reason == null -> HttpStatus.NOT_FOUND
            else -> HttpStatus.BAD_REQUEST
        }
        return ResponseEntity.status(status).body(NationMutationResponse(result.success, result.reason, result.availableCnt))
    }

    @PostMapping("/block-war")
    fun setBlockWar(
        @PathVariable nationId: Long,
        @RequestBody request: SetToggleRequest,
    ): ResponseEntity<NationMutationResponse> {
        val loginId = currentLoginId() ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(NationMutationResponse(false, "Unauthorized"))
        if (!factionService.verifyPolicyAccess(nationId, loginId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(NationMutationResponse(false, "권한이 부족합니다."))
        }
        val result = factionService.updateBlockWar(nationId, request.value)
        val status = when {
            result.success -> HttpStatus.OK
            result.reason == null -> HttpStatus.NOT_FOUND
            else -> HttpStatus.BAD_REQUEST
        }
        return ResponseEntity.status(status).body(NationMutationResponse(result.success, result.reason, result.availableCnt))
    }

    private fun currentLoginId(): String? = SecurityContextHolder.getContext().authentication?.name
}
