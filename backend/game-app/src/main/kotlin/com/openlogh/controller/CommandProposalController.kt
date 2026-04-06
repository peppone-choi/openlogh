package com.openlogh.controller

import com.openlogh.entity.CommandProposal
import com.openlogh.service.CommandProposalService
import kotlinx.coroutines.runBlocking
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/{sessionId}/proposals")
class CommandProposalController(
    private val proposalService: CommandProposalService,
) {
    data class CreateProposalRequest(
        val proposerId: Long,
        val approverId: Long,
        val commandCode: String,
        val args: Map<String, Any> = emptyMap(),
    )

    @PostMapping
    fun createProposal(
        @PathVariable sessionId: Long,
        @RequestBody request: CreateProposalRequest,
    ): ResponseEntity<CommandProposal> {
        val proposal = proposalService.createProposal(
            sessionId, request.proposerId, request.approverId,
            request.commandCode, request.args,
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(proposal)
    }

    @PutMapping("/{proposalId}/approve")
    fun approveProposal(
        @PathVariable sessionId: Long,
        @PathVariable proposalId: Long,
        @RequestParam approverId: Long,
    ): ResponseEntity<CommandProposal> {
        val result = runBlocking {
            proposalService.approveProposal(sessionId, proposalId, approverId)
        }
        return ResponseEntity.ok(result)
    }

    @PutMapping("/{proposalId}/reject")
    fun rejectProposal(
        @PathVariable sessionId: Long,
        @PathVariable proposalId: Long,
        @RequestParam approverId: Long,
    ): ResponseEntity<CommandProposal> {
        val result = proposalService.rejectProposal(sessionId, proposalId, approverId)
        return ResponseEntity.ok(result)
    }

    @GetMapping("/pending")
    fun getPendingProposals(
        @PathVariable sessionId: Long,
        @RequestParam approverId: Long,
    ): ResponseEntity<List<CommandProposal>> =
        ResponseEntity.ok(proposalService.getPendingProposals(sessionId, approverId))

    @GetMapping("/mine")
    fun getMyProposals(
        @PathVariable sessionId: Long,
        @RequestParam proposerId: Long,
    ): ResponseEntity<List<CommandProposal>> =
        ResponseEntity.ok(proposalService.getMyProposals(sessionId, proposerId))
}
