package com.openlogh.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.OffsetDateTime

private const val COMPAT_UNSET_LONG: Long = Long.MIN_VALUE
private const val COMPAT_UNSET_INT: Int = Int.MIN_VALUE
private const val COMPAT_UNSET_SHORT: Short = Short.MIN_VALUE

@Entity
@Table(name = "faction")
class Faction(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(name = "world_id", nullable = false)
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
    var taxRate: Short = 0,

    @Column(name = "conscription_rate", nullable = false)
    var conscriptionRate: Short = 0,

    @Column(name = "conscription_rate_tmp", nullable = false)
    var conscriptionRateTmp: Short = 0,

    @Column(name = "secret_limit", nullable = false)
    var secretLimit: Short = 3,

    @Column(name = "supreme_commander_id", nullable = false)
    var supremeCommanderId: Long = 0,

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
    var factionType: String = "empire",

    @Column(name = "parent_faction_id")
    var parentFactionId: Long? = null,

    @Column(name = "secession_type", nullable = false)
    var secessionType: String = "none",

    @Column(name = "secession_state", nullable = false)
    var secessionState: String = "none",

    @Column(name = "secession_leader_id")
    var secessionLeaderId: Long? = null,

    @Column(name = "diplomacy_enabled", nullable = false)
    var diplomacyEnabled: Boolean = true,

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

    // === Old field name aliases (non-persisted constructor params) ===
    worldId: Long = COMPAT_UNSET_LONG,
    capitalCityId: Long? = null,
    gold: Int = COMPAT_UNSET_INT,
    rice: Int = COMPAT_UNSET_INT,
    level: Short = COMPAT_UNSET_SHORT,
    gennum: Int = COMPAT_UNSET_INT,
    chiefGeneralId: Long = COMPAT_UNSET_LONG,
    typeCode: String? = null,
    tech: Float? = null,
) {
    init {
        if (worldId != COMPAT_UNSET_LONG) sessionId = worldId
        if (capitalCityId != null) capitalPlanetId = capitalCityId
        if (gold != COMPAT_UNSET_INT) funds = gold
        if (rice != COMPAT_UNSET_INT) supplies = rice
        if (level != COMPAT_UNSET_SHORT) factionRank = level
        if (gennum != COMPAT_UNSET_INT) officerCount = gennum
        if (chiefGeneralId != COMPAT_UNSET_LONG) supremeCommanderId = chiefGeneralId
        if (typeCode != null) factionType = typeCode
        if (tech != null) techLevel = tech
    }
}
