package com.openlogh.engine.modifier

/**
 * gin7 진영 타입별 수정자.
 *
 * 삼국지(che_*) 기반 국가타입 수정자는 제거되었습니다.
 * TODO Phase 4: gin7 faction_type(empire/alliance/fezzan/rebel)에 맞는
 *               경제/전투/내정 수정자를 Gin7EconomyService에서 구현할 것.
 */
object NationTypeModifiers {

    private val factionTypes = mapOf<String, ActionModifier>(
        "empire" to object : ActionModifier {
            override val code = "empire"; override val name = "은하제국"
            // TODO Phase 4: 제국 진영 수정자 구현
        },
        "alliance" to object : ActionModifier {
            override val code = "alliance"; override val name = "자유행성동맹"
            // TODO Phase 4: 동맹 진영 수정자 구현
        },
        "fezzan" to object : ActionModifier {
            override val code = "fezzan"; override val name = "페잔 자치령"
            // TODO Phase 4: 페잔 진영 수정자 구현 (교역 보너스 등)
        },
        "rebel" to object : ActionModifier {
            override val code = "rebel"; override val name = "반란군"
            // TODO Phase 4: 반란군 진영 수정자 구현
        },
    )

    fun get(code: String): ActionModifier? = factionTypes[code]
    fun getAll(): Map<String, ActionModifier> = factionTypes
}
