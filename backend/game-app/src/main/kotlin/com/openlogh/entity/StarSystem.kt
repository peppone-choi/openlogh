package com.openlogh.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(name = "star_system")
class StarSystem(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(name = "session_id", nullable = false)
    var sessionId: Long = 0,

    @Column(name = "map_star_id", nullable = false)
    var mapStarId: Int = 0,

    @Column(name = "name_ko", nullable = false)
    var nameKo: String = "",

    @Column(name = "name_en", nullable = false)
    var nameEn: String = "",

    @Column(name = "faction_id", nullable = false)
    var factionId: Long = 0,

    @Column(nullable = false)
    var x: Int = 0,

    @Column(nullable = false)
    var y: Int = 0,

    @Column(name = "spectral_type", nullable = false)
    var spectralType: String = "A",

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "star_rgb", columnDefinition = "jsonb", nullable = false)
    var starRgb: MutableList<Int> = mutableListOf(255, 255, 255),

    @Column(nullable = false)
    var level: Short = 5,

    @Column(nullable = false)
    var region: Short = 1,

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
)
