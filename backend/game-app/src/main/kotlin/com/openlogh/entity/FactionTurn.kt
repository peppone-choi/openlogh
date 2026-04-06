package com.openlogh.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.OffsetDateTime

/**
 * DEPRECATED: Legacy turn-based queue entity.
 * Not used in real-time mode — commands execute immediately with cooldowns.
 * Kept for Hibernate schema validation only.
 */
@Entity
@Table(
    name = "faction_turn",
    uniqueConstraints = [UniqueConstraint(columnNames = ["faction_id", "officer_level", "turn_idx"])]
)
class FactionTurn(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(name = "session_id", nullable = false)
    var sessionId: Long = 0,

    @Column(name = "faction_id", nullable = false)
    var factionId: Long = 0,

    @Column(name = "officer_level", nullable = false)
    var officerLevel: Short = 0,

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
