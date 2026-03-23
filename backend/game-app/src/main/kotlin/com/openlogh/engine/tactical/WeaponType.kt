package com.openlogh.engine.tactical

/**
 * 은하영웅전설 무기 체계.
 *
 * @param code JSON 직렬화 코드
 * @param displayName 한국어 표시명
 * @param baseDamageMultiplier 기본 데미지 배율 (함종 baseAttack에 곱)
 * @param baseRange 기본 사거리 (distance units)
 * @param armorPenetration 방어 관통 비율 (0.0 ~ 1.0, 높을수록 방어 무시)
 * @param rateOfFire 연사 배율 (1.0 기준, 높을수록 다수 히트)
 * @param interceptable 요격 가능 여부 (미사일)
 * @param energyChannel 대응 에너지 채널 ("beam" or "gun")
 */
enum class WeaponType(
    val code: String,
    val displayName: String,
    val baseDamageMultiplier: Double,
    val baseRange: Double,
    val armorPenetration: Double,
    val rateOfFire: Double,
    val interceptable: Boolean,
    val energyChannel: String,
) {
    /** 중성자 광선포: 장거리 주포, 고위력 */
    NEUTRON_BEAM(
        "neutron_beam", "중성자 광선포",
        baseDamageMultiplier = 1.0,
        baseRange = 350.0,
        armorPenetration = 0.0,
        rateOfFire = 1.0,
        interceptable = false,
        energyChannel = "beam",
    ),

    /** 레일건: 근접 실탄, 관통력 우수 */
    RAILGUN(
        "railgun", "레일건",
        baseDamageMultiplier = 0.8,
        baseRange = 150.0,
        armorPenetration = 0.4,
        rateOfFire = 1.2,
        interceptable = false,
        energyChannel = "gun",
    ),

    /** 미사일: 중거리 포화 공격, 요격 가능 */
    MISSILE(
        "missile", "미사일",
        baseDamageMultiplier = 1.2,
        baseRange = 280.0,
        armorPenetration = 0.1,
        rateOfFire = 0.8,
        interceptable = true,
        energyChannel = "gun",
    ),

    /** 레이저포: 근접 연사, 대공/함재기 요격 */
    LASER(
        "laser", "레이저포",
        baseDamageMultiplier = 0.4,
        baseRange = 120.0,
        armorPenetration = 0.0,
        rateOfFire = 3.0,
        interceptable = false,
        energyChannel = "beam",
    ),

    /** 함재기 (스파르타니안/발퀴레): 항모 전용, 근접 기동 공격 */
    FIGHTER(
        "fighter", "함재기",
        baseDamageMultiplier = 0.6,
        baseRange = 80.0,
        armorPenetration = 0.2,
        rateOfFire = 2.0,
        interceptable = true, // 레이저로 요격 가능
        energyChannel = "gun",
    ),

    /** 토르 해머: 요새 전용 초대형 중성자 광선포 */
    THOR_HAMMER(
        "thor_hammer", "토르 해머",
        baseDamageMultiplier = 5.0,
        baseRange = 500.0,
        armorPenetration = 0.8,
        rateOfFire = 0.3,
        interceptable = false,
        energyChannel = "beam",
    ),
    ;

    companion object {
        private val byCode = entries.associateBy { it.code }
        fun fromCode(code: String): WeaponType? = byCode[code]
    }
}

/**
 * 함종별 무장 탑재 구성.
 * 각 함종이 사용할 수 있는 무기 목록과 슬롯 수.
 */
data class WeaponMount(
    val weaponType: WeaponType,
    val slots: Int, // 문 수 (데미지 배율에 적용)
)

/** 함종별 기본 무장 */
fun TacticalShipClass.defaultWeapons(): List<WeaponMount> = when (this) {
    TacticalShipClass.BATTLESHIP -> listOf(
        WeaponMount(WeaponType.NEUTRON_BEAM, 6),
        WeaponMount(WeaponType.RAILGUN, 2),
        WeaponMount(WeaponType.MISSILE, 4),
        WeaponMount(WeaponType.LASER, 4),
    )
    TacticalShipClass.FAST_BATTLESHIP -> listOf(
        WeaponMount(WeaponType.NEUTRON_BEAM, 4),
        WeaponMount(WeaponType.RAILGUN, 3),
        WeaponMount(WeaponType.MISSILE, 2),
        WeaponMount(WeaponType.LASER, 4),
    )
    TacticalShipClass.CRUISER -> listOf(
        WeaponMount(WeaponType.NEUTRON_BEAM, 4),
        WeaponMount(WeaponType.LASER, 6),
        WeaponMount(WeaponType.MISSILE, 2),
    )
    TacticalShipClass.STRIKE_CRUISER -> listOf(
        WeaponMount(WeaponType.MISSILE, 12),
        WeaponMount(WeaponType.LASER, 4),
    )
    TacticalShipClass.DESTROYER -> listOf(
        WeaponMount(WeaponType.RAILGUN, 6),
        WeaponMount(WeaponType.LASER, 4),
        WeaponMount(WeaponType.MISSILE, 2),
    )
    TacticalShipClass.CARRIER -> listOf(
        WeaponMount(WeaponType.FIGHTER, 8),
        WeaponMount(WeaponType.LASER, 6),
        WeaponMount(WeaponType.NEUTRON_BEAM, 2),
    )
    TacticalShipClass.TORPEDO_CARRIER -> listOf(
        WeaponMount(WeaponType.FIGHTER, 10), // 어뢰정 = 함재기 변형
        WeaponMount(WeaponType.LASER, 4),
        WeaponMount(WeaponType.NEUTRON_BEAM, 2),
    )
    TacticalShipClass.ASSAULT_SHIP -> listOf(
        WeaponMount(WeaponType.LASER, 4),
        WeaponMount(WeaponType.MISSILE, 2),
    )
    TacticalShipClass.TRANSPORT -> listOf(
        WeaponMount(WeaponType.LASER, 2),
    )
    TacticalShipClass.ENGINEERING -> listOf(
        WeaponMount(WeaponType.LASER, 2),
    )
    TacticalShipClass.HOSPITAL -> listOf(
        WeaponMount(WeaponType.LASER, 2),
    )
    TacticalShipClass.FORTRESS -> listOf(
        WeaponMount(WeaponType.THOR_HAMMER, 1),
        WeaponMount(WeaponType.NEUTRON_BEAM, 12),
        WeaponMount(WeaponType.LASER, 20),
        WeaponMount(WeaponType.MISSILE, 8),
    )
}

/**
 * 방향에 따른 방어력 배율.
 * 은영전 설정: 전면 방어 > 측면 > 후면 (후방 장갑 취약)
 */
enum class AttackDirection(val defenseMultiplier: Double) {
    FRONT(1.0),
    SIDE(0.6),
    REAR(0.3),
}

/**
 * 공격자와 방어자의 위치와 방어자의 진행 방향을 기반으로 공격 방향 판정.
 * @param attackerPos 공격자 위치
 * @param defenderPos 방어자 위치
 * @param defenderFacingX 방어자가 향하는 방향 (최근 이동 방향)
 * @param defenderFacingY 방어자가 향하는 방향
 */
fun computeAttackDirection(
    attackerPos: Position,
    defenderPos: Position,
    defenderFacingX: Double,
    defenderFacingY: Double,
): AttackDirection {
    // 방어자→공격자 벡터
    val toAttackerX = attackerPos.x - defenderPos.x
    val toAttackerY = attackerPos.y - defenderPos.y

    // 내적으로 각도 계산 (facing과 toAttacker 사이)
    val facingLen = kotlin.math.sqrt(defenderFacingX * defenderFacingX + defenderFacingY * defenderFacingY)
    val toLen = kotlin.math.sqrt(toAttackerX * toAttackerX + toAttackerY * toAttackerY)
    if (facingLen < 0.001 || toLen < 0.001) return AttackDirection.FRONT

    val dot = (defenderFacingX * toAttackerX + defenderFacingY * toAttackerY) / (facingLen * toLen)

    return when {
        dot >= 0.5 -> AttackDirection.FRONT   // ±60° 정면
        dot <= -0.5 -> AttackDirection.REAR   // ±120° 후방
        else -> AttackDirection.SIDE          // 측면
    }
}
