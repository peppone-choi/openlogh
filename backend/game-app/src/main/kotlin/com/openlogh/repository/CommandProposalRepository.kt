package com.openlogh.repository

import com.openlogh.entity.CommandProposal
import org.springframework.data.jpa.repository.JpaRepository

interface CommandProposalRepository : JpaRepository<CommandProposal, Long> {
    fun findBySessionIdAndStatus(sessionId: Long, status: String): List<CommandProposal>
    fun findBySessionIdAndApproverIdAndStatus(
        sessionId: Long, approverId: Long, status: String
    ): List<CommandProposal>
    fun findBySessionIdAndProposerId(sessionId: Long, proposerId: Long): List<CommandProposal>
}
