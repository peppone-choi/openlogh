package com.openlogh.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Phase 24-22 (gap A11, gin7 매뉴얼 p36):
 * 계급 변경 시 기함 자동 교체 로직의 순수 함수 커버리지.
 *
 * 정책:
 *   1. 각 tier 에는 기본 기함이 지정되어 있다.
 *   2. 장교가 기함이 없는 상태("None")라면 새 tier 의 기본 기함을 배정한다.
 *   3. 장교가 본 오브젝트가 관리하는 tier 기본 기함 중 하나를 들고 있으면 새 tier
 *      기본 기함으로 교체한다.
 *   4. 장교가 특수/구매 기함(관리 집합 밖의 코드)을 들고 있으면 그대로 보존한다.
 */
class FlagshipProgressionTest {

    @Test
    fun `tier 0 maps to cadet frigate`() {
        assertEquals("CADET_FRIGATE", FlagshipProgression.codeForTier(0))
    }

    @Test
    fun `tier 10 maps to sovereign battleship`() {
        assertEquals("SOVEREIGN_BATTLESHIP", FlagshipProgression.codeForTier(10))
    }

    @Test
    fun `out-of-range tier is clamped into 0 to 10`() {
        assertEquals("CADET_FRIGATE", FlagshipProgression.codeForTier(-5))
        assertEquals("SOVEREIGN_BATTLESHIP", FlagshipProgression.codeForTier(99))
    }

    @Test
    fun `officer without flagship receives the tier default on promote`() {
        val result = FlagshipProgression.resolveOnRankChange(
            currentCode = FlagshipProgression.NONE,
            newTier = 4,
        )
        assertEquals("LIGHT_CRUISER", result)
    }

    @Test
    fun `officer with managed tier flagship is swapped on promote`() {
        // 대령(tier 4) → 준장(tier 5) 승진: LIGHT_CRUISER → HEAVY_CRUISER
        val result = FlagshipProgression.resolveOnRankChange(
            currentCode = "LIGHT_CRUISER",
            newTier = 5,
        )
        assertEquals("HEAVY_CRUISER", result)
    }

    @Test
    fun `officer with managed tier flagship is swapped down on demote`() {
        // 준장(tier 5) → 대령(tier 4) 강등: HEAVY_CRUISER → LIGHT_CRUISER
        val result = FlagshipProgression.resolveOnRankChange(
            currentCode = "HEAVY_CRUISER",
            newTier = 4,
        )
        assertEquals("LIGHT_CRUISER", result)
    }

    @Test
    fun `special purchased flagship is preserved across promote`() {
        // 특수 보상 기함(관리 집합 밖) 은 승진해도 유지된다.
        val result = FlagshipProgression.resolveOnRankChange(
            currentCode = "BRUNHILD",  // 특수 기함 placeholder
            newTier = 10,
        )
        assertEquals("BRUNHILD", result)
    }

    @Test
    fun `special purchased flagship is preserved across demote`() {
        val result = FlagshipProgression.resolveOnRankChange(
            currentCode = "HYPERION",  // 또 다른 특수 기함 placeholder
            newTier = 3,
        )
        assertEquals("HYPERION", result)
    }

    @Test
    fun `consecutive promotes walk the tier flagship ladder`() {
        var code = FlagshipProgression.NONE
        var tier = 0
        val trace = mutableListOf<String>()
        while (tier <= 10) {
            code = FlagshipProgression.resolveOnRankChange(code, tier)
            trace.add("t$tier=$code")
            tier++
        }
        // Every transition must yield a managed code (no NONE leakage).
        assertTrue(trace.none { it.endsWith("None") }, "promote ladder never leaves flagship at None: $trace")
        // Final tier must be sovereign battleship.
        assertTrue(code == "SOVEREIGN_BATTLESHIP")
    }

    @Test
    fun `managed codes set contains exactly the distinct flagship codes`() {
        val managed = FlagshipProgression.managedCodes
        assertTrue(managed.contains("CADET_FRIGATE"))
        assertTrue(managed.contains("PATROL_CORVETTE"))
        assertTrue(managed.contains("LIGHT_CRUISER"))
        assertTrue(managed.contains("HEAVY_CRUISER"))
        assertTrue(managed.contains("BATTLE_CRUISER"))
        assertTrue(managed.contains("FLAG_BATTLESHIP"))
        assertTrue(managed.contains("COMMAND_BATTLESHIP"))
        assertTrue(managed.contains("SOVEREIGN_BATTLESHIP"))
        assertEquals(8, managed.size, "8 distinct tier flagships expected")
    }

    @Test
    fun `resolveOnRankChange is a pure function and does not mutate inputs`() {
        val input = "BRUNHILD"
        val out = FlagshipProgression.resolveOnRankChange(input, 9)
        assertEquals("BRUNHILD", input, "input string must not be mutated")
        assertNotEquals("", out)
    }
}
