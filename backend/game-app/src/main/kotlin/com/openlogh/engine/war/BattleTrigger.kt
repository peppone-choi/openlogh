package com.openlogh.engine.war

/**
 * BattleTrigger — stub placeholder.
 *
 * TODO Phase 3: 삼국지 병종 전투 트리거 제거됨. gin7 전술전 트리거 시스템으로 대체 예정.
 * 삼국지 trigger/ 서브패키지(BattleHealTrigger, RageTrigger 등) 전량 삭제됨.
 */
interface BattleTrigger {
    val triggerKey: String
}

/**
 * BattleTriggerRegistry — stub placeholder.
 *
 * TODO Phase 3: 삼국지 전투 트리거 레지스트리 제거됨. gin7 TacticalBattleEngine 트리거로 대체 예정.
 */
object BattleTriggerRegistry {
    fun get(key: String): BattleTrigger? = null
}
