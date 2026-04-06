package com.openlogh.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.math.round

/**
 * Rounding Parity Test (TYPE-02)
 *
 * PHP-to-Kotlin rounding comparison table:
 *
 * Value    PHP (int)   PHP round()   Kotlin .toInt()   Kotlin round().toInt()   Kotlin Math.round().toInt()
 * ------   ---------   -----------   ----------------  ----------------------   --------------------------
 *  2.3        2           2              2                   2                        2
 *  2.5        2           3              2                   2 (BANKER'S!)            3 (Long->Int)
 *  2.7        2           3              2                   3                        3
 *  3.5        3           4              3                   4 (BANKER'S!)            4
 * -2.3       -2          -2             -2                  -2                       -2
 * -2.5       -2          -3             -2                  -2 (BANKER'S!)           -2 (DIFFERS from PHP!)
 * -2.7       -2          -3             -2                  -3                       -3
 *  0.5        0           1              0                   0 (BANKER'S!)            1
 *
 * Engine normalization decision (Plan 02):
 *   All Math.round(x).toInt() replaced with kotlin.math.round(x).toInt().
 *   kotlin.math.round uses banker's rounding (half-to-even).
 *   This diverges from PHP round() at exact .5 boundaries only.
 *   In practice, exact .5 values are rare in game formulas (random * multiplier).
 *   Deterministic .5 sites (value/2, value*0.5) are documented inline.
 */
@DisplayName("Rounding Parity (PHP vs Kotlin)")
class RoundingParityTest {

    @Nested
    @DisplayName("PHP (int) cast vs Kotlin .toInt() -- exact match")
    inner class PhpIntCast {

        @Test
        fun `positive fractional values truncate toward zero`() {
            assertEquals(2, 2.3.toInt())
            assertEquals(2, 2.5.toInt())
            assertEquals(2, 2.7.toInt())
        }

        @Test
        fun `negative fractional values truncate toward zero`() {
            // PHP: (int)(-2.3) == -2, (int)(-2.5) == -2, (int)(-2.7) == -2
            assertEquals(-2, (-2.3).toInt())
            assertEquals(-2, (-2.5).toInt())
            assertEquals(-2, (-2.7).toInt())
        }

        @Test
        fun `zero and whole numbers are unchanged`() {
            assertEquals(0, 0.0.toInt())
            assertEquals(1, 1.0.toInt())
            assertEquals(-1, (-1.0).toInt())
        }

        @Test
        fun `small positive values below 1 truncate to 0`() {
            assertEquals(0, 0.1.toInt())
            assertEquals(0, 0.5.toInt())
            assertEquals(0, 0.9.toInt())
        }

        @Test
        fun `small negative values above -1 truncate to 0`() {
            assertEquals(0, (-0.1).toInt())
            assertEquals(0, (-0.5).toInt())
            assertEquals(0, (-0.9).toInt())
        }
    }

    @Nested
    @DisplayName("PHP round() vs kotlin.math.round -- golden values")
    inner class PhpRound {

        /**
         * PHP round() uses half-away-from-zero.
         * kotlin.math.round() uses banker's rounding (half-to-even).
         *
         * These tests verify kotlin.math.round behavior and document
         * divergence from PHP at exact .5 boundaries.
         * Non-.5 values match PHP round() exactly.
         */

        @Test
        fun `non-half values match PHP round exactly`() {
            // PHP round(2.3) = 2, kotlin.math.round(2.3) = 2.0
            assertEquals(2, round(2.3).toInt())
            // PHP round(2.7) = 3, kotlin.math.round(2.7) = 3.0
            assertEquals(3, round(2.7).toInt())
            // PHP round(-2.3) = -2, kotlin.math.round(-2.3) = -2.0
            assertEquals(-2, round(-2.3).toInt())
            // PHP round(-2.7) = -3, kotlin.math.round(-2.7) = -3.0
            assertEquals(-3, round(-2.7).toInt())
        }

        @Test
        fun `positive half values use banker rounding - diverges from PHP`() {
            // PHP round(2.5) = 3 (half-away-from-zero)
            // kotlin.math.round(2.5) = 2.0 (half-to-even: nearest even is 2)
            assertEquals(2, round(2.5).toInt(), "kotlin round(2.5) = 2, PHP round(2.5) = 3")
            // PHP round(1.5) = 2 (half-away-from-zero)
            // kotlin.math.round(1.5) = 2.0 (half-to-even: nearest even is 2)
            assertEquals(2, round(1.5).toInt(), "kotlin round(1.5) = 2, PHP round(1.5) = 2 -- agrees")
            // PHP round(3.5) = 4 (half-away-from-zero)
            // kotlin.math.round(3.5) = 4.0 (half-to-even: nearest even is 4)
            assertEquals(4, round(3.5).toInt(), "kotlin round(3.5) = 4, PHP round(3.5) = 4 -- agrees")
        }

        @Test
        fun `negative half values use banker rounding - diverges from PHP`() {
            // PHP round(-2.5) = -3 (half-away-from-zero)
            // kotlin.math.round(-2.5) = -2.0 (half-to-even: nearest even is -2)
            assertEquals(-2, round(-2.5).toInt(), "kotlin round(-2.5) = -2, PHP round(-2.5) = -3")
            // PHP round(-1.5) = -2 (half-away-from-zero)
            // kotlin.math.round(-1.5) = -2.0 (half-to-even: nearest even is -2)
            assertEquals(-2, round(-1.5).toInt(), "kotlin round(-1.5) = -2, PHP round(-1.5) = -2 -- agrees")
            // PHP round(-3.5) = -4 (half-away-from-zero)
            // kotlin.math.round(-3.5) = -4.0 (half-to-even: nearest even is -4)
            assertEquals(-4, round(-3.5).toInt(), "kotlin round(-3.5) = -4, PHP round(-3.5) = -4 -- agrees")
        }

        @Test
        fun `zero-point-five boundary`() {
            // PHP round(0.5) = 1 (half-away-from-zero)
            // kotlin.math.round(0.5) = 0.0 (half-to-even: nearest even is 0)
            assertEquals(0, round(0.5).toInt(), "kotlin round(0.5) = 0, PHP round(0.5) = 1")
        }

        @Test
        fun `game-typical values without exact half never diverge`() {
            // Economy formula: population * commerce / commerceMax * trustRatio / 30
            // With population=10000, commerce=500, commerceMax=1000, approval=100 -> trustRatio=1.0
            val income = 10000.0 * 500 / 1000 * 1.0 / 30.0  // = 166.666...
            assertEquals(167, round(income).toInt(), "game income rounding")

            // NPC stat derivation: intel*0.4 + leadership*0.3 + noise
            val stat = 80 * 0.4 + 70 * 0.3 + 5.0  // = 58.0 (exact integer, no rounding issue)
            assertEquals(58, round(stat).toInt(), "NPC stat rounding")

            // Bill calculation: totalBill * billRate / 100.0
            val bill = 2000.0 * 100 / 100.0  // = 2000.0 (exact)
            assertEquals(2000, round(bill).toInt(), "bill rounding")
        }
    }

    @Nested
    @DisplayName("Math.round vs kotlin.math.round divergence")
    inner class RoundingMethodDivergence {

        @Test
        fun `Math_round returns Long - narrowing risk for large values`() {
            // Math.round(Double) returns Long, .toInt() narrows silently
            // kotlin.math.round(Double) returns Double, .toInt() is safe for game ranges
            val largeValue = 50000.7
            assertEquals(50001, Math.round(largeValue).toInt(), "Math.round large value")
            assertEquals(50001, round(largeValue).toInt(), "kotlin round large value")
            // Both produce same result for game-range values (< 100,000)
            assertEquals(Math.round(largeValue).toInt(), round(largeValue).toInt(), "same for game range")
        }

        @Test
        fun `Math_round and kotlin_round diverge at positive 2_5`() {
            // Math.round(2.5) = 3 (Java half-up toward positive infinity)
            // kotlin.math.round(2.5) = 2 (banker's rounding, half-to-even)
            assertEquals(3, Math.round(2.5).toInt(), "Math.round(2.5)")
            assertEquals(2, round(2.5).toInt(), "kotlin round(2.5)")
        }

        @Test
        fun `Math_round and kotlin_round agree at negative 2_5`() {
            // Math.round(-2.5) = -2 (Java half-up toward positive infinity)
            // kotlin.math.round(-2.5) = -2 (banker's rounding, half-to-even)
            // PHP round(-2.5) = -3 (half-away-from-zero) -- NEITHER matches PHP here
            assertEquals(-2, Math.round(-2.5).toInt(), "Math.round(-2.5)")
            assertEquals(-2, round(-2.5).toInt(), "kotlin round(-2.5)")
        }

        @Test
        fun `non-half values produce identical results across all methods`() {
            val testValues = listOf(2.3, 2.7, -2.3, -2.7, 100.1, 100.9, -0.3, -0.7)
            for (v in testValues) {
                assertEquals(
                    Math.round(v).toInt(),
                    round(v).toInt(),
                    "Math.round vs kotlin round for $v"
                )
            }
        }
    }
}
