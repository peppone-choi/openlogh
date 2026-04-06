package com.openlogh.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.OffsetDateTime

/**
 * Represents a coup attempt within the Empire faction.
 * Tracks the lifecycle from secret planning through resolution.
 */
@Entity
@Table(name = "coup_event")
class CoupEvent(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(name = "session_id", nullable = false)
    var sessionId: Long = 0,

    @Column(name = "faction_id", nullable = false)
    var factionId: Long = 0,

    /** Officer who initiated the coup */
    @Column(name = "leader_id", nullable = false)
    var leaderId: Long = 0,

    /** Coup lifecycle phase: PLANNING, ACTIVE, SUCCESS, FAILED, ABORTED */
    @Column(nullable = false, length = 20)
    var phase: String = "PLANNING",

    /** List of officer IDs supporting the coup */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "supporter_ids", columnDefinition = "jsonb", nullable = false)
    var supporterIds: MutableList<Long> = mutableListOf(),

    /** Current sovereign being overthrown */
    @Column(name = "target_sovereign_id", nullable = false)
    var targetSovereignId: Long = 0,

    /** Accumulated political machination points */
    @Column(name = "political_power", nullable = false)
    var politicalPower: Int = 0,

    /** Points needed to trigger coup (default 8000 per gin7) */
    @Column(nullable = false)
    var threshold: Int = 8000,

    @Column(name = "started_at", nullable = false)
    var startedAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "resolved_at")
    var resolvedAt: OffsetDateTime? = null,

    /** Result: "overthrow", "suppressed", "aborted" */
    @Column(length = 20)
    var result: String? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    var meta: MutableMap<String, Any> = mutableMapOf(),
)
