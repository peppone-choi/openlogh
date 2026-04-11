package com.openlogh.model

/**
 * Ground combat unit types (지상전 병종).
 *
 * Based on gin7 manual:
 * - Normal planet: all three common types available (producible)
 * - Gas planet: GRENADIER and LIGHT_INFANTRY only
 * - Fortress: GRENADIER and LIGHT_INFANTRY only
 *
 * Phase 24-24 (gap A8, gin7 매뉴얼 p100 "생산 불가 단위"):
 * 원작 매뉴얼 p100 은 세 가지 **생산 불가 특수 병종**을 별도 표기한다 —
 * 근위사단(은하제국 황제 친위대), 척탄병교도대(엘리트 훈련대), 장미기사단
 * (자유행성동맹 특수 습격단 — 로젠리터). 이들은 커맨드로 징집되지 않고
 * 시나리오 프리셋 또는 특수 이벤트를 통해만 배치된다. v2.4 까지는 enum 에
 * 존재조차 하지 않아, 시나리오 seed 나 특수 이벤트에서 "로젠리터" 같은
 * 타입 문자열을 참조할 때 역매핑이 불가능했다.
 */
enum class GroundUnitType(
    val displayNameKo: String,
    val attack: Int,
    val defense: Int,
    val speed: Int,
    val allowedOnGas: Boolean,
    val allowedOnFortress: Boolean,
    /** 플레이어/AI 의 일반 생산 커맨드로 만들 수 있는가. false = 시나리오/이벤트 전용. */
    val producible: Boolean = true,
    /** 특정 진영 전용인가. null = 양 진영 사용 가능. */
    val factionRestricted: String? = null,
) {
    /** 장갑병 - Armored infantry. Heavy ground unit, normal planets only. */
    ARMORED_INFANTRY(
        displayNameKo = "장갑병",
        attack = 200,
        defense = 250,
        speed = 3,
        allowedOnGas = false,
        allowedOnFortress = false,
    ),

    /** 장갑유탄병 - Armored grenadier. Medium ground unit, all terrain. */
    GRENADIER(
        displayNameKo = "장갑유탄병",
        attack = 180,
        defense = 180,
        speed = 5,
        allowedOnGas = true,
        allowedOnFortress = true,
    ),

    /** 경장육전병 - Light infantry. Fast ground unit, all terrain. */
    LIGHT_INFANTRY(
        displayNameKo = "경장육전병",
        attack = 120,
        defense = 100,
        speed = 8,
        allowedOnGas = true,
        allowedOnFortress = true,
    ),

    /**
     * 근위사단 — 은하제국 황제 친위대. 커맨드 생산 불가, 수도 방위 프리셋 전용.
     * 공격·방어 모두 최상위, 기동 중간 (장갑병보다 빠름).
     */
    IMPERIAL_GUARD(
        displayNameKo = "근위사단",
        attack = 260,
        defense = 300,
        speed = 4,
        allowedOnGas = false,
        allowedOnFortress = true,
        producible = false,
        factionRestricted = "empire",
    ),

    /**
     * 척탄병교도대 — 은하제국 엘리트 훈련 지휘 단위. 커맨드 생산 불가, 특수 이벤트/시나리오 전용.
     * 장갑유탄병 스탯의 1.5 배수준.
     */
    GRENADIER_GUARD(
        displayNameKo = "척탄병교도대",
        attack = 260,
        defense = 240,
        speed = 5,
        allowedOnGas = true,
        allowedOnFortress = true,
        producible = false,
        factionRestricted = "empire",
    ),

    /**
     * 장미기사단 — 자유행성동맹 특수 습격 단위 (원작 Rosenritter). 커맨드 생산 불가,
     * 특수 시나리오/함대 편성 프리셋 전용. 속도·공격 최상위, 방어 중간.
     */
    ROSENRITTER(
        displayNameKo = "장미기사단",
        attack = 280,
        defense = 200,
        speed = 9,
        allowedOnGas = true,
        allowedOnFortress = true,
        producible = false,
        factionRestricted = "alliance",
    );

    companion object {
        /**
         * Returns ground unit types allowed for a given planet type.
         *
         * 기본값은 매뉴얼 p50 정책대로 생산 가능(=엔진 내부에서 자동 선택 대상) 단위만 반환한다.
         * 특수 단위(근위사단/척탄병교도대/장미기사단)는 시나리오/이벤트 경로에서 직접 호명될 때에만
         * 포함되어야 하므로 `includeNonProducible=true` 를 명시해야 노출된다.
         */
        fun allowedFor(
            isGasPlanet: Boolean,
            isFortress: Boolean,
            includeNonProducible: Boolean = false,
        ): List<GroundUnitType> {
            return entries.filter { type ->
                if (!includeNonProducible && !type.producible) return@filter false
                when {
                    isFortress -> type.allowedOnFortress
                    isGasPlanet -> type.allowedOnGas
                    else -> true // normal planet allows all
                }
            }
        }
    }
}
