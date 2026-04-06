package com.openlogh.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.OffsetDateTime

/**
 * Represents a single tactical (RTS) battle instance.
 * Battle state is stored as JSONB for flexibility and real-time tick updates.
 */
@Entity
@Table(name = "tactical_battle")
class TacticalBattle(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(name = "session_id", nullable = false)
    var sessionId: Long = 0,

    @Column(name = "star_system_id", nullable = false)
    var starSystemId: Long = 0,

    @Column(name = "attacker_faction_id", nullable = false)
    var attackerFactionId: Long = 0,

    @Column(name = "defender_faction_id", nullable = false)
    var defenderFactionId: Long = 0,

    /** Battle lifecycle phase: PREPARING, ACTIVE, PAUSED, ENDED */
    @Column(nullable = false)
    var phase: String = "PREPARING",

    @Column(name = "started_at", nullable = false)
    var startedAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "ended_at")
    var endedAt: OffsetDateTime? = null,

    /** Fleet IDs participating, keyed by side: {"attackers": [1,2], "defenders": [3,4]} */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    var participants: MutableMap<String, Any> = mutableMapOf(),

    /** Full tactical battle state snapshot (units, positions, energy, etc.) */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "battle_state", columnDefinition = "jsonb", nullable = false)
    var battleState: MutableMap<String, Any> = mutableMapOf(),

    /** Battle result: "attacker_win", "defender_win", "draw", null if ongoing */
    @Column
    var result: String? = null,

    /** Number of battle ticks elapsed */
    @Column(name = "tick_count", nullable = false)
    var tickCount: Int = 0,
)
