package com.openlogh.engine.tactical

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Phase 24-26 (gap D4): groundUnitType 문자열과 GroundUnitType enum 사이의
 * 드리프트를 고정한다.
 *
 * 히스토리:
 *   · `GroundUnitType` enum: GRENADIER, LIGHT_INFANTRY, ARMORED_INFANTRY (+ 24-24 특수 3종)
 *   · 기존 `GroundBattleEngine.calculateGroundDamage` 의 when 문자열:
 *       "ARMORED_GRENADIER", "LIGHT_MARINE" ← enum 과 불일치 (legacy drift)
 *
 * 결과: v2.4 까지는 유닛이 enum 이름("GRENADIER" 등) 으로 실제 저장되면
 * 데미지 배수가 1.0 (else) 로 fall-through 되어 매뉴얼 스펙과 달랐다.
 *
 * 이 테스트는 신규 이름과 레거시 alias 양쪽 모두 매뉴얼 배수로 해석되는지를
 * 보증하고, Phase 24-24 에서 추가한 3 특수 병종이 엘리트 배수를 받는지도
 * 확인한다.
 */
class GroundBattleEngineD4Test {

    @Test
    fun `new canonical names map to expected modifiers`() {
        assertEquals(1.0, GroundBattleEngine.typeModifierFor("ARMORED_INFANTRY"))
        assertEquals(1.3, GroundBattleEngine.typeModifierFor("GRENADIER"))
        assertEquals(0.8, GroundBattleEngine.typeModifierFor("LIGHT_INFANTRY"))
    }

    @Test
    fun `legacy drift aliases map to the same modifiers`() {
        // 드리프트 이름도 그대로 해석되어야 한다 — 이미 DB/seed 에 저장된 row 보호.
        assertEquals(1.3, GroundBattleEngine.typeModifierFor("ARMORED_GRENADIER"),
            "legacy ARMORED_GRENADIER must alias to GRENADIER's 1.3× modifier")
        assertEquals(0.8, GroundBattleEngine.typeModifierFor("LIGHT_MARINE"),
            "legacy LIGHT_MARINE must alias to LIGHT_INFANTRY's 0.8× modifier")
    }

    @Test
    fun `special elite units (24-24) receive elite modifiers`() {
        assertEquals(1.5, GroundBattleEngine.typeModifierFor("IMPERIAL_GUARD"))
        assertEquals(1.5, GroundBattleEngine.typeModifierFor("GRENADIER_GUARD"))
        assertEquals(1.6, GroundBattleEngine.typeModifierFor("ROSENRITTER"))
    }

    @Test
    fun `unknown types fall through to 1_0 default`() {
        assertEquals(1.0, GroundBattleEngine.typeModifierFor("UNKNOWN_BLASTER_SQUAD"))
    }

    @Test
    fun `case-insensitive lookup works for mixed case strings`() {
        assertEquals(1.3, GroundBattleEngine.typeModifierFor("grenadier"))
        assertEquals(1.6, GroundBattleEngine.typeModifierFor("rosenritter"))
    }

    @Test
    fun `imperial guard is banned on gas planets but allowed on fortresses`() {
        val guardUnit = GroundUnit(
            unitId = 1L,
            factionId = 1L,
            groundUnitType = "IMPERIAL_GUARD",
            count = 100,
            maxCount = 100,
        )
        assertFalse(GroundBattleEngine.isUnitAllowedOnPlanetType(guardUnit, "gas"),
            "근위사단 is heavy-class and must NOT be deployable on gas giants")
        assertTrue(GroundBattleEngine.isUnitAllowedOnPlanetType(guardUnit, "fortress"),
            "근위사단 defends fortresses per manual p50 + gin7 Kaiser guard doctrine")
        assertTrue(GroundBattleEngine.isUnitAllowedOnPlanetType(guardUnit, "normal"))
    }

    @Test
    fun `rosenritter is allowed on every terrain type`() {
        val rr = GroundUnit(
            unitId = 2L,
            factionId = 2L,
            groundUnitType = "ROSENRITTER",
            count = 100,
            maxCount = 100,
        )
        assertTrue(GroundBattleEngine.isUnitAllowedOnPlanetType(rr, "normal"))
        assertTrue(GroundBattleEngine.isUnitAllowedOnPlanetType(rr, "gas"))
        assertTrue(GroundBattleEngine.isUnitAllowedOnPlanetType(rr, "fortress"))
    }

    @Test
    fun `armored infantry still blocked on gas and fortress per 24-12`() {
        val armor = GroundUnit(
            unitId = 3L,
            factionId = 1L,
            groundUnitType = "ARMORED_INFANTRY",
            count = 100,
            maxCount = 100,
        )
        assertTrue(GroundBattleEngine.isUnitAllowedOnPlanetType(armor, "normal"))
        assertFalse(GroundBattleEngine.isUnitAllowedOnPlanetType(armor, "gas"))
        assertFalse(GroundBattleEngine.isUnitAllowedOnPlanetType(armor, "fortress"))
    }
}
