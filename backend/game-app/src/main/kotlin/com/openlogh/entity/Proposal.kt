package com.openlogh.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.OffsetDateTime

/**
 * Proposal entity for the suggestion/proposal system.
 *
 * A lower-rank officer (requester) can propose a command to a superior (approver)
 * who holds the required position card. The approver can then approve or reject the proposal.
 * On approval, the command is executed using the approver's authority but the CP cost
 * is deducted from the requester.
 */
@Entity
@Table(name = "proposal")
class Proposal(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(name = "session_id", nullable = false)
    var sessionId: Long = 0,

    @Column(name = "requester_id", nullable = false)
    var requesterId: Long = 0,

    @Column(name = "approver_id", nullable = false)
    var approverId: Long = 0,

    @Column(name = "action_code", nullable = false)
    var actionCode: String = "",

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    var args: MutableMap<String, Any> = mutableMapOf(),

    @Column(nullable = false)
    var status: String = "pending",

    var reason: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "resolved_at")
    var resolvedAt: OffsetDateTime? = null,
)
