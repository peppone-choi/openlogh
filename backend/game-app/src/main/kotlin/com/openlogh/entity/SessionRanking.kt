package com.openlogh.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.OffsetDateTime

@Entity
@Table(name = "session_ranking")
class SessionRanking(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(name = "session_id", nullable = false)
    var sessionId: Long = 0,

    @Column(name = "officer_id", nullable = false)
    var officerId: Long = 0,

    @Column(name = "officer_name", nullable = false)
    var officerName: String = "",

    @Column(name = "faction_id", nullable = false)
    var factionId: Long = 0,

    @Column(name = "final_rank", nullable = false)
    var finalRank: Int = 0,

    @Column(nullable = false)
    var score: Int = 0,

    @Column(name = "merit_points", nullable = false)
    var meritPoints: Int = 0,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    var stats: MutableMap<String, Any> = mutableMapOf(),

    @Column(name = "created_at", nullable = false)
    var createdAt: OffsetDateTime = OffsetDateTime.now(),
)
