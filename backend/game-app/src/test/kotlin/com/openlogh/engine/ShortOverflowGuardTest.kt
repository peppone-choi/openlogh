package com.openlogh.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Short Overflow Guard Test (TYPE-01)
 *
 * Verifies that the coerceIn guard pattern prevents silent Short wrap-around
 * when arithmetic produces values outside a field's domain bounds.
 *
 * Guard pattern (from ItemService.kt):
 *   general.injury = (general.injury - item.value).coerceIn(0, 80).toShort()
 *
 * Each test category simulates the arithmetic pattern
 *   (existingValue + delta).coerceIn(min, max).toShort()
 * and verifies that:
 *   - Values above the upper bound are clamped to the upper bound
 *   - Values below the lower bound are clamped to the lower bound
 *   - Values within bounds pass through unchanged
 *   - Boundary values (exactly at min/max) pass through unchanged
 */
@DisplayName("Short Overflow Guard")
class ShortOverflowGuardTest {

    /**
     * Helper: simulates the coerceIn guard pattern for a Short field assignment.
     * Returns the guarded Short value.
     */
    private fun guardedShort(computed: Int, min: Int, max: Int): Short =
        computed.coerceIn(min, max).toShort()

    @Nested
    @DisplayName("Stats (leadership, strength, intel, politics, charm): 0..100")
    inner class StatFields {
        private val min = 0
        private val max = 100

        @Test
        fun `overflow above max is clamped to 100`() {
            assertEquals(100.toShort(), guardedShort(150, min, max))
            assertEquals(100.toShort(), guardedShort(40000, min, max))
        }

        @Test
        fun `underflow below min is clamped to 0`() {
            assertEquals(0.toShort(), guardedShort(-5, min, max))
            assertEquals(0.toShort(), guardedShort(-5000, min, max))
        }

        @Test
        fun `boundary values pass through unchanged`() {
            assertEquals(0.toShort(), guardedShort(0, min, max))
            assertEquals(100.toShort(), guardedShort(100, min, max))
        }

        @Test
        fun `normal-range values pass through unchanged`() {
            assertEquals(50.toShort(), guardedShort(50, min, max))
            assertEquals(75.toShort(), guardedShort(75, min, max))
            assertEquals(1.toShort(), guardedShort(1, min, max))
            assertEquals(99.toShort(), guardedShort(99, min, max))
        }

        @Test
        fun `one above max is clamped`() {
            assertEquals(100.toShort(), guardedShort(101, min, max))
        }

        @Test
        fun `one below min is clamped`() {
            assertEquals(0.toShort(), guardedShort(-1, min, max))
        }
    }

    @Nested
    @DisplayName("Stat Exp (leadershipExp, strengthExp, intelExp, politicsExp, charmExp): 0..1000")
    inner class StatExpFields {
        private val min = 0
        private val max = 1000

        @Test
        fun `overflow above max is clamped to 1000`() {
            assertEquals(1000.toShort(), guardedShort(1500, min, max))
            assertEquals(1000.toShort(), guardedShort(40000, min, max))
        }

        @Test
        fun `underflow below min is clamped to 0`() {
            assertEquals(0.toShort(), guardedShort(-10, min, max))
            assertEquals(0.toShort(), guardedShort(-5000, min, max))
        }

        @Test
        fun `boundary values pass through unchanged`() {
            assertEquals(0.toShort(), guardedShort(0, min, max))
            assertEquals(1000.toShort(), guardedShort(1000, min, max))
        }

        @Test
        fun `normal-range values pass through unchanged`() {
            assertEquals(500.toShort(), guardedShort(500, min, max))
            assertEquals(999.toShort(), guardedShort(999, min, max))
        }
    }

    @Nested
    @DisplayName("Military (training: 0..110, morale: 0..150, injury: 0..80, defenceTrain: 0..100)")
    inner class MilitaryFields {

        @Test
        fun `train overflow is clamped to 110`() {
            assertEquals(110.toShort(), guardedShort(150, 0, 110))
            assertEquals(110.toShort(), guardedShort(40000, 0, 110))
        }

        @Test
        fun `train underflow is clamped to 0`() {
            assertEquals(0.toShort(), guardedShort(-5, 0, 110))
        }

        @Test
        fun `train boundary values pass through`() {
            assertEquals(0.toShort(), guardedShort(0, 0, 110))
            assertEquals(110.toShort(), guardedShort(110, 0, 110))
        }

        @Test
        fun `morale overflow is clamped to 150`() {
            assertEquals(150.toShort(), guardedShort(200, 0, 150))
            assertEquals(150.toShort(), guardedShort(40000, 0, 150))
        }

        @Test
        fun `morale underflow is clamped to 0`() {
            assertEquals(0.toShort(), guardedShort(-10, 0, 150))
        }

        @Test
        fun `morale boundary values pass through`() {
            assertEquals(0.toShort(), guardedShort(0, 0, 150))
            assertEquals(150.toShort(), guardedShort(150, 0, 150))
        }

        @Test
        fun `injury overflow is clamped to 80`() {
            assertEquals(80.toShort(), guardedShort(100, 0, 80))
            assertEquals(80.toShort(), guardedShort(40000, 0, 80))
        }

        @Test
        fun `injury underflow is clamped to 0`() {
            assertEquals(0.toShort(), guardedShort(-5, 0, 80))
        }

        @Test
        fun `injury boundary values pass through`() {
            assertEquals(0.toShort(), guardedShort(0, 0, 80))
            assertEquals(80.toShort(), guardedShort(80, 0, 80))
        }

        @Test
        fun `defenceTrain overflow is clamped to 100`() {
            assertEquals(100.toShort(), guardedShort(120, 0, 100))
        }

        @Test
        fun `defenceTrain normal value passes through`() {
            assertEquals(80.toShort(), guardedShort(80, 0, 100))
        }
    }

    @Nested
    @DisplayName("Nation Economy (bill: 0..200, conscriptionRate: 0..100, rateTmp: 0..100)")
    inner class NationEconomyFields {

        @Test
        fun `bill overflow is clamped to 200`() {
            assertEquals(200.toShort(), guardedShort(250, 0, 200))
            assertEquals(200.toShort(), guardedShort(40000, 0, 200))
        }

        @Test
        fun `bill underflow is clamped to 0`() {
            assertEquals(0.toShort(), guardedShort(-10, 0, 200))
        }

        @Test
        fun `bill boundary values pass through`() {
            assertEquals(0.toShort(), guardedShort(0, 0, 200))
            assertEquals(200.toShort(), guardedShort(200, 0, 200))
        }

        @Test
        fun `rate overflow is clamped to 100`() {
            assertEquals(100.toShort(), guardedShort(120, 0, 100))
        }

        @Test
        fun `rate underflow is clamped to 0`() {
            assertEquals(0.toShort(), guardedShort(-5, 0, 100))
        }

        @Test
        fun `rateTmp boundary values pass through`() {
            assertEquals(0.toShort(), guardedShort(0, 0, 100))
            assertEquals(100.toShort(), guardedShort(100, 0, 100))
        }
    }

    @Nested
    @DisplayName("Extreme overflow values that would wrap without guard")
    inner class ExtremeOverflow {

        @Test
        fun `40000 would wrap to -25536 without guard but clamps to upper bound`() {
            // (40000).toShort() == -25536 (silent wrap)
            // (40000).coerceIn(0, 100).toShort() == 100 (guarded)
            assertEquals((-25536).toShort(), (40000).toShort(), "Unguarded wraps silently")
            assertEquals(100.toShort(), guardedShort(40000, 0, 100), "Guard prevents wrap")
        }

        @Test
        fun `negative 5000 would wrap without guard but clamps to lower bound`() {
            assertEquals(0.toShort(), guardedShort(-5000, 0, 100))
            assertEquals(0.toShort(), guardedShort(-5000, 0, 150))
            assertEquals(0.toShort(), guardedShort(-5000, 0, 200))
        }

        @Test
        fun `Short MAX_VALUE plus 1 wraps but guard prevents it`() {
            // (32768).toShort() == -32768 (silent wrap)
            assertEquals((-32768).toShort(), (32768).toShort(), "Unguarded wraps silently")
            assertEquals(100.toShort(), guardedShort(32768, 0, 100), "Guard prevents wrap")
        }

        @Test
        fun `large intermediate computation stays safe with guard`() {
            // Simulates ships * training = 10000 * 110 = 1_100_000 then assigned to Short field
            val computed = 10000 * 110  // 1_100_000
            assertEquals(110.toShort(), guardedShort(computed, 0, 110))
        }
    }

    @Nested
    @DisplayName("Fields with non-zero lower bounds")
    inner class NonZeroLowerBound {

        @Test
        fun `npcState bounds -1 to 9`() {
            assertEquals((-1).toShort(), guardedShort(-5, -1, 9))
            assertEquals(9.toShort(), guardedShort(15, -1, 9))
            assertEquals(5.toShort(), guardedShort(5, -1, 9))
        }

        @Test
        fun `belong bounds 0 to 12`() {
            assertEquals(0.toShort(), guardedShort(-1, 0, 12))
            assertEquals(12.toShort(), guardedShort(20, 0, 12))
            assertEquals(6.toShort(), guardedShort(6, 0, 12))
        }

        @Test
        fun `crewType bounds 0 to 50`() {
            assertEquals(0.toShort(), guardedShort(-1, 0, 50))
            assertEquals(50.toShort(), guardedShort(100, 0, 50))
            assertEquals(25.toShort(), guardedShort(25, 0, 50))
        }
    }
}
