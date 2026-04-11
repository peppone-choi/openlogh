package com.openlogh.engine

/**
 * Phase 24-22 (gap A11, gin7 매뉴얼 p36):
 *
 * gin7 원작은 장교의 계급이 변경되면 그 계급에 어울리는 기함으로 자동 교체된다.
 * v2.4 까지 OpenLOGH 는 [com.openlogh.service.PersonnelService] 의 promote/demote
 * 경로에서 officer.officerLevel 만 갱신하고 기함은 건드리지 않아, 대장으로 승진한
 * 장교가 여전히 위관급 쾌속함을 기함으로 들고 다니는 상태가 유지됐다.
 *
 * 본 오브젝트는 순수 함수 계층이다. 실제 LOGH 함선 카탈로그(기존 삼국지 items.json
 * 삭제 이후 공백 상태)가 채워지기 전까지 플레이스홀더 코드를 리턴한다. 코드 값 자체는
 * [com.openlogh.engine.modifier.ItemModifiers] 에서 현재 null 로 graceful degrade
 * 하므로 어떤 기존 경로도 깨지지 않는다.
 *
 * 자동 교체 정책:
 *   - 현재 flagshipCode 가 "None" 이거나 [managedCodes] 집합에 포함되면 새 tier 의
 *     기본 기함 코드로 교체한다.
 *   - 특수/로터리 보상으로 얻은 기함(여기서 관리하지 않는 코드)은 건드리지 않는다.
 *   - 만약 아무 기함도 들고 있지 않은 신참이 tier 0 으로 임관하면 CADET_FRIGATE 가
 *     기본 배정된다.
 */
object FlagshipProgression {

    /** Special sentinel: 장교가 기함이 없는 상태. Officer.kt 기본값과 일치. */
    const val NONE = "None"

    /**
     * LOGH 계급 tier → 기본 기함 코드. 8 단계 (gin7 계급 0~10) 를 LOGH 풍 명명으로 매핑.
     * 실제 함선 스펙은 후속 phase 의 items 카탈로그 확장에서 채워진다.
     */
    private val tierFlagships: Map<Int, String> = mapOf(
        0  to "CADET_FRIGATE",        // 소위 — 사관 경호위함
        1  to "CADET_FRIGATE",        // 대위 — 동일 기함 지속
        2  to "PATROL_CORVETTE",      // 소령 — 순찰 콜벳
        3  to "PATROL_CORVETTE",      // 중령 — 동일
        4  to "LIGHT_CRUISER",        // 대령 — 경순양함
        5  to "HEAVY_CRUISER",        // 준장 — 중순양함
        6  to "HEAVY_CRUISER",        // 소장 — 동일
        7  to "BATTLE_CRUISER",       // 중장 — 전투순양함
        8  to "FLAG_BATTLESHIP",      // 대장 — 기함급 전함
        9  to "COMMAND_BATTLESHIP",   // 상급대장 — 사령부 전함
        10 to "SOVEREIGN_BATTLESHIP", // 원수/총수 — 최고사령부 전함
    )

    /**
     * 본 오브젝트가 자동 교체 대상으로 인식하는 코드 집합.
     * 장교가 이 집합 밖의 값을 flagshipCode 로 들고 있으면(특수 보상/구매 기함),
     * promote/demote 는 그 값을 보존한다.
     */
    val managedCodes: Set<String> = tierFlagships.values.toSet()

    /** 특정 tier 가 배정받는 기본 기함 코드. tier 값은 0..10. */
    fun codeForTier(tier: Int): String =
        tierFlagships[tier.coerceIn(0, 10)] ?: "CADET_FRIGATE"

    /**
     * 자동 교체 결정 로직.
     *
     * @param currentCode 장교가 현재 들고 있는 flagshipCode
     * @param newTier     새 계급 (승진/강등 후)
     * @return 교체되어야 할 새 flagshipCode — 자동 교체 대상이 아니면 currentCode 그대로 반환
     */
    fun resolveOnRankChange(currentCode: String, newTier: Int): String {
        val target = codeForTier(newTier)
        // 장교가 아무 기함도 없거나 본 오브젝트가 관리하는 tier 기함 중 하나를 들고 있으면
        // 새 tier 의 기본 기함으로 교체. 그 외 (특수 보상/구매 기함) 은 그대로.
        return if (currentCode == NONE || currentCode in managedCodes) target else currentCode
    }
}
