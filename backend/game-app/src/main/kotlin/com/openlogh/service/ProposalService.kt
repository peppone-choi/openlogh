package com.openlogh.service

import com.openlogh.dto.EligibleApproverResponse
import com.openlogh.dto.ProposalResponse
import com.openlogh.dto.ResolveProposalRequest
import com.openlogh.dto.SubmitProposalRequest
import com.openlogh.entity.Proposal
import com.openlogh.model.PositionCardRegistry
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.ProposalRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

/**
 * Service for the proposal/suggestion system (gin7 제안 시스템).
 *
 * Lower-rank officers can propose commands to superiors who hold the required
 * position cards. On approval, the command is executed using the superior's
 * card authority but CP is deducted from the requester (gin7 rule).
 */
@Service
class ProposalService(
    private val proposalRepository: ProposalRepository,
    private val officerRepository: OfficerRepository,
    private val commandService: CommandService,
    private val messageService: MessageService,
    private val gameEventService: GameEventService? = null,
) {

    /**
     * Submit a proposal from a requester to an approver.
     *
     * Validates:
     * - Requester and approver exist and belong to the same session/faction
     * - Approver holds a position card that can execute the requested command
     * - Requester does NOT already have such a card (otherwise they should execute directly)
     */
    @Transactional
    fun submitProposal(requesterId: Long, request: SubmitProposalRequest): ProposalResponse {
        val requester = officerRepository.findById(requesterId).orElseThrow {
            IllegalArgumentException("요청자를 찾을 수 없습니다: $requesterId")
        }
        val approver = officerRepository.findById(request.approverId).orElseThrow {
            IllegalArgumentException("승인자를 찾을 수 없습니다: ${request.approverId}")
        }

        // Same session check
        require(requester.sessionId == approver.sessionId) {
            "요청자와 승인자가 같은 세션에 있어야 합니다."
        }
        // Same faction check
        require(requester.factionId == approver.factionId && requester.factionId != 0L) {
            "요청자와 승인자가 같은 진영에 소속되어 있어야 합니다."
        }

        // Verify approver holds a card that can execute the command
        val approverCards = approver.getPositionCardEnums()
        require(PositionCardRegistry.canExecute(approverCards, request.actionCode)) {
            "승인자가 해당 커맨드를 실행할 권한이 없습니다: ${request.actionCode}"
        }

        // Verify requester does NOT have the card (otherwise they should execute directly)
        val requesterCards = requester.getPositionCardEnums()
        require(!PositionCardRegistry.canExecute(requesterCards, request.actionCode)) {
            "요청자가 이미 해당 커맨드를 실행할 권한을 가지고 있습니다. 직접 실행하세요."
        }

        val proposal = proposalRepository.save(
            Proposal(
                sessionId = requester.sessionId,
                requesterId = requesterId,
                approverId = request.approverId,
                actionCode = request.actionCode,
                args = request.args?.toMutableMap() ?: mutableMapOf(),
                status = "pending",
                reason = request.reason,
            )
        )

        // Send in-game message notification to approver
        messageService.sendMessage(
            worldId = requester.sessionId,
            mailboxCode = "personal",
            messageType = "proposal_submitted",
            srcId = requesterId,
            destId = request.approverId,
            payload = mapOf(
                "proposalId" to proposal.id,
                "requesterName" to requester.name,
                "actionCode" to request.actionCode,
                "text" to "${requester.name}이(가) ${request.actionCode} 커맨드를 제안했습니다.",
            ),
        )

        return toResponse(proposal, requester.name, approver.name)
    }

    /**
     * Resolve (approve or reject) a proposal.
     *
     * On approval:
     * - Execute the command via CommandService using the approver's context
     * - CP is deducted from the REQUESTER (gin7 rule), not the approver
     *
     * On rejection:
     * - Notify the requester with the reason
     */
    @Transactional
    fun resolveProposal(approverId: Long, proposalId: Long, request: ResolveProposalRequest): ProposalResponse {
        val proposal = proposalRepository.findById(proposalId).orElseThrow {
            IllegalArgumentException("제안을 찾을 수 없습니다: $proposalId")
        }
        require(proposal.approverId == approverId) {
            "이 제안의 승인 권한이 없습니다."
        }
        require(proposal.status == "pending") {
            "이미 처리된 제안입니다: ${proposal.status}"
        }

        val approver = officerRepository.findById(approverId).orElseThrow {
            IllegalArgumentException("승인자를 찾을 수 없습니다: $approverId")
        }
        val requester = officerRepository.findById(proposal.requesterId).orElseThrow {
            IllegalArgumentException("요청자를 찾을 수 없습니다: ${proposal.requesterId}")
        }

        if (request.approved) {
            // Execute the command using the approver's authority
            // The command is executed as the requester acting with the approver's card authority
            val result = commandService.executeCommand(
                generalId = proposal.requesterId,
                actionCode = proposal.actionCode,
                arg = proposal.args.ifEmpty { null },
            )

            proposal.status = "approved"
            proposal.resolvedAt = OffsetDateTime.now()
            proposalRepository.save(proposal)

            // Notify requester of approval
            messageService.sendMessage(
                worldId = proposal.sessionId,
                mailboxCode = "personal",
                messageType = "proposal_approved",
                srcId = approverId,
                destId = proposal.requesterId,
                payload = mapOf(
                    "proposalId" to proposal.id,
                    "actionCode" to proposal.actionCode,
                    "text" to "제안이 승인되었습니다: ${proposal.actionCode}",
                    "commandResult" to (result?.logs?.joinToString("; ") ?: ""),
                ),
            )
        } else {
            proposal.status = "rejected"
            proposal.reason = request.reason ?: proposal.reason
            proposal.resolvedAt = OffsetDateTime.now()
            proposalRepository.save(proposal)

            // Notify requester of rejection
            val reasonText = if (request.reason.isNullOrBlank()) "" else " - ${request.reason}"
            messageService.sendMessage(
                worldId = proposal.sessionId,
                mailboxCode = "personal",
                messageType = "proposal_rejected",
                srcId = approverId,
                destId = proposal.requesterId,
                payload = mapOf(
                    "proposalId" to proposal.id,
                    "actionCode" to proposal.actionCode,
                    "text" to "제안이 거부되었습니다: ${proposal.actionCode}$reasonText",
                ),
            )
        }

        return toResponse(proposal, requester.name, approver.name)
    }

    /**
     * List all pending proposals for an approver.
     */
    fun listPendingForApprover(approverId: Long): List<ProposalResponse> {
        val proposals = proposalRepository.findByApproverIdAndStatusOrderByCreatedAtDesc(approverId, "pending")
        return proposals.map { proposal ->
            val requesterName = officerRepository.findById(proposal.requesterId).map { it.name }.orElse("(알 수 없음)")
            val approverName = officerRepository.findById(proposal.approverId).map { it.name }.orElse("(알 수 없음)")
            toResponse(proposal, requesterName, approverName)
        }
    }

    /**
     * List all proposals submitted by a requester.
     */
    fun listMyProposals(requesterId: Long): List<ProposalResponse> {
        val proposals = proposalRepository.findByRequesterIdOrderByCreatedAtDesc(requesterId)
        return proposals.map { proposal ->
            val requesterName = officerRepository.findById(proposal.requesterId).map { it.name }.orElse("(알 수 없음)")
            val approverName = officerRepository.findById(proposal.approverId).map { it.name }.orElse("(알 수 없음)")
            toResponse(proposal, requesterName, approverName)
        }
    }

    /**
     * Find all officers in the same faction who hold a position card
     * granting the required command group for the given action code.
     */
    fun findEligibleApprovers(requesterId: Long, actionCode: String): List<EligibleApproverResponse> {
        val requester = officerRepository.findById(requesterId).orElseThrow {
            IllegalArgumentException("요청자를 찾을 수 없습니다: $requesterId")
        }
        if (requester.factionId == 0L) return emptyList()

        val eligibleCards = PositionCardRegistry.getCardsForCommand(actionCode)
        if (eligibleCards.isEmpty()) return emptyList()

        val factionOfficers = officerRepository.findBySessionIdAndFactionId(requester.sessionId, requester.factionId)

        return factionOfficers
            .filter { it.id != requesterId }
            .mapNotNull { officer ->
                val officerCards = officer.getPositionCardEnums()
                val matchingCards = officerCards.filter { it in eligibleCards }
                if (matchingCards.isNotEmpty()) {
                    EligibleApproverResponse(
                        officerId = officer.id,
                        officerName = officer.name,
                        rank = officer.officerLevel.toInt(),
                        cards = matchingCards.map { it.nameKo },
                    )
                } else {
                    null
                }
            }
            .sortedByDescending { it.rank }
    }

    /**
     * Expire old pending proposals (called by tick engine or scheduled task).
     * Sets status to "expired" for pending proposals older than 24 real-time hours.
     */
    @Transactional
    fun expireOldProposals(sessionId: Long) {
        val cutoff = OffsetDateTime.now().minusHours(24)
        val pending = proposalRepository.findBySessionIdAndStatus(sessionId, "pending")
        pending.filter { it.createdAt.isBefore(cutoff) }.forEach { proposal ->
            proposal.status = "expired"
            proposal.resolvedAt = OffsetDateTime.now()
            proposalRepository.save(proposal)
        }
    }

    private fun toResponse(proposal: Proposal, requesterName: String, approverName: String): ProposalResponse {
        return ProposalResponse(
            id = proposal.id,
            requesterId = proposal.requesterId,
            requesterName = requesterName,
            approverId = proposal.approverId,
            approverName = approverName,
            actionCode = proposal.actionCode,
            args = proposal.args,
            status = proposal.status,
            reason = proposal.reason,
            createdAt = proposal.createdAt.toString(),
            resolvedAt = proposal.resolvedAt?.toString(),
        )
    }
}
