package com.openlogh.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(name = "sovereign")
class Sovereign(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(name = "session_id", nullable = false)
    var sessionId: String = "",

    @Column(nullable = false)
    var phase: String = "",

    @Column(name = "faction_count", nullable = false)
    var factionCount: String = "",

    @Column(name = "faction_name", nullable = false)
    var factionName: String = "",

    @Column(name = "faction_hist", nullable = false)
    var factionHist: String = "",

    @Column(name = "officer_count", nullable = false)
    var officerCount: String = "",

    @Column(name = "personal_hist", nullable = false)
    var personalHist: String = "",

    @Column(name = "special_hist", nullable = false)
    var specialHist: String = "",

    @Column(nullable = false)
    var name: String = "",

    @Column(nullable = false)
    var type: String = "",

    @Column(nullable = false)
    var color: String = "",

    @Column(nullable = false)
    var year: Short = 0,

    @Column(nullable = false)
    var month: Short = 0,

    @Column(nullable = false)
    var power: Int = 0,

    @Column(name = "officer_num", nullable = false)
    var officerNum: Int = 0,

    @Column(name = "planet_num", nullable = false)
    var planetNum: Int = 0,

    @Column(nullable = false)
    var population: String = "",

    @Column(name = "population_rate", nullable = false)
    var populationRate: String = "",

    @Column(nullable = false)
    var funds: Int = 0,

    @Column(nullable = false)
    var supplies: Int = 0,

    @Column(nullable = false)
    var l12name: String = "",

    @Column(nullable = false)
    var l12pic: String = "",

    @Column(nullable = false)
    var l11name: String = "",

    @Column(nullable = false)
    var l11pic: String = "",

    @Column(nullable = false)
    var l10name: String = "",

    @Column(nullable = false)
    var l10pic: String = "",

    @Column(nullable = false)
    var l9name: String = "",

    @Column(nullable = false)
    var l9pic: String = "",

    @Column(nullable = false)
    var l8name: String = "",

    @Column(nullable = false)
    var l8pic: String = "",

    @Column(nullable = false)
    var l7name: String = "",

    @Column(nullable = false)
    var l7pic: String = "",

    @Column(nullable = false)
    var l6name: String = "",

    @Column(nullable = false)
    var l6pic: String = "",

    @Column(nullable = false)
    var l5name: String = "",

    @Column(nullable = false)
    var l5pic: String = "",

    @Column(nullable = false)
    var tiger: String = "",

    @Column(nullable = false)
    var eagle: String = "",

    @Column(nullable = false)
    var gen: String = "",

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    var history: List<String> = emptyList(),

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    var aux: MutableMap<String, Any> = mutableMapOf(),
)
