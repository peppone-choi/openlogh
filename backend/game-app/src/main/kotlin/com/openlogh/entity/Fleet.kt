package com.openlogh.entity

import com.openlogh.model.UnitType
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.OffsetDateTime

@Entity
@Table(name = "fleet")
class Fleet(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(name = "session_id", nullable = false)
    var sessionId: Long = 0,

    @Column(name = "leader_officer_id", nullable = false)
    var leaderOfficerId: Long = 0,

    @Column(name = "faction_id", nullable = false)
    var factionId: Long = 0,

    @Column(nullable = false)
    var name: String = "",

    @Column(name = "unit_type", nullable = false)
    var unitType: String = "FLEET",

    @Column(name = "max_units", nullable = false)
    var maxUnits: Int = 60,

    @Column(name = "current_units", nullable = false)
    var currentUnits: Int = 0,

    @Column(name = "max_crew", nullable = false)
    var maxCrew: Int = 10,

    @Column(name = "planet_id")
    var planetId: Long? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    var meta: MutableMap<String, Any> = mutableMapOf(),

    @Column(name = "created_at", nullable = false)
    var createdAt: OffsetDateTime = OffsetDateTime.now(),
) {
    /** Returns the typed UnitType enum for this fleet's unit_type column. */
    fun getUnitTypeEnum(): UnitType = UnitType.valueOf(unitType)
}
