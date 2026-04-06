package com.openlogh.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.OffsetDateTime

/**
 * Represents a loan from Fezzan to a player faction.
 * Interest accrues over time; default triggers escalating penalties.
 */
@Entity
@Table(name = "fezzan_loan")
class FezzanLoan(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(name = "session_id", nullable = false)
    var sessionId: Long = 0,

    @Column(name = "borrower_faction_id", nullable = false)
    var borrowerFactionId: Long = 0,

    @Column(nullable = false)
    var principal: Int = 0,

    @Column(name = "interest_rate", nullable = false)
    var interestRate: Float = 0.05f,

    @Column(name = "remaining_debt", nullable = false)
    var remainingDebt: Int = 0,

    @Column(name = "issued_at", nullable = false)
    var issuedAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "due_at", nullable = false)
    var dueAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "repaid_at")
    var repaidAt: OffsetDateTime? = null,

    @Column(name = "is_defaulted", nullable = false)
    var isDefaulted: Boolean = false,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    var meta: MutableMap<String, Any> = mutableMapOf(),
)
