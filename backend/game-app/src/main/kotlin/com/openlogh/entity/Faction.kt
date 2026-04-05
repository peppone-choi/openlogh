package com.openlogh.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.OffsetDateTime

@Entity
@Table(name = "faction")
class Faction(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(name = "session_id", nullable = false)
    var sessionId: Long = 0,

    @Column(nullable = false)
    var name: String = "",

    @Column(nullable = false, length = 2)
    var abbreviation: String = "",

    @Column(nullable = false)
    var color: String = "",

    @Column(name = "capital_planet_id")
    var capitalPlanetId: Long? = null,

    @Column(nullable = false)
    var funds: Int = 0,

    @Column(nullable = false)
    var supplies: Int = 0,

    @Column(name = "tax_rate", nullable = false)
    var taxRate: Short = 100,

    @Column(name = "conscription_rate", nullable = false)
    var conscriptionRate: Short = 15,

    @Column(name = "conscription_rate_tmp", nullable = false)
    var conscriptionRateTmp: Short = 15,

    @Column(name = "secret_limit", nullable = false)
    var secretLimit: Short = 3,

    @Column(name = "chief_officer_id", nullable = false)
    var chiefOfficerId: Long = 0,

    @Column(name = "scout_level", nullable = false)
    var scoutLevel: Short = 0,

    @Column(name = "war_state", nullable = false)
    var warState: Short = 0,

    @Column(name = "strategic_cmd_limit", nullable = false)
    var strategicCmdLimit: Short = 36,

    @Column(name = "surrender_limit", nullable = false)
    var surrenderLimit: Short = 72,

    @Column(name = "tech_level", nullable = false)
    var techLevel: Float = 0f,

    @Column(name = "military_power", nullable = false)
    var militaryPower: Int = 0,

    @Column(name = "officer_count", nullable = false)
    var officerCount: Int = 0,

    @Column(name = "faction_rank", nullable = false)
    var factionRank: Short = 0,

    @Column(name = "faction_type", nullable = false)
    var factionType: String = "che_neutral",

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    var spy: MutableMap<String, Any> = mutableMapOf(),

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    var meta: MutableMap<String, Any> = mutableMapOf(),

    @Column(name = "created_at", nullable = false)
    var createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now(),
)
