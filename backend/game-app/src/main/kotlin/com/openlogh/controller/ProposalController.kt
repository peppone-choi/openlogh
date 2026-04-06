package com.openlogh.controller

import com.openlogh.dto.EligibleApproverResponse
import com.openlogh.dto.ProposalResponse
import com.openlogh.dto.ResolveProposalRequest
import com.openlogh.dto.SubmitProposalRequest
import com.openlogh.service.CommandService
import com.openlogh.service.ProposalService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*

/**
 * REST controller for the proposal/suggestion system.
 *
 * Endpoints follow the same authentication pattern as CommandController:
 * generalId is the acting officer, ownership verified via SecurityContext.
 */
@RestController
@RequestMapping("/api/proposals")
class ProposalController(
    private val proposalService: ProposalService,
    private val commandService: CommandService,
) {

    /**
     * Submit a proposal from generalId (requester) to an approver.
     */
    @PostMapping("/submit/{generalId}")
    fun submit(
        @PathVariable generalId: Long,
        @RequestBody request: SubmitProposalRequest,
    ): ResponseEntity<ProposalResponse> {
        val loginId = SecurityContextHolder.getContext().authentication?.name
            ?: return ResponseEntity.status(403).build()
        if (!commandService.verifyOwnership(generalId, loginId)) {
            return ResponseEntity.status(403).build()
        }
        return try {
            val response = proposalService.submitProposal(generalId, request)
            ResponseEntity.ok(response)
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        } catch (e: IllegalStateException) {
            ResponseEntity.badRequest().build()
        }
    }

    /**
     * Approve or reject a proposal. generalId must be the approver.
     */
    @PostMapping("/resolve/{generalId}/{proposalId}")
    fun resolve(
        @PathVariable generalId: Long,
        @PathVariable proposalId: Long,
        @RequestBody request: ResolveProposalRequest,
    ): ResponseEntity<ProposalResponse> {
        val loginId = SecurityContextHolder.getContext().authentication?.name
            ?: return ResponseEntity.status(403).build()
        if (!commandService.verifyOwnership(generalId, loginId)) {
            return ResponseEntity.status(403).build()
        }
        return try {
            val response = proposalService.resolveProposal(generalId, proposalId, request)
            ResponseEntity.ok(response)
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        } catch (e: IllegalStateException) {
            ResponseEntity.badRequest().build()
        }
    }

    /**
     * List pending proposals awaiting approval by generalId.
     */
    @GetMapping("/pending/{generalId}")
    fun pendingForApprover(@PathVariable generalId: Long): ResponseEntity<List<ProposalResponse>> {
        return ResponseEntity.ok(proposalService.listPendingForApprover(generalId))
    }

    /**
     * List all proposals submitted by generalId.
     */
    @GetMapping("/my/{generalId}")
    fun myProposals(@PathVariable generalId: Long): ResponseEntity<List<ProposalResponse>> {
        return ResponseEntity.ok(proposalService.listMyProposals(generalId))
    }

    /**
     * Find officers in the same faction who can approve a given command.
     */
    @GetMapping("/eligible-approvers/{generalId}")
    fun eligibleApprovers(
        @PathVariable generalId: Long,
        @RequestParam actionCode: String,
    ): ResponseEntity<List<EligibleApproverResponse>> {
        return try {
            ResponseEntity.ok(proposalService.findEligibleApprovers(generalId, actionCode))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        }
    }
}
