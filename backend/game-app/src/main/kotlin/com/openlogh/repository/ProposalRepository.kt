package com.openlogh.repository

import com.openlogh.entity.Proposal
import org.springframework.data.jpa.repository.JpaRepository

interface ProposalRepository : JpaRepository<Proposal, Long> {
    fun findByApproverIdAndStatus(approverId: Long, status: String): List<Proposal>
    fun findByRequesterIdOrderByCreatedAtDesc(requesterId: Long): List<Proposal>
    fun findBySessionIdAndStatus(sessionId: Long, status: String): List<Proposal>
    fun findByApproverIdAndStatusOrderByCreatedAtDesc(approverId: Long, status: String): List<Proposal>
}
