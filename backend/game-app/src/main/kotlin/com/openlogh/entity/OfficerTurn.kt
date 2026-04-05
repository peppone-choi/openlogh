package com.openlogh.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.OffsetDateTime

@Entity
@Table(
    name = "officer_turn",
    uniqueConstraints = [UniqueConstraint(columnNames = ["officer_id", "turn_idx"])]
)
class OfficerTurn(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(name = "session_id", nullable = false)
    var sessionId: Long = 0,

    @Column(name = "officer_id", nullable = false)
    var officerId: Long = 0,

    @Column(name = "turn_idx", nullable = false)
    var turnIdx: Short = 0,

    @Column(name = "action_code", nullable = false)
    var actionCode: String = "",

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    var arg: MutableMap<String, Any> = mutableMapOf(),

    var brief: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: OffsetDateTime = OffsetDateTime.now(),
)
