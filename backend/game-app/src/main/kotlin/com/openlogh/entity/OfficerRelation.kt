package com.openlogh.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.OffsetDateTime

@Entity
@Table(
    name = "officer_relation",
    uniqueConstraints = [UniqueConstraint(columnNames = ["session_id", "officer_a_id", "officer_b_id"])],
)
class OfficerRelation(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(name = "session_id", nullable = false)
    var sessionId: Long = 0,

    // Always store with officerAId < officerBId to avoid duplicates
    @Column(name = "officer_a_id", nullable = false)
    var officerAId: Long = 0,

    @Column(name = "officer_b_id", nullable = false)
    var officerBId: Long = 0,

    // 우호도 (0–100): mutual friendliness score
    @Column(name = "friendship_score", nullable = false)
    var friendshipScore: Int = 0,

    // 교류 횟수: number of interactions (회견/수렵/담화 etc.)
    @Column(name = "interaction_count", nullable = false)
    var interactionCount: Int = 0,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    var meta: MutableMap<String, Any> = mutableMapOf(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now(),
)
