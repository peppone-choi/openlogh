package com.openlogh.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.OffsetDateTime

/**
 * Represents a seat on the Alliance Supreme Council.
 * Each seat can be held by one officer at a time.
 */
@Entity
@Table(name = "council_seat")
class CouncilSeat(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(name = "session_id", nullable = false)
    var sessionId: Long = 0,

    @Column(name = "faction_id", nullable = false)
    var factionId: Long = 0,

    @Column(name = "seat_code", nullable = false, length = 50)
    var seatCode: String = "",

    @Column(name = "officer_id")
    var officerId: Long? = null,

    @Column(name = "elected_at")
    var electedAt: OffsetDateTime? = null,

    @Column(name = "term_end_at")
    var termEndAt: OffsetDateTime? = null,

    @Column(name = "votes_received", nullable = false)
    var votesReceived: Int = 0,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    var meta: MutableMap<String, Any> = mutableMapOf(),
)
