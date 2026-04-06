package com.openlogh.model

/**
 * Tactical weapon types for real-time combat.
 *
 * Beyond BEAM/GUN (energy-based), these are specialized weapon systems:
 * - Missiles: long range, consumes military supplies per missile value
 * - Fighters (전투정/스파르타니안): low direct damage, reduces target movement speed
 */
enum class TacticalWeaponType(
    val displayNameKo: String,
    val displayNameEn: String,
    val baseRange: Double,
    val baseDamage: Int,
    val cooldownTicks: Int,
    val supplyCostPerUse: Int,
    val description: String,
) {
    /** BEAM: mid-close range continuous fire. Energy-based, no supply cost. */
    BEAM(
        displayNameKo = "빔",
        displayNameEn = "Beam",
        baseRange = 3.0,
        baseDamage = 50,
        cooldownTicks = 10,
        supplyCostPerUse = 0,
        description = "중근거리 연속 사격 무기. 에너지 배분에 의해 위력 변동.",
    ),

    /** GUN: mid-close range burst. Energy-based, no supply cost. */
    GUN(
        displayNameKo = "함포",
        displayNameEn = "Gun",
        baseRange = 4.0,
        baseDamage = 70,
        cooldownTicks = 20,
        supplyCostPerUse = 0,
        description = "중근거리 단발 사격 무기. 에너지 배분에 의해 위력 변동.",
    ),

    /** MISSILE: long range, consumes supplies per missile value. */
    MISSILE(
        displayNameKo = "미사일",
        displayNameEn = "Missile",
        baseRange = 8.0,
        baseDamage = 120,
        cooldownTicks = 60,
        supplyCostPerUse = 50,
        description = "장거리 유도 미사일. 군수물자 소모.",
    ),

    /** FIGHTER: launched from carriers. Low direct damage, slows enemy movement. */
    FIGHTER(
        displayNameKo = "전투정",
        displayNameEn = "Fighter (Spartanian)",
        baseRange = 6.0,
        baseDamage = 20,
        cooldownTicks = 120,
        supplyCostPerUse = 10,
        description = "전투정 발진. 적 이동속도 감소 효과. 적 전투정 자동요격.",
    );

    companion object {
        /** Speed reduction applied to target when hit by fighters (30%) */
        const val FIGHTER_SPEED_REDUCTION = 0.3

        /** Duration of fighter speed debuff in ticks */
        const val FIGHTER_DEBUFF_DURATION_TICKS = 60

        /** Fighter auto-intercept: when targeting enemy fighters, damage is doubled */
        const val FIGHTER_INTERCEPT_DAMAGE_MULTIPLIER = 2.0
    }
}

/**
 * Represents a single missile/fighter attack event in tactical combat.
 */
data class TacticalWeaponEvent(
    val weaponType: TacticalWeaponType,
    val sourceFleetId: Long,
    val targetFleetId: Long,
    val damage: Int,
    val supplyCost: Int,
    val tick: Int,
    /** For fighters: whether this was an auto-intercept against enemy fighters */
    val isIntercept: Boolean = false,
    /** For fighters: speed reduction applied to target */
    val speedReduction: Double = 0.0,
)
