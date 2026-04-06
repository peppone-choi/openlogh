package com.openlogh.service

import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandExecutor
import com.openlogh.entity.CommandProposal
import com.openlogh.repository.CommandProposalRepository
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.SessionStateRepository
import org.springframework.stereotype.Service
import java.time.OffsetDateTime

@Service
class CommandProposalService(
    private val proposalRepository: CommandProposalRepository,
    private val officerRepository: OfficerRepository,
    private val commandExecutor: CommandExecutor,
    private val gameEventService: GameEventService,
    private val sessionStateRepository: SessionStateRepository,
) {
    /**
     * 하급자가 상급자에게 커맨드 실행을 제안한다.
     * approverId는 proposer의 officerLevel보다 높아야 한다.
     */
    fun createProposal(
        sessionId: Long,
        proposerId: Long,
        approverId: Long,
        commandCode: String,
        args: Map<String, Any>,
    ): CommandProposal {
        val proposer = officerRepository.findById(proposerId).orElseThrow {
            IllegalArgumentException("제안자 장교 미존재: $proposerId")
        }
        val approver = officerRepository.findById(approverId).orElseThrow {
            IllegalArgumentException("승인자 장교 미존재: $approverId")
        }
        require(proposer.officerLevel < approver.officerLevel) {
            "제안자(${proposer.officerLevel}계급)는 승인자(${approver.officerLevel}계급)보다 하급이어야 합니다."
        }
        val proposal = CommandProposal(
            sessionId = sessionId,
            proposerId = proposerId,
            approverId = approverId,
            commandCode = commandCode,
            args = args.toMutableMap(),
            status = "PENDING",
        )
        return proposalRepository.save(proposal)
    }

    /**
     * 상급자가 제안을 승인하고 커맨드를 실행한다.
     */
    suspend fun approveProposal(sessionId: Long, proposalId: Long, approverId: Long): CommandProposal {
        val proposal = proposalRepository.findById(proposalId).orElseThrow {
            IllegalArgumentException("제안 미존재: $proposalId")
        }
        require(proposal.sessionId == sessionId) { "세션 불일치" }
        require(proposal.approverId == approverId) { "승인 권한 없음" }
        require(proposal.status == "PENDING") { "이미 처리된 제안입니다." }

        val proposer = officerRepository.findById(proposal.proposerId).orElseThrow {
            IllegalArgumentException("제안자 미존재")
        }
        val session = sessionStateRepository.findById(sessionId.toShort()).orElseThrow {
            IllegalArgumentException("세션 미존재: $sessionId")
        }
        val env = CommandEnv(
            sessionId = sessionId,
            year = session.currentYear.toInt(),
            month = session.currentMonth.toInt(),
            startYear = session.currentYear.toInt(),
            realtimeMode = session.realtimeMode,
            gameStor = mutableMapOf(),
        )

        val result = commandExecutor.executeOfficerCommand(
            actionCode = proposal.commandCode,
            general = proposer,
            env = env,
            arg = proposal.args,
        )

        proposal.status = if (result.success) "APPROVED" else "REJECTED"
        proposal.resolvedAt = OffsetDateTime.now()
        proposal.resultLog = result.logs.joinToString("\n")

        val saved = proposalRepository.save(proposal)

        // WebSocket 브로드캐스트
        gameEventService.broadcastCommand(
            sessionId,
            proposal.proposerId,
            mapOf(
                "type" to "PROPOSAL_RESULT",
                "proposalId" to saved.id,
                "status" to saved.status,
                "commandCode" to saved.commandCode,
                "logs" to result.logs,
                "success" to result.success,
            )
        )

        return saved
    }

    /**
     * 상급자가 제안을 거부한다.
     */
    fun rejectProposal(sessionId: Long, proposalId: Long, approverId: Long): CommandProposal {
        val proposal = proposalRepository.findById(proposalId).orElseThrow {
            IllegalArgumentException("제안 미존재: $proposalId")
        }
        require(proposal.sessionId == sessionId) { "세션 불일치" }
        require(proposal.approverId == approverId) { "거부 권한 없음" }
        require(proposal.status == "PENDING") { "이미 처리된 제안입니다." }

        proposal.status = "REJECTED"
        proposal.resolvedAt = OffsetDateTime.now()
        return proposalRepository.save(proposal)
    }

    fun getPendingProposals(sessionId: Long, approverId: Long): List<CommandProposal> =
        proposalRepository.findBySessionIdAndApproverIdAndStatus(sessionId, approverId, "PENDING")

    fun getMyProposals(sessionId: Long, proposerId: Long): List<CommandProposal> =
        proposalRepository.findBySessionIdAndProposerId(sessionId, proposerId)
}
