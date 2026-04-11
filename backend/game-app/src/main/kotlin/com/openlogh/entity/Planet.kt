package com.openlogh.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(name = "planet")
class Planet(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(name = "session_id", nullable = false)
    var sessionId: Long = 0,

    @Column(nullable = false)
    var name: String = "",

    @Column(name = "map_planet_id", nullable = false)
    var mapPlanetId: Int = 0,

    @Column(nullable = false)
    var level: Short = 0,

    @Column(name = "faction_id", nullable = false)
    var factionId: Long = 0,

    @Column(name = "supply_state", nullable = false)
    var supplyState: Short = 1,

    @Column(name = "front_state", nullable = false)
    var frontState: Short = 0,

    @Column(nullable = false)
    var population: Int = 0,

    @Column(name = "population_max", nullable = false)
    var populationMax: Int = 0,

    @Column(nullable = false)
    var production: Int = 0,

    @Column(name = "production_max", nullable = false)
    var productionMax: Int = 0,

    @Column(nullable = false)
    var commerce: Int = 0,

    @Column(name = "commerce_max", nullable = false)
    var commerceMax: Int = 0,

    @Column(nullable = false)
    var security: Int = 0,

    @Column(name = "security_max", nullable = false)
    var securityMax: Int = 0,

    approval: Number = 0,

    @Column(nullable = false)
    var tradeRoute: Int = 100,

    dead: Number = 0,

    @Column(name = "orbital_defense", nullable = false)
    var orbitalDefense: Int = 0,

    @Column(name = "orbital_defense_max", nullable = false)
    var orbitalDefenseMax: Int = 0,

    @Column(nullable = false)
    var fortress: Int = 0,

    @Column(name = "fortress_max", nullable = false)
    var fortressMax: Int = 0,

    @Column(name = "officer_set", nullable = false)
    var officerSet: Int = 0,

    @Column(nullable = false)
    var state: Short = 0,

    @Column(nullable = false)
    var region: Short = 0,

    @Column(nullable = false)
    var term: Short = 0,

    @Column(name = "star_system_id")
    var starSystemId: Long? = null,

    @Column(name = "fortress_type", nullable = false)
    var fortressType: String = "NONE",

    @Column(name = "fortress_gun_power", nullable = false)
    var fortressGunPower: Int = 0,

    @Column(name = "fortress_gun_range", nullable = false)
    var fortressGunRange: Int = 0,

    @Column(name = "fortress_gun_cooldown", nullable = false)
    var fortressGunCooldown: Int = 0,

    @Column(name = "garrison_capacity", nullable = false)
    var garrisonCapacity: Int = 0,

    /**
     * Phase 24-12 (gap A6/C3): planet terrain classification that gates which
     * ground units can be deployed during planetary ground combat.
     *
     * Values (gin7 manual p50):
     *   - "normal"   : 세 병종 모두 (장갑병 / 장갑유탄병 / 경장육전병)
     *   - "gas"      : 장갑유탄병 + 경장육전병만 (장갑병 배치 불가)
     *   - "fortress" : 장갑유탄병 + 경장육전병만 (요새 침공 제한)
     *
     * Consumed by GroundBattleEngine.addAttackers / initDefenders which
     * filter out disallowed unit types before they enter the 30-unit box.
     */
    @Column(name = "planet_type", nullable = false)
    var planetType: String = "normal",

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    var conflict: MutableMap<String, Any> = mutableMapOf(),

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    var meta: MutableMap<String, Any> = mutableMapOf(),
) {
    @Column(nullable = false)
    var approval: Float = approval.toFloat()

    @Column(nullable = false)
    var dead: Int = dead.toInt()
}
