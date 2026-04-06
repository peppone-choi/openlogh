package com.openlogh.entity

import com.openlogh.model.CrewProficiency
import com.openlogh.model.ShipClass
import com.openlogh.model.ShipSubtype
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

/**
 * 함선 유닛 — 300척 단위 부대.
 * Fleet 내 슬롯(0~7번)에 배속되며, 함종과 서브타입으로 전투 스탯이 결정된다.
 * 스탯은 ShipStatRegistry에서 로드 후 갱신된다 (Plan 05).
 */
@Entity
@Table(
    name = "ship_unit",
    indexes = [
        Index(name = "idx_ship_unit_fleet_id", columnList = "fleet_id"),
        Index(name = "idx_ship_unit_session_id", columnList = "session_id"),
    ]
)
class ShipUnit(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(name = "session_id", nullable = false)
    var sessionId: Long = 0,

    /** 소속 Fleet ID (fleet.id FK) */
    @Column(name = "fleet_id", nullable = false)
    var fleetId: Long = 0,

    /** 함대 내 부대 번호 (0~7). 0번이 사령관 직할 부대. */
    @Column(name = "slot_index", nullable = false)
    var slotIndex: Int = 0,

    // ── 함종 정보 ──────────────────────────────────────────────
    /** 함종 (ShipClass enum의 name). 예: "BATTLESHIP" */
    @Column(name = "ship_class", nullable = false, length = 32)
    var shipClass: String = ShipClass.BATTLESHIP.name,

    /** 서브타입 (ShipSubtype enum의 name). 예: "BATTLESHIP_I" */
    @Column(name = "ship_subtype", nullable = false, length = 32)
    var shipSubtype: String = ShipSubtype.BATTLESHIP_I.name,

    // ── 함선 수 ────────────────────────────────────────────────
    /** 현재 함선 수 (전투 피해로 감소). 최대 300. */
    @Column(name = "ship_count", nullable = false)
    var shipCount: Int = 0,

    @Column(name = "max_ship_count", nullable = false)
    var maxShipCount: Int = 300,

    // ── 기본 전투 수치 (ShipStatRegistry 로드 후 갱신) ────────
    @Column(nullable = false) var armor: Int = 0,
    @Column(nullable = false) var shield: Int = 0,
    @Column(name = "weapon_power", nullable = false) var weaponPower: Int = 0,
    @Column(nullable = false) var speed: Int = 0,
    @Column(name = "crew_capacity", nullable = false) var crewCapacity: Int = 0,
    @Column(name = "supply_capacity", nullable = false) var supplyCapacity: Int = 0,

    // ── 상태 ───────────────────────────────────────────────────
    @Column(nullable = false) var morale: Short = 50,
    @Column(nullable = false) var training: Short = 50,

    /** 미사일 재고 (소모형 무기). 0이 되면 미사일 발사 불가. */
    @Column(name = "missile_stock", nullable = false)
    var missileStock: Int = 100,

    /** 부대 자세: NAVIGATION / DOCK / CRUISE / COMBAT */
    @Column(nullable = false, length = 16)
    var stance: String = "CRUISE",

    /** 승조원 수련도 (CrewProficiency enum의 name). */
    @Column(name = "crew_proficiency", nullable = false, length = 16)
    var crewProficiency: String = CrewProficiency.GREEN.name,

    // ── 기함 정보 ──────────────────────────────────────────────
    /** 이 유닛이 기함 유닛이면 true. */
    @Column(name = "is_flagship", nullable = false)
    var isFlagship: Boolean = false,

    /** 고유 기함 코드 (빌헬미나 등). 기함이 아니면 빈 문자열. */
    @Column(name = "flagship_code", nullable = false, length = 64)
    var flagshipCode: String = "",

    // ── 지상부대 탑재 ──────────────────────────────────────────
    /** 탑재된 지상부대 타입 코드 (GroundUnitType). 없으면 빈 문자열. */
    @Column(name = "ground_unit_type", nullable = false, length = 32)
    var groundUnitType: String = "",

    /** 탑재된 지상부대 수. */
    @Column(name = "ground_unit_count", nullable = false)
    var groundUnitCount: Int = 0,

    // ── 유연 필드 ──────────────────────────────────────────────
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    var meta: MutableMap<String, Any> = mutableMapOf(),
) {
    /** ShipSubtype enum 조회 편의 메서드 */
    fun resolveSubtype(): ShipSubtype? = runCatching { ShipSubtype.valueOf(shipSubtype) }.getOrNull()

    /** CrewProficiency enum 조회 편의 메서드 */
    fun resolveProficiency(): CrewProficiency = runCatching { CrewProficiency.valueOf(crewProficiency) }.getOrElse { CrewProficiency.GREEN }

    /** 전투 유효 전투력 (수련도 승수 적용) */
    fun effectiveCombatPower(): Double = shipCount * resolveProficiency().combatMultiplier
}
