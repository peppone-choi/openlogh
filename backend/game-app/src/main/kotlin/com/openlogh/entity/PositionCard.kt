package com.openlogh.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.OffsetDateTime

/**
 * 직무카드 (Position Card)
 *
 * Represents a special role/position held by an officer within a session.
 * Some position cards (봉토카드) persist through promotions/demotions.
 *
 * positionType examples:
 *   military   - fleet_commander, vice_commander, chief_of_staff, staff_officer, adjutant
 *   political  - prime_minister, interior_minister, justice_minister, customs_minister
 *   academy    - academy_commandant, instructor
 *   military_police - provost_marshal (헌병총감), mp_commander (헌병사령관)
 *   fief       - fief_lord (봉토카드, Empire only, survives rank changes)
 */
@Entity
@Table(name = "position_card")
class PositionCard(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(name = "officer_id", nullable = false)
    var officerId: Long = 0,

    @Column(name = "session_id", nullable = false)
    var sessionId: Long = 0,

    // Category: military, political, academy, military_police, fief
    @Column(name = "position_type", nullable = false)
    var positionType: String = "",

    // Human-readable Korean name (e.g. "함대 사령관", "봉토 영주")
    @Column(name = "position_name_ko", nullable = false)
    var positionNameKo: String = "",

    @Column(name = "granted_at", nullable = false)
    var grantedAt: OffsetDateTime = OffsetDateTime.now(),

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    var meta: MutableMap<String, Any> = mutableMapOf(),
)
