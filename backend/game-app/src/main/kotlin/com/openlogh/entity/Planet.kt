package com.openlogh.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

private const val COMPAT_UNSET_LONG: Long = Long.MIN_VALUE
private const val COMPAT_UNSET_INT: Int = Int.MIN_VALUE

@Entity
@Table(name = "planet")
class Planet(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(name = "world_id", nullable = false)
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

    @Column(name = "trade_route", nullable = false)
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

    @Column(name = "garrison_set", nullable = false)
    var garrisonSet: Int = 0,

    @Column(nullable = false)
    var state: Short = 0,

    @Column(nullable = false)
    var region: Short = 0,

    @Column(nullable = false)
    var term: Short = 0,

    @Column(name = "fief_officer_id")
    var fiefOfficerId: Long? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    var conflict: MutableMap<String, Any> = mutableMapOf(),

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    var meta: MutableMap<String, Any> = mutableMapOf(),

    // === Old field name aliases (non-persisted constructor params) ===
    worldId: Long = COMPAT_UNSET_LONG,
    nationId: Long = COMPAT_UNSET_LONG,
    pop: Int = COMPAT_UNSET_INT,
    popMax: Int = COMPAT_UNSET_INT,
    agri: Int = COMPAT_UNSET_INT,
    agriMax: Int = COMPAT_UNSET_INT,
    comm: Int = COMPAT_UNSET_INT,
    commMax: Int = COMPAT_UNSET_INT,
    secu: Int = COMPAT_UNSET_INT,
    secuMax: Int = COMPAT_UNSET_INT,
    trust: Number? = null,
    def: Int = COMPAT_UNSET_INT,
    defMax: Int = COMPAT_UNSET_INT,
    wall: Int = COMPAT_UNSET_INT,
    wallMax: Int = COMPAT_UNSET_INT,
    trade: Int = COMPAT_UNSET_INT,
) {
    @Column(nullable = false)
    var approval: Float = approval.toFloat()

    @Column(nullable = false)
    var dead: Int = dead.toInt()

    init {
        if (worldId != COMPAT_UNSET_LONG) sessionId = worldId
        if (nationId != COMPAT_UNSET_LONG) factionId = nationId
        if (pop != COMPAT_UNSET_INT) population = pop
        if (popMax != COMPAT_UNSET_INT) populationMax = popMax
        if (agri != COMPAT_UNSET_INT) production = agri
        if (agriMax != COMPAT_UNSET_INT) productionMax = agriMax
        if (comm != COMPAT_UNSET_INT) commerce = comm
        if (commMax != COMPAT_UNSET_INT) commerceMax = commMax
        if (secu != COMPAT_UNSET_INT) security = secu
        if (secuMax != COMPAT_UNSET_INT) securityMax = secuMax
        if (trust != null) this.approval = trust.toFloat()
        if (def != COMPAT_UNSET_INT) orbitalDefense = def
        if (defMax != COMPAT_UNSET_INT) orbitalDefenseMax = defMax
        if (wall != COMPAT_UNSET_INT) fortress = wall
        if (wallMax != COMPAT_UNSET_INT) fortressMax = wallMax
        if (trade != COMPAT_UNSET_INT) tradeRoute = trade
    }
}
