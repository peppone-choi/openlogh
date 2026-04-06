package com.openlogh.model

/**
 * Lifecycle phases of a tactical battle instance.
 */
enum class BattlePhase(val displayNameKo: String) {
    /** Battle is being set up, units positioning */
    PREPARING("준비 중"),

    /** Battle is actively in progress */
    ACTIVE("교전 중"),

    /** Battle is temporarily paused (e.g., ceasefire negotiation) */
    PAUSED("일시정지"),

    /** Battle has ended with a result */
    ENDED("종료");

    val isActive: Boolean get() = this == ACTIVE
    val isOver: Boolean get() = this == ENDED
}
