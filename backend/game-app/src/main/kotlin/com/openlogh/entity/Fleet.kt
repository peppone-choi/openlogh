package com.openlogh.entity

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

    // === 함대 계층 구조 (Fleet Hierarchy) ===
    @Column(name = "parent_fleet_id")
    var parentFleetId: Long? = null,

    @Column(name = "sort_order", nullable = false)
    var sortOrder: Short = 0,

    @Column(nullable = false)
    var name: String = "",

    // fleet: 함대, division: 분함대, patrol: 순찰대,
    // transport: 수송함대, ground: 지상부대, garrison: 행성수비대
    @Column(name = "fleet_type", nullable = false)
    var fleetType: String = "fleet",

    @Column(name = "planet_id")
    var planetId: Long? = null,

    @Column(name = "grid_x")
    var gridX: Int? = null,

    @Column(name = "grid_y")
    var gridY: Int? = null,

    // === 기함 (Flagship) ===
    @Column(name = "flagship_code", nullable = false)
    var flagshipCode: String = "standard_battleship",

    // === 전투 함선 부대 (Combat Ship Units) ===
    @Column(nullable = false)
    var battleships: Int = 0,

    @Column(name = "fast_battleships", nullable = false)
    var fastBattleships: Int = 0,

    @Column(nullable = false)
    var cruisers: Int = 0,

    @Column(name = "strike_cruisers", nullable = false)
    var strikeCruisers: Int = 0,

    @Column(nullable = false)
    var destroyers: Int = 0,

    @Column(nullable = false)
    var carriers: Int = 0,

    /** 뇌격정모함 (제국 전용) */
    @Column(name = "torpedo_carriers", nullable = false)
    var torpedoCarriers: Int = 0,

    // === 지상 부대 (Ground Forces) ===
    @Column(name = "ground_troops", nullable = false)
    var groundTroops: Int = 0,

    @Column(name = "assault_ships", nullable = false)
    var assaultShips: Int = 0,

    // === 지원 부대 (Support Units) ===
    @Column(nullable = false)
    var transports: Int = 0,

    /** 공작함 */
    @Column(name = "engineering_ships", nullable = false)
    var engineeringShips: Int = 0,

    @Column(name = "hospital_ships", nullable = false)
    var hospitalShips: Int = 0,

    /** 항속 (연료). 워프 시 소비, 100 미만 워프 불가 */
    @Column(nullable = false)
    var fuel: Int = 1000,

    @Column(name = "fuel_max", nullable = false)
    var fuelMax: Int = 1000,

    /** 승무원 등급: elite/veteran/normal/green */
    @Column(name = "crew_grade", nullable = false)
    var crewGrade: String = "normal",

    /** 함종 서브타입 세대 (1~8) */
    @Column(name = "ship_generation", nullable = false)
    var shipGeneration: Short = 1,

    // === 함대 상태 ===
    @Column(nullable = false)
    var morale: Short = 100,

    @Column(nullable = false)
    var training: Short = 50,

    @Column(nullable = false)
    var supplies: Int = 0,

    @Column(nullable = false)
    var formation: String = "spindle",

    /** 지상전 병종 (전략 단계에서 결정, 전투 중 변경 불가) */
    @Column(name = "ground_unit_type", nullable = false)
    var groundUnitType: String = "marines",

    @Column(name = "fleet_state", nullable = false)
    var fleetState: Short = 0,

    // === 에너지 분배 (Energy Allocation, 전투 시) — 6채널 합계 100 ===
    @Column(name = "energy_beam", nullable = false)
    var energyBeam: Short = 20,

    @Column(name = "energy_gun", nullable = false)
    var energyGun: Short = 20,

    @Column(name = "energy_shield", nullable = false)
    var energyShield: Short = 20,

    @Column(name = "energy_engine", nullable = false)
    var energyEngine: Short = 20,

    @Column(name = "energy_sensor", nullable = false)
    var energySensor: Short = 10,

    // WARP: 전술 게임 철퇴(retreat) 에너지 채널. 최대값 할당 시 전술맵 원외 철퇴 가능.
    @Column(name = "energy_warp", nullable = false)
    var energyWarp: Short = 10,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    var meta: MutableMap<String, Any> = mutableMapOf(),

    @Column(name = "created_at", nullable = false)
    var createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now(),
) {
    /** Compat alias for old leaderGeneralId name */
    var leaderGeneralId: Long
        get() = leaderOfficerId
        set(value) { leaderOfficerId = value }

    /** 전투함 총 수 (기함 제외) */
    fun totalCombatShips(): Int = battleships + cruisers + destroyers + carriers

    /** 전체 함선 수 (기함 포함) */
    fun totalShips(): Int = 1 + totalCombatShips() + assaultShips + transports + hospitalShips

    /** 전투력 (함종별 가중치) */
    fun combatPower(): Int =
        battleships * 3 + cruisers * 2 + destroyers * 1 + carriers * 3 + (morale / 10)
}
