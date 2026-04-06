package com.openlogh.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.OffsetDateTime

/**
 * Represents an election in the Alliance faction.
 * Tracks candidates, votes, and results.
 */
@Entity
@Table(name = "election")
class Election(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(name = "session_id", nullable = false)
    var sessionId: Long = 0,

    @Column(name = "faction_id", nullable = false)
    var factionId: Long = 0,

    @Column(name = "election_type", nullable = false, length = 30)
    var electionType: String = "",

    @Column(name = "started_at", nullable = false)
    var startedAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "ended_at")
    var endedAt: OffsetDateTime? = null,

    /** List of candidate officer IDs: [{"officerId": 1, "name": "..."}, ...] */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    var candidates: MutableList<Map<String, Any>> = mutableListOf(),

    /** Vote map: {"voterId": "candidateId", ...} */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    var votes: MutableMap<String, Any> = mutableMapOf(),

    @Column(name = "winner_officer_id")
    var winnerOfficerId: Long? = null,

    @Column(name = "is_completed", nullable = false)
    var isCompleted: Boolean = false,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    var meta: MutableMap<String, Any> = mutableMapOf(),
)
