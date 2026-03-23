package com.openlogh.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.OffsetDateTime

/**
 * 전술 전투 기록. 전투 종료 시 저장.
 * 요약 정보 + 전체 이벤트 로그(JSON)를 영구 보관.
 */
@Entity
@Table(name = "battle_record")
class BattleRecord(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(name = "session_id", nullable = false)
    var sessionId: Long = 0,

    @Column(name = "session_code", nullable = false)
    var sessionCode: String = "",

    @Column(name = "planet_id", nullable = false)
    var planetId: Long = 0,

    @Column(name = "planet_name", nullable = false)
    var planetName: String = "",

    // === 공격측 ===
    @Column(name = "attacker_faction_id", nullable = false)
    var attackerFactionId: Long = 0,

    @Column(name = "attacker_faction_name", nullable = false)
    var attackerFactionName: String = "",

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attacker_officers", columnDefinition = "jsonb", nullable = false)
    var attackerOfficers: List<Map<String, Any>> = emptyList(),

    // === 방어측 ===
    @Column(name = "defender_faction_id", nullable = false)
    var defenderFactionId: Long = 0,

    @Column(name = "defender_faction_name", nullable = false)
    var defenderFactionName: String = "",

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "defender_officers", columnDefinition = "jsonb", nullable = false)
    var defenderOfficers: List<Map<String, Any>> = emptyList(),

    // === 결과 ===
    @Column(name = "winner_faction_id", nullable = false)
    var winnerFactionId: Long = 0,

    @Column(name = "victory_type", nullable = false)
    var victoryType: String = "",

    @Column(name = "total_turns", nullable = false)
    var totalTurns: Int = 0,

    @Column(name = "attacker_won", nullable = false)
    var attackerWon: Boolean = false,

    @Column(name = "planet_captured", nullable = false)
    var planetCaptured: Boolean = false,

    // === 전투 통계 ===
    @Column(name = "attacker_ships_lost", nullable = false)
    var attackerShipsLost: Int = 0,

    @Column(name = "defender_ships_lost", nullable = false)
    var defenderShipsLost: Int = 0,

    @Column(name = "attacker_ships_initial", nullable = false)
    var attackerShipsInitial: Int = 0,

    @Column(name = "defender_ships_initial", nullable = false)
    var defenderShipsInitial: Int = 0,

    // === 전체 이벤트 로그 (리플레이용) ===
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "battle_log", columnDefinition = "jsonb", nullable = false)
    var battleLog: List<Map<String, Any>> = emptyList(),

    // === 초기 배치 스냅샷 (리플레이용) ===
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "initial_state", columnDefinition = "jsonb", nullable = false)
    var initialState: Map<String, Any> = emptyMap(),

    @Column(name = "started_at", nullable = false)
    var startedAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "ended_at", nullable = false)
    var endedAt: OffsetDateTime = OffsetDateTime.now(),
)
