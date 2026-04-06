package com.openlogh.repository

import com.openlogh.entity.FezzanLoan
import org.springframework.data.jpa.repository.JpaRepository

interface FezzanLoanRepository : JpaRepository<FezzanLoan, Long> {

    fun findBySessionIdAndBorrowerFactionIdAndRepaidAtIsNull(
        sessionId: Long,
        borrowerFactionId: Long,
    ): List<FezzanLoan>

    fun findBySessionIdAndRepaidAtIsNull(sessionId: Long): List<FezzanLoan>

    fun findBySessionIdAndBorrowerFactionIdAndIsDefaulted(
        sessionId: Long,
        borrowerFactionId: Long,
        isDefaulted: Boolean,
    ): List<FezzanLoan>

    fun findBySessionIdAndIsDefaulted(
        sessionId: Long,
        isDefaulted: Boolean,
    ): List<FezzanLoan>
}
