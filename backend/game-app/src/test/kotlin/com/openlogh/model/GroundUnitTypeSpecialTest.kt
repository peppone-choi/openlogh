package com.openlogh.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Phase 24-24 (gap A8, gin7 매뉴얼 p100 "생산 불가 단위"):
 *
 * 매뉴얼 p100 은 3 개의 특수 지상 병종을 **생산 불가**로 별도 표기한다.
 *   · 근위사단 (제국 황제 친위대)
 *   · 척탄병교도대 (제국 엘리트 훈련 지휘대)
 *   · 장미기사단 (자유행성동맹 특수 습격대, 원작 Rosenritter)
 *
 * 이들은 커맨드로 징집할 수 없고 시나리오 프리셋/특수 이벤트 경로로만 배치된다.
 * v2.4 까지 enum 에 존재조차 하지 않아 seed 나 이벤트가 이 이름을 참조할 때
 * 역매핑이 불가능했다. 본 테스트는 enum 존재 + 제약 플래그 + allowedFor
 * 기본/옵트인 동작을 모두 고정한다.
 */
class GroundUnitTypeSpecialTest {

    @Test
    fun `imperial guard is a non-producible empire-only heavy unit`() {
        val u = GroundUnitType.IMPERIAL_GUARD
        assertEquals("근위사단", u.displayNameKo)
        assertFalse(u.producible, "근위사단 is scenario-only (not producible)")
        assertEquals("empire", u.factionRestricted)
        assertTrue(u.attack >= 250, "제국 친위대는 최상위 공격력")
        assertTrue(u.defense >= 260, "근위사단 defense must be elite")
        assertFalse(u.allowedOnGas, "근위사단 cannot deploy on gas giants")
        assertTrue(u.allowedOnFortress, "근위사단 defends fortresses")
    }

    @Test
    fun `grenadier guard is a non-producible empire training unit`() {
        val u = GroundUnitType.GRENADIER_GUARD
        assertEquals("척탄병교도대", u.displayNameKo)
        assertFalse(u.producible)
        assertEquals("empire", u.factionRestricted)
        assertTrue(u.allowedOnGas)
        assertTrue(u.allowedOnFortress)
    }

    @Test
    fun `rosenritter is a non-producible alliance raiding unit`() {
        val u = GroundUnitType.ROSENRITTER
        assertEquals("장미기사단", u.displayNameKo)
        assertFalse(u.producible)
        assertEquals("alliance", u.factionRestricted)
        assertTrue(u.attack >= 250, "로젠리터는 최상위 공격력")
        assertTrue(u.speed >= 8, "로젠리터는 기동 중시")
    }

    @Test
    fun `allowedFor default excludes non-producible special units`() {
        // normal planet: only the three producible types are returned by default.
        val defaults = GroundUnitType.allowedFor(isGasPlanet = false, isFortress = false)
        assertTrue(GroundUnitType.ARMORED_INFANTRY in defaults)
        assertTrue(GroundUnitType.GRENADIER in defaults)
        assertTrue(GroundUnitType.LIGHT_INFANTRY in defaults)
        assertFalse(GroundUnitType.IMPERIAL_GUARD in defaults,
            "default allowedFor must NOT leak IMPERIAL_GUARD")
        assertFalse(GroundUnitType.GRENADIER_GUARD in defaults)
        assertFalse(GroundUnitType.ROSENRITTER in defaults)
        assertEquals(3, defaults.size)
    }

    @Test
    fun `allowedFor with includeNonProducible true returns special units`() {
        val all = GroundUnitType.allowedFor(
            isGasPlanet = false,
            isFortress = false,
            includeNonProducible = true,
        )
        assertTrue(GroundUnitType.IMPERIAL_GUARD in all,
            "scenario path must be able to opt into non-producible units")
        assertTrue(GroundUnitType.GRENADIER_GUARD in all)
        assertTrue(GroundUnitType.ROSENRITTER in all)
    }

    @Test
    fun `imperial guard is filtered out on gas planets even with opt-in`() {
        val all = GroundUnitType.allowedFor(
            isGasPlanet = true,
            isFortress = false,
            includeNonProducible = true,
        )
        assertFalse(GroundUnitType.IMPERIAL_GUARD in all,
            "근위사단 is still gas-banned even when non-producible opt-in is true")
        // Rosenritter and grenadier guard should remain (they allow gas)
        assertTrue(GroundUnitType.ROSENRITTER in all)
        assertTrue(GroundUnitType.GRENADIER_GUARD in all)
    }

    @Test
    fun `enum now has exactly 6 entries`() {
        assertEquals(6, GroundUnitType.entries.size,
            "Phase 24-24: 3 producible + 3 special = 6 ground unit types")
    }

    @Test
    fun `producible set matches manual p100 exactly`() {
        val producible = GroundUnitType.entries.filter { it.producible }.toSet()
        assertEquals(
            setOf(
                GroundUnitType.ARMORED_INFANTRY,
                GroundUnitType.GRENADIER,
                GroundUnitType.LIGHT_INFANTRY,
            ),
            producible,
            "Only the three common types may be produced by commands per manual p100",
        )
    }
}
